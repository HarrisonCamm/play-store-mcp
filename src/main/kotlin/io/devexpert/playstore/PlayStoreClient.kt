package io.devexpert.playstore

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.AppDetails
import com.google.api.services.androidpublisher.model.LocalizedText
import com.google.api.services.androidpublisher.model.Listing
import com.google.api.services.androidpublisher.model.SafetyLabelsUpdateRequest
import com.google.api.services.androidpublisher.model.Subscription
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Client for interacting with Google Play Console API
 */
class PlayStoreClient(
    private val serviceAccountKeyPath: String,
    private val applicationName: String = "Play Store MCP Server"
) {
    private val logger = LoggerFactory.getLogger(PlayStoreClient::class.java)

    private val publisher: AndroidPublisher by lazy {
        initializePublisher()
    }

    private fun initializePublisher(): AndroidPublisher {
        logger.info("Initializing Google Play Console API client...")

        try {
            // Initialize HTTP transport and JSON factory
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            // Load service account credentials
            val credentialsFile = File(serviceAccountKeyPath)
            if (!credentialsFile.exists()) {
                throw IllegalArgumentException("Service account key file not found: $serviceAccountKeyPath")
            }

            val credential = ServiceAccountCredentials.fromStream(
                FileInputStream(credentialsFile)
            ).createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
            
            val httpCredentialsAdapter = HttpCredentialsAdapter(credential)

            val requestInitializer = HttpRequestInitializer { request ->
                httpCredentialsAdapter.initialize(request)
                request.connectTimeout = 60000 // 60 seconds
                request.readTimeout = 600000 // 10 minutes for large file uploads
            }
            
            // Build the API client with extended timeouts
            val publisher = AndroidPublisher.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName(applicationName)
                .build()

            logger.info("Google Play Console API client initialized successfully")
            return publisher

        } catch (e: Exception) {
            logger.error("Failed to initialize Google Play Console API client", e)
            throw PlayStoreException("Failed to initialize API client: ${e.message}", e)
        }
    }

    /**
     * Update store listing details for a specific language.
     */
    fun updateStoreListing(
        packageName: String,
        language: String,
        title: String?,
        shortDescription: String?,
        fullDescription: String?,
        video: String?
    ) {
        logger.info("Updating store listing for $packageName ($language)")

        val edit = publisher.edits().insert(packageName, null).execute()
        val editId = edit.id

        try {
            val listing = Listing().setLanguage(language)
            title?.let { listing.setTitle(it) }
            shortDescription?.let { listing.setShortDescription(it) }
            fullDescription?.let { listing.setFullDescription(it) }
            video?.let { listing.setVideo(it) }

            publisher.edits().listings().patch(packageName, editId, language, listing).execute()
            commitEditWithDraftFallback(packageName, editId)

            logger.info("Store listing updated for $packageName ($language)")
        } catch (e: Exception) {
            logger.error("Failed to update store listing for $packageName ($language)", e)
            runCatching { publisher.edits().delete(packageName, editId).execute() }
            throw PlayStoreException("Failed to update store listing: ${e.message}", e)
        }
    }

    /**
     * Update app details (contact info, default language).
     */
    fun updateAppDetails(
        packageName: String,
        defaultLanguage: String?,
        contactEmail: String?,
        contactPhone: String?,
        contactWebsite: String?
    ) {
        logger.info("Updating app details for $packageName")

        val edit = publisher.edits().insert(packageName, null).execute()
        val editId = edit.id

        try {
            val details = AppDetails()
            defaultLanguage?.let { details.setDefaultLanguage(it) }
            contactEmail?.let { details.setContactEmail(it) }
            contactPhone?.let { details.setContactPhone(it) }
            contactWebsite?.let { details.setContactWebsite(it) }

            publisher.edits().details().patch(packageName, editId, details).execute()
            commitEditWithDraftFallback(packageName, editId)

            logger.info("App details updated for $packageName")
        } catch (e: Exception) {
            logger.error("Failed to update app details for $packageName", e)
            runCatching { publisher.edits().delete(packageName, editId).execute() }
            throw PlayStoreException("Failed to update app details: ${e.message}", e)
        }
    }

    /**
     * Upload a store listing image (including screenshots).
     */
    fun uploadListingImage(
        packageName: String,
        language: String,
        imageType: String,
        imagePath: String,
        clearExisting: Boolean
    ) {
        logger.info("Uploading $imageType for $packageName ($language)")

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            throw PlayStoreException("Image file not found: $imagePath")
        }

        val edit = publisher.edits().insert(packageName, null).execute()
        val editId = edit.id

        try {
            if (clearExisting) {
                publisher.edits().images().deleteall(packageName, editId, language, imageType).execute()
            }

            val contentType = detectImageMimeType(imageFile)
            val content = FileContent(contentType, imageFile)
            publisher.edits().images().upload(packageName, editId, language, imageType, content).execute()
            commitEditWithDraftFallback(packageName, editId)

            logger.info("Image uploaded for $packageName ($language) [$imageType]")
        } catch (e: Exception) {
            logger.error("Failed to upload image for $packageName ($language) [$imageType]", e)
            runCatching { publisher.edits().delete(packageName, editId).execute() }
            throw PlayStoreException("Failed to upload image: ${e.message}", e)
        }
    }

    /**
     * Update Data Safety labels (CSV content).
     */
    fun updateDataSafety(packageName: String, safetyLabelsCsv: String) {
        logger.info("Updating data safety for $packageName")

        try {
            val request = SafetyLabelsUpdateRequest().setSafetyLabels(safetyLabelsCsv)
            publisher.applications().dataSafety(packageName, request).execute()
            logger.info("Data safety updated for $packageName")
        } catch (e: Exception) {
            logger.error("Failed to update data safety for $packageName", e)
            throw PlayStoreException("Failed to update data safety: ${e.message}", e)
        }
    }

    /**
     * Create a new subscription.
     */
    fun createSubscription(
        packageName: String,
        productId: String,
        regionsVersion: String?,
        subscriptionJson: String
    ): Subscription {
        logger.info("Creating subscription $productId for $packageName")

        try {
            val subscription = parseSubscriptionJson(subscriptionJson)
                .setPackageName(packageName)
                .setProductId(productId)

            val request = publisher.monetization().subscriptions().create(packageName, subscription)
                .setProductId(productId)

            regionsVersion?.let { request.setRegionsVersionVersion(it) }

            return request.execute()
        } catch (e: Exception) {
            logger.error("Failed to create subscription $productId for $packageName", e)
            throw PlayStoreException("Failed to create subscription: ${e.message}", e)
        }
    }

    /**
     * Patch an existing subscription.
     */
    fun updateSubscription(
        packageName: String,
        productId: String,
        regionsVersion: String?,
        subscriptionJson: String,
        updateMask: String?,
        allowMissing: Boolean?
    ): Subscription {
        logger.info("Updating subscription $productId for $packageName")

        try {
            val subscription = parseSubscriptionJson(subscriptionJson)
                .setPackageName(packageName)
                .setProductId(productId)

            val request = publisher.monetization().subscriptions().patch(packageName, productId, subscription)
            updateMask?.let { request.setUpdateMask(it) }
            allowMissing?.let { request.setAllowMissing(it) }
            regionsVersion?.let { request.setRegionsVersionVersion(it) }

            return request.execute()
        } catch (e: Exception) {
            logger.error("Failed to update subscription $productId for $packageName", e)
            throw PlayStoreException("Failed to update subscription: ${e.message}", e)
        }
    }

    /**
     * Get release information for an app
     */
    fun getReleases(packageName: String): List<PlayStoreRelease> {
        logger.debug("Fetching releases for: $packageName")

        return try {
            val editRequest = publisher.edits().insert(packageName, null)
            val edit = editRequest.execute()
            val editId = edit.id

            val tracks = publisher.edits().tracks().list(packageName, editId).execute()
            val releases = mutableListOf<PlayStoreRelease>()

            tracks?.tracks?.forEach { track ->
                track.releases?.forEach { release ->
                    releases.add(
                        PlayStoreRelease(
                            packageName = packageName,
                            track = track.track ?: "unknown",
                            status = release.status ?: "unknown",
                            versionCode = release.versionCodes?.firstOrNull() ?: 0,
                            rolloutPercentage = release.userFraction?.times(100)?.toInt() ?: 100,
                            startTime = System.currentTimeMillis(),
                            completedTime = if (release.status == "completed") System.currentTimeMillis() else null
                        )
                    )
                }
            }

            // Clean up the edit
            publisher.edits().delete(packageName, editId).execute()

            releases

        } catch (e: Exception) {
            logger.error("Failed to fetch releases for $packageName", e)
            throw PlayStoreException("Failed to fetch releases: ${e.message}", e)
        }
    }

    private fun parseSubscriptionJson(subscriptionJson: String): Subscription {
        val parser = GsonFactory.getDefaultInstance().createJsonParser(subscriptionJson)
        return parser.parseAndClose(Subscription::class.java)
    }

    private fun commitEditWithDraftFallback(packageName: String, editId: String) {
        try {
            publisher.edits().commit(packageName, editId).execute()
            return
        } catch (e: Exception) {
            if (!isDraftReleaseOnlyError(e)) {
                throw e
            }
            logger.warn(
                "Commit failed due to draft app release status. " +
                    "Attempting to convert existing releases to draft and retry."
            )
        }

        forceDraftReleases(packageName, editId)
        publisher.edits().commit(packageName, editId).execute()
    }

    private fun forceDraftReleases(packageName: String, editId: String) {
        val trackList = publisher.edits().tracks().list(packageName, editId).execute()
        trackList.tracks?.forEach { track ->
            val trackName = track.track ?: return@forEach
            val existingReleases = track.releases ?: return@forEach
            if (existingReleases.isEmpty()) {
                return@forEach
            }

            logger.info(
                "Updating ${existingReleases.size} releases to draft for $packageName track $trackName"
            )

            val updatedReleases = existingReleases.map { release ->
                TrackRelease()
                    .setName(release.name)
                    .setVersionCodes(release.versionCodes)
                    .setReleaseNotes(release.releaseNotes)
                    .setUserFraction(release.userFraction)
                    .setCountryTargeting(release.countryTargeting)
                    .setInAppUpdatePriority(release.inAppUpdatePriority)
                    .setStatus("draft")
            }

            val updatedTrack = Track()
                .setTrack(trackName)
                .setReleases(updatedReleases)

            publisher.edits().tracks().update(packageName, editId, trackName, updatedTrack).execute()
        }
    }

    private fun isDraftReleaseOnlyError(error: Exception): Boolean {
        val target = "Only releases with status draft may be created on draft app"
        var current: Throwable? = error
        while (current != null) {
            val message = current.message
            if (message?.contains(target, ignoreCase = true) == true) {
                return true
            }
            if (current is GoogleJsonResponseException) {
                val details = current.details
                if (details?.message?.contains(target, ignoreCase = true) == true) {
                    return true
                }
                val matched = details?.errors?.any { info ->
                    info?.message?.contains(target, ignoreCase = true) == true
                } ?: false
                if (matched) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    private fun detectImageMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    /**
     * Deploy a new version of an app
     */
    fun deployApp(
        packageName: String,
        track: String,
        apkPath: String,
        versionCode: Long,
        releaseNotes: String?,
        rolloutPercentage: Double = 1.0
    ): PlayStoreDeploymentResult {
        logger.info("Deploying app: $packageName to $track track, version $versionCode")

        return try {
            // Create a new edit
            val editRequest = publisher.edits().insert(packageName, null)
            val edit = editRequest.execute()
            val editId = edit.id

            logger.debug("Created edit: $editId")

            // Upload the APK/AAB
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                throw PlayStoreException("APK/AAB file not found: $apkPath")
            }

            val uploadRequest = if (apkPath.endsWith(".aab")) {
                publisher.edits().bundles().upload(
                    packageName, editId,
                    FileContent("application/octet-stream", apkFile)
                )
            } else {
                publisher.edits().apks().upload(
                    packageName, editId,
                    FileContent("application/vnd.android.package-archive", apkFile)
                )
            }

            uploadRequest.execute()
            logger.debug("Upload completed, version code: $versionCode")

            // Create a release
            val release = TrackRelease()
            release.name = "Release $versionCode"
            release.versionCodes = listOf(versionCode)
            
            // Set status and rollout percentage (userFraction in the API)
            if (rolloutPercentage < 1.0) {
                release.status = "inProgress"
                release.userFraction = rolloutPercentage
            } else {
                release.status = "completed"
            }

            if (!releaseNotes.isNullOrBlank()) {
                val releaseNote = LocalizedText()
                releaseNote.language = "en-US"
                releaseNote.text = releaseNotes
                release.releaseNotes = listOf(releaseNote)
            }

            // Update the track
            val trackUpdate = Track()
            trackUpdate.track = track
            trackUpdate.releases = listOf(release)

            publisher.edits().tracks().update(packageName, editId, track, trackUpdate).execute()

            // Commit the edit
            val commitRequest = publisher.edits().commit(packageName, editId)
            commitRequest.execute()

            logger.info("Successfully deployed $packageName version $versionCode to $track")

            PlayStoreDeploymentResult(
                success = true,
                deploymentId = editId,
                packageName = packageName,
                track = track,
                versionCode = versionCode,
                message = "Successfully deployed to $track track"
            )

        } catch (e: Exception) {
            logger.error("Failed to deploy app $packageName", e)
            PlayStoreDeploymentResult(
                success = false,
                deploymentId = null,
                packageName = packageName,
                track = track,
                versionCode = versionCode,
                message = "Deployment failed: ${e.message}",
                error = e
            )
        }
    }

    /**
     * Promote a release from one track to another
     */
    fun promoteRelease(
        packageName: String,
        fromTrack: String,
        toTrack: String,
        versionCode: Long
    ): PlayStoreDeploymentResult {
        logger.info("Promoting $packageName version $versionCode from $fromTrack to $toTrack")

        return try {
            val editRequest = publisher.edits().insert(packageName, null)
            val edit = editRequest.execute()
            val editId = edit.id

            // Get the release from source track
            val sourceTrackData = publisher.edits().tracks().get(packageName, editId, fromTrack).execute()
            val sourceRelease = sourceTrackData.releases?.find { release ->
                release.versionCodes?.contains(versionCode) == true
            } ?: throw PlayStoreException("Version $versionCode not found in $fromTrack track")

            // Create new release for target track
            val newRelease = TrackRelease()
            newRelease.name = sourceRelease.name
            newRelease.versionCodes = sourceRelease.versionCodes
            newRelease.status = "completed"
            newRelease.releaseNotes = sourceRelease.releaseNotes

            val targetTrack = Track()
            targetTrack.track = toTrack
            targetTrack.releases = listOf(newRelease)

            publisher.edits().tracks().update(packageName, editId, toTrack, targetTrack).execute()

            // Commit the changes
            publisher.edits().commit(packageName, editId).execute()

            logger.info("Successfully promoted $packageName version $versionCode to $toTrack")

            PlayStoreDeploymentResult(
                success = true,
                deploymentId = editId,
                packageName = packageName,
                track = toTrack,
                versionCode = versionCode,
                message = "Successfully promoted from $fromTrack to $toTrack"
            )

        } catch (e: Exception) {
            logger.error("Failed to promote release", e)
            PlayStoreDeploymentResult(
                success = false,
                deploymentId = null,
                packageName = packageName,
                track = toTrack,
                versionCode = versionCode,
                message = "Promotion failed: ${e.message}",
                error = e
            )
        }
    }

}

/**
 * Exception thrown by Play Store operations
 */
class PlayStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
