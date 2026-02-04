package io.devexpert.playstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * Service for managing Play Store operations with fallback to mock data
 */
class PlayStoreService(private val config: PlayStoreConfig) {
    private val logger = LoggerFactory.getLogger(PlayStoreService::class.java)

    private val playStoreClient: PlayStoreClient = initializeClient()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        logger.info("Initializing Play Store Service...")
    }

    private fun initializeClient(): PlayStoreClient {
        val keyFile = File(config.serviceAccountKeyPath)
        if (!keyFile.exists()) {
            throw IllegalArgumentException("Service account key not found: ${config.serviceAccountKeyPath}")
        }

        return try {
            PlayStoreClient(config.serviceAccountKeyPath, config.applicationName)
        } catch (e: Exception) {
            logger.error("Failed to initialize Play Store client", e)
            throw e
        }
    }

    /**
     * Get release status for a specific package
     */
    suspend fun getReleases(packageName: String): String = withContext(Dispatchers.IO) {
        logger.debug("Fetching releases for package: $packageName")

        val releases = playStoreClient.getReleases(packageName)

        val result = mapOf(
            "packageName" to packageName,
            "releases" to releases,
            "summary" to mapOf(
                "totalReleases" to releases.size,
                "activeReleases" to releases.count { it.status == "inProgress" },
                "completedReleases" to releases.count { it.status == "completed" }
            ),
            "lastUpdate" to Instant.now().toString()
        )

        json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(result.mapValues {
                kotlinx.serialization.json.JsonPrimitive(it.value.toString())
            })
        )
    }

    /**
     * Deploy an app
     */
    suspend fun deployApp(
        packageName: String,
        track: String,
        apkPath: String,
        versionCode: Long,
        releaseNotes: String?,
        rolloutPercentage: Double = 1.0
    ): PlayStoreDeploymentResult = withContext(Dispatchers.IO) {
        logger.info("Deploying app: $packageName to $track with ${(rolloutPercentage * 100).toInt()}% rollout")
        playStoreClient.deployApp(packageName, track, apkPath, versionCode, releaseNotes, rolloutPercentage)
    }

    /**
     * Promote a release
     */
    suspend fun promoteRelease(
        packageName: String,
        fromTrack: String,
        toTrack: String,
        versionCode: Long
    ): PlayStoreDeploymentResult = withContext(Dispatchers.IO) {
        logger.info("Promoting release: $packageName from $fromTrack to $toTrack")
        playStoreClient.promoteRelease(packageName, fromTrack, toTrack, versionCode)
    }

    /**
     * Update store listing details.
     */
    suspend fun updateStoreListing(
        packageName: String,
        language: String,
        title: String?,
        shortDescription: String?,
        fullDescription: String?,
        video: String?
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Updating store listing for $packageName ($language)")

        return@withContext try {
            playStoreClient.updateStoreListing(packageName, language, title, shortDescription, fullDescription, video)
            PlayStoreOperationResult(
                success = true,
                message = "Store listing updated for $packageName ($language)",
                details = mapOf(
                    "packageName" to packageName,
                    "language" to language,
                    "title" to title,
                    "shortDescription" to shortDescription,
                    "fullDescription" to fullDescription,
                    "video" to video
                )
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "Store listing update failed: ${e.message}",
                details = mapOf("packageName" to packageName, "language" to language),
                error = e
            )
        }
    }

    /**
     * Update app details (contact info, default language).
     */
    suspend fun updateAppDetails(
        packageName: String,
        defaultLanguage: String?,
        contactEmail: String?,
        contactPhone: String?,
        contactWebsite: String?
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Updating app details for $packageName")

        return@withContext try {
            playStoreClient.updateAppDetails(packageName, defaultLanguage, contactEmail, contactPhone, contactWebsite)
            PlayStoreOperationResult(
                success = true,
                message = "App details updated for $packageName",
                details = mapOf(
                    "packageName" to packageName,
                    "defaultLanguage" to defaultLanguage,
                    "contactEmail" to contactEmail,
                    "contactPhone" to contactPhone,
                    "contactWebsite" to contactWebsite
                )
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "App details update failed: ${e.message}",
                details = mapOf("packageName" to packageName),
                error = e
            )
        }
    }

    /**
     * Upload a store listing image (including screenshots).
     */
    suspend fun uploadListingImage(
        packageName: String,
        language: String,
        imageType: String,
        imagePath: String,
        clearExisting: Boolean
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Uploading image for $packageName ($language): $imageType")

        return@withContext try {
            playStoreClient.uploadListingImage(packageName, language, imageType, imagePath, clearExisting)
            PlayStoreOperationResult(
                success = true,
                message = "Image uploaded for $packageName ($language) [$imageType]",
                details = mapOf(
                    "packageName" to packageName,
                    "language" to language,
                    "imageType" to imageType,
                    "imagePath" to imagePath,
                    "clearExisting" to clearExisting
                )
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "Image upload failed: ${e.message}",
                details = mapOf(
                    "packageName" to packageName,
                    "language" to language,
                    "imageType" to imageType
                ),
                error = e
            )
        }
    }

    /**
     * Update data safety labels.
     */
    suspend fun updateDataSafety(
        packageName: String,
        safetyLabelsCsv: String
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Updating data safety for $packageName")

        return@withContext try {
            playStoreClient.updateDataSafety(packageName, safetyLabelsCsv)
            PlayStoreOperationResult(
                success = true,
                message = "Data safety updated for $packageName",
                details = mapOf("packageName" to packageName)
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "Data safety update failed: ${e.message}",
                details = mapOf("packageName" to packageName),
                error = e
            )
        }
    }

    /**
     * Create a subscription.
     */
    suspend fun createSubscription(
        packageName: String,
        productId: String,
        regionsVersion: String?,
        subscriptionJson: String
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Creating subscription $productId for $packageName")

        return@withContext try {
            val subscription = playStoreClient.createSubscription(
                packageName,
                productId,
                regionsVersion,
                subscriptionJson
            )
            PlayStoreOperationResult(
                success = true,
                message = "Subscription created for $packageName ($productId)",
                details = mapOf(
                    "packageName" to packageName,
                    "productId" to productId,
                    "basePlans" to (subscription.basePlans?.size ?: 0),
                    "listings" to (subscription.listings?.mapNotNull { it.languageCode } ?: emptyList<String>())
                )
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "Subscription create failed: ${e.message}",
                details = mapOf("packageName" to packageName, "productId" to productId),
                error = e
            )
        }
    }

    /**
     * Update a subscription.
     */
    suspend fun updateSubscription(
        packageName: String,
        productId: String,
        regionsVersion: String?,
        subscriptionJson: String,
        updateMask: String?,
        allowMissing: Boolean?
    ): PlayStoreOperationResult = withContext(Dispatchers.IO) {
        logger.info("Updating subscription $productId for $packageName")

        return@withContext try {
            val subscription = playStoreClient.updateSubscription(
                packageName,
                productId,
                regionsVersion,
                subscriptionJson,
                updateMask,
                allowMissing
            )
            PlayStoreOperationResult(
                success = true,
                message = "Subscription updated for $packageName ($productId)",
                details = mapOf(
                    "packageName" to packageName,
                    "productId" to productId,
                    "basePlans" to (subscription.basePlans?.size ?: 0),
                    "listings" to (subscription.listings?.mapNotNull { it.languageCode } ?: emptyList<String>())
                )
            )
        } catch (e: Exception) {
            PlayStoreOperationResult(
                success = false,
                message = "Subscription update failed: ${e.message}",
                details = mapOf("packageName" to packageName, "productId" to productId),
                error = e
            )
        }
    }

}
