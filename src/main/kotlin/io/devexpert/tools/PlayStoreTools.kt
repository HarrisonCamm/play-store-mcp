package io.devexpert.tools

import io.devexpert.playstore.PlayStoreService
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

class PlayStoreTools(private val playStoreService: PlayStoreService) {
    private val logger = LoggerFactory.getLogger(PlayStoreTools::class.java)

    fun registerTools(server: Server) {
        logger.info("Registering Play Store deployment tools...")

        server.addTool(
            name = "deploy_app",
            description = "Deploy a new version of an app to Play Store",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app (e.g., com.example.myapp)"))
                    })
                    put("track", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Release track: internal, alpha, beta, production"))
                        put(
                            "enum", kotlinx.serialization.json.JsonArray(
                                listOf(
                                    JsonPrimitive("internal"),
                                    JsonPrimitive("alpha"),
                                    JsonPrimitive("beta"),
                                    JsonPrimitive("production")
                                )
                            )
                        )
                    })
                    put("apkPath", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to APK or AAB file"))
                    })
                    put("versionCode", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Version code (must be higher than current)"))
                    })
                    put("releaseNotes", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Release notes for this version"))
                    })
                    put("rolloutPercentage", buildJsonObject {
                        put("type", JsonPrimitive("number"))
                        put(
                            "description",
                            JsonPrimitive("Rollout percentage (0.0 to 1.0, default: 1.0 for full rollout)")
                        )
                        put("minimum", JsonPrimitive(0.0))
                        put("maximum", JsonPrimitive(1.0))
                    })
                },
                required = listOf("packageName", "track", "apkPath", "versionCode")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "unknown")
            val track = request.arguments.getArgument("track", "internal")
            val apkPath = request.arguments.getArgument("apkPath", "")
            val versionCode = request.arguments.getArgument("versionCode", 1L)
            val releaseNotes = request.arguments.getArgument("releaseNotes", "No release notes provided")
            val rolloutPercentage = request.arguments.getArgument("rolloutPercentage", 1.0)

            logger.info("Deploy app tool called: $packageName to $track track with ${(rolloutPercentage * 100).toInt()}% rollout")

            val deploymentResult =
                playStoreService.deployApp(packageName, track, apkPath, versionCode, releaseNotes, rolloutPercentage)

            val result = buildString {
                if (deploymentResult.success) {
                    appendLine("🚀 App Deployment Successful")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Track: $track")
                    appendLine("Version Code: $versionCode")
                    appendLine("Deployment ID: ${deploymentResult.deploymentId}")
                    appendLine("")
                    appendLine("✅ ${deploymentResult.message}")
                    appendLine("Started at: ${Instant.now()}")
                } else {
                    appendLine("❌ App Deployment Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Track: $track")
                    appendLine("Version Code: $versionCode")
                    appendLine("")
                    appendLine("Error: ${deploymentResult.message}")
                    deploymentResult.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = result)
                )
            )
        }

        server.addTool(
            name = "promote_release",
            description = "Promote a release from one track to another (e.g., alpha to beta)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("fromTrack", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Source track"))
                        put(
                            "enum", kotlinx.serialization.json.JsonArray(
                                listOf(
                                    JsonPrimitive("internal"),
                                    JsonPrimitive("alpha"),
                                    JsonPrimitive("beta")
                                )
                            )
                        )
                    })
                    put("toTrack", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Target track"))
                        put(
                            "enum", kotlinx.serialization.json.JsonArray(
                                listOf(
                                    JsonPrimitive("alpha"),
                                    JsonPrimitive("beta"),
                                    JsonPrimitive("production")
                                )
                            )
                        )
                    })
                    put("versionCode", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Version code to promote"))
                    })
                },
                required = listOf("packageName", "fromTrack", "toTrack", "versionCode")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "unknown")
            val fromTrack = request.arguments.getArgument("fromTrack", "internal")
            val toTrack = request.arguments.getArgument("toTrack", "alpha")
            val versionCode = request.arguments.getArgument("versionCode", 1L)

            logger.info("Promote release tool called: $packageName from $fromTrack to $toTrack")

            val promotionResult = playStoreService.promoteRelease(packageName, fromTrack, toTrack, versionCode)

            val result = buildString {
                if (promotionResult.success) {
                    appendLine("⬆️ Release Promotion Successful")
                    appendLine("============================")
                    appendLine("Package Name: $packageName")
                    appendLine("Version Code: $versionCode")
                    appendLine("From Track: $fromTrack")
                    appendLine("To Track: $toTrack")
                    appendLine("Promotion ID: ${promotionResult.deploymentId}")
                    appendLine("")
                    appendLine("✅ ${promotionResult.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Release Promotion Failed")
                    appendLine("============================")
                    appendLine("Package Name: $packageName")
                    appendLine("Version Code: $versionCode")
                    appendLine("From Track: $fromTrack")
                    appendLine("To Track: $toTrack")
                    appendLine("")
                    appendLine("Error: ${promotionResult.message}")
                    promotionResult.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = result)
                )
            )
        }

        server.addTool(
            name = "get_releases",
            description = "Get current status of app releases and deployments for a specific package",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app (e.g., com.example.myapp)"))
                    })
                },
                required = listOf("packageName")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }

            logger.info("Get releases tool called for package: $packageName")

            val releasesJson = playStoreService.getReleases(packageName)

            CallToolResult(
                content = listOf(
                    TextContent(text = releasesJson)
                )
            )
        }

        server.addTool(
            name = "update_store_listing",
            description = "Update localized store listing fields (title, short description, full description, video)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("language", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("BCP-47 language tag for the listing (e.g., en-US)"))
                    })
                    put("title", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Store listing title"))
                    })
                    put("shortDescription", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Short description"))
                    })
                    put("fullDescription", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Full description"))
                    })
                    put("video", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("YouTube video URL"))
                    })
                },
                required = listOf("packageName", "language")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val language = request.arguments.getArgument("language", "")
            val title = request.arguments.getOptionalArgument<String>("title")
            val shortDescription = request.arguments.getOptionalArgument<String>("shortDescription")
            val fullDescription = request.arguments.getOptionalArgument<String>("fullDescription")
            val video = request.arguments.getOptionalArgument<String>("video")

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (language.isBlank()) {
                throw IllegalArgumentException("language is required")
            }
            if (title == null && shortDescription == null && fullDescription == null && video == null) {
                throw IllegalArgumentException("At least one of title, shortDescription, fullDescription, or video is required")
            }

            logger.info("Update store listing tool called for $packageName ($language)")

            val result = playStoreService.updateStoreListing(
                packageName = packageName,
                language = language,
                title = title,
                shortDescription = shortDescription,
                fullDescription = fullDescription,
                video = video
            )

            val responseText = buildString {
                if (result.success) {
                    appendLine("📝 Store Listing Updated")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Language: $language")
                    title?.let { appendLine("Title: $it") }
                    shortDescription?.let { appendLine("Short Description: $it") }
                    fullDescription?.let { appendLine("Full Description: ${it.take(120)}${if (it.length > 120) "..." else ""}") }
                    video?.let { appendLine("Video: $it") }
                    appendLine("")
                    appendLine("✅ ${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Store Listing Update Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Language: $language")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        server.addTool(
            name = "update_app_details",
            description = "Update app details such as contact info and default language",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("defaultLanguage", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Default language for the app (e.g., en-US)"))
                    })
                    put("contactEmail", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Contact email address"))
                    })
                    put("contactPhone", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Contact phone number"))
                    })
                    put("contactWebsite", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Contact website URL"))
                    })
                },
                required = listOf("packageName")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val defaultLanguage = request.arguments.getOptionalArgument<String>("defaultLanguage")
            val contactEmail = request.arguments.getOptionalArgument<String>("contactEmail")
            val contactPhone = request.arguments.getOptionalArgument<String>("contactPhone")
            val contactWebsite = request.arguments.getOptionalArgument<String>("contactWebsite")

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (defaultLanguage == null && contactEmail == null && contactPhone == null && contactWebsite == null) {
                throw IllegalArgumentException("At least one of defaultLanguage, contactEmail, contactPhone, or contactWebsite is required")
            }

            logger.info("Update app details tool called for $packageName")

            val result = playStoreService.updateAppDetails(
                packageName = packageName,
                defaultLanguage = defaultLanguage,
                contactEmail = contactEmail,
                contactPhone = contactPhone,
                contactWebsite = contactWebsite
            )

            val responseText = buildString {
                if (result.success) {
                    appendLine("📇 App Details Updated")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    defaultLanguage?.let { appendLine("Default Language: $it") }
                    contactEmail?.let { appendLine("Contact Email: $it") }
                    contactPhone?.let { appendLine("Contact Phone: $it") }
                    contactWebsite?.let { appendLine("Contact Website: $it") }
                    appendLine("")
                    appendLine("✅ ${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ App Details Update Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        server.addTool(
            name = "upload_listing_image",
            description = "Upload store listing images, including screenshots",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("language", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("BCP-47 language tag for the listing (e.g., en-US)"))
                    })
                    put("imageType", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Image type (screenshots, icons, graphics)"))
                        put(
                            "enum", kotlinx.serialization.json.JsonArray(
                                listOf(
                                    JsonPrimitive("phoneScreenshots"),
                                    JsonPrimitive("sevenInchScreenshots"),
                                    JsonPrimitive("tenInchScreenshots"),
                                    JsonPrimitive("tvScreenshots"),
                                    JsonPrimitive("wearScreenshots"),
                                    JsonPrimitive("icon"),
                                    JsonPrimitive("featureGraphic"),
                                    JsonPrimitive("promoGraphic"),
                                    JsonPrimitive("tvBanner")
                                )
                            )
                        )
                    })
                    put("imagePath", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the image file (PNG/JPG/WEBP)"))
                    })
                    put("clearExisting", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Delete existing images for this type before upload"))
                    })
                },
                required = listOf("packageName", "language", "imageType", "imagePath")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val language = request.arguments.getArgument("language", "")
            val imageType = request.arguments.getArgument("imageType", "")
            val imagePath = request.arguments.getArgument("imagePath", "")
            val clearExisting = request.arguments.getArgument("clearExisting", false)

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (language.isBlank()) {
                throw IllegalArgumentException("language is required")
            }
            if (imageType.isBlank()) {
                throw IllegalArgumentException("imageType is required")
            }
            if (imagePath.isBlank()) {
                throw IllegalArgumentException("imagePath is required")
            }

            logger.info("Upload listing image tool called for $packageName ($language) [$imageType]")

            val result = playStoreService.uploadListingImage(
                packageName = packageName,
                language = language,
                imageType = imageType,
                imagePath = imagePath,
                clearExisting = clearExisting
            )

            val responseText = buildString {
                if (result.success) {
                    appendLine("🖼️ Listing Image Uploaded")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Language: $language")
                    appendLine("Image Type: $imageType")
                    appendLine("Image Path: $imagePath")
                    appendLine("Clear Existing: $clearExisting")
                    appendLine("")
                    appendLine("✅ ${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Listing Image Upload Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Language: $language")
                    appendLine("Image Type: $imageType")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        server.addTool(
            name = "set_data_safety",
            description = "Update Data Safety labels using a CSV payload",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("safetyLabelsCsv", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("CSV content for Data Safety labels"))
                    })
                    put("csvPath", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to a CSV file containing Data Safety labels"))
                    })
                },
                required = listOf("packageName")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val safetyLabelsCsvRaw = request.arguments.getOptionalArgument<String>("safetyLabelsCsv")
            val csvPathRaw = request.arguments.getOptionalArgument<String>("csvPath")
            val safetyLabelsCsv = safetyLabelsCsvRaw?.takeIf { it.isNotBlank() }
            val csvPath = csvPathRaw?.takeIf { it.isNotBlank() }

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (safetyLabelsCsv == null && csvPath == null) {
                throw IllegalArgumentException("Either safetyLabelsCsv or csvPath is required")
            }

            val csvContent = if (csvPath != null) {
                java.io.File(csvPath).readText()
            } else {
                safetyLabelsCsv ?: ""
            }

            logger.info("Set data safety tool called for $packageName")

            val result = playStoreService.updateDataSafety(packageName, csvContent)

            val responseText = buildString {
                if (result.success) {
                    appendLine("🛡️ Data Safety Updated")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    csvPath?.let { appendLine("CSV Path: $it") }
                    appendLine("")
                    appendLine("✅ ${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Data Safety Update Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        server.addTool(
            name = "create_subscription",
            description = "Create a monetization subscription from a JSON payload",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("productId", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Subscription product ID"))
                    })
                    put("regionsVersion", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Regions version for pricing/availability (optional)"))
                    })
                    put("subscriptionJson", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Subscription JSON payload"))
                    })
                    put("subscriptionJsonPath", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to a JSON file containing subscription payload"))
                    })
                },
                required = listOf("packageName", "productId", "regionsVersion")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val productId = request.arguments.getArgument("productId", "")
            val regionsVersion = request.arguments.getOptionalArgument<String>("regionsVersion")?.takeIf { it.isNotBlank() }
            val subscriptionJson = request.arguments.getOptionalArgument<String>("subscriptionJson")?.takeIf { it.isNotBlank() }
            val subscriptionJsonPath = request.arguments.getOptionalArgument<String>("subscriptionJsonPath")?.takeIf { it.isNotBlank() }

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (productId.isBlank()) {
                throw IllegalArgumentException("productId is required")
            }
            if (regionsVersion.isNullOrBlank()) {
                throw IllegalArgumentException("regionsVersion is required")
            }
            if (subscriptionJson == null && subscriptionJsonPath == null) {
                throw IllegalArgumentException("Either subscriptionJson or subscriptionJsonPath is required")
            }

            val payload = if (subscriptionJsonPath != null) {
                java.io.File(subscriptionJsonPath).readText()
            } else {
                subscriptionJson ?: ""
            }

            logger.info("Create subscription tool called for $packageName ($productId)")

            val result = playStoreService.createSubscription(packageName, productId, regionsVersion, payload)

            val responseText = buildString {
                if (result.success) {
                    appendLine("✅ Subscription Created")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Product ID: $productId")
                    regionsVersion?.let { appendLine("Regions Version: $it") }
                    appendLine("")
                    appendLine("${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Subscription Create Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Product ID: $productId")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        server.addTool(
            name = "update_subscription",
            description = "Patch a monetization subscription from a JSON payload",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("packageName", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Package name of the app"))
                    })
                    put("productId", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Subscription product ID"))
                    })
                    put("regionsVersion", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Regions version for pricing/availability (optional)"))
                    })
                    put("updateMask", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Comma-separated field mask for the update"))
                    })
                    put("allowMissing", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Create subscription if it does not exist"))
                    })
                    put("subscriptionJson", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Subscription JSON payload"))
                    })
                    put("subscriptionJsonPath", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to a JSON file containing subscription payload"))
                    })
                },
                required = listOf("packageName", "productId", "regionsVersion", "updateMask")
            )
        ) { request ->
            val packageName = request.arguments.getArgument("packageName", "")
            val productId = request.arguments.getArgument("productId", "")
            val regionsVersion = request.arguments.getOptionalArgument<String>("regionsVersion")?.takeIf { it.isNotBlank() }
            val updateMask = request.arguments.getOptionalArgument<String>("updateMask")?.takeIf { it.isNotBlank() }
            val allowMissing = request.arguments.getOptionalArgument<Boolean>("allowMissing")
            val subscriptionJson = request.arguments.getOptionalArgument<String>("subscriptionJson")?.takeIf { it.isNotBlank() }
            val subscriptionJsonPath = request.arguments.getOptionalArgument<String>("subscriptionJsonPath")?.takeIf { it.isNotBlank() }

            if (packageName.isBlank()) {
                throw IllegalArgumentException("packageName is required")
            }
            if (productId.isBlank()) {
                throw IllegalArgumentException("productId is required")
            }
            if (regionsVersion.isNullOrBlank()) {
                throw IllegalArgumentException("regionsVersion is required")
            }
            if (updateMask.isNullOrBlank()) {
                throw IllegalArgumentException("updateMask is required")
            }
            if (subscriptionJson == null && subscriptionJsonPath == null) {
                throw IllegalArgumentException("Either subscriptionJson or subscriptionJsonPath is required")
            }

            val payload = if (subscriptionJsonPath != null) {
                java.io.File(subscriptionJsonPath).readText()
            } else {
                subscriptionJson ?: ""
            }

            logger.info("Update subscription tool called for $packageName ($productId)")

            val result = playStoreService.updateSubscription(
                packageName = packageName,
                productId = productId,
                regionsVersion = regionsVersion,
                subscriptionJson = payload,
                updateMask = updateMask,
                allowMissing = allowMissing
            )

            val responseText = buildString {
                if (result.success) {
                    appendLine("✅ Subscription Updated")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Product ID: $productId")
                    regionsVersion?.let { appendLine("Regions Version: $it") }
                    updateMask?.let { appendLine("Update Mask: $it") }
                    allowMissing?.let { appendLine("Allow Missing: $it") }
                    appendLine("")
                    appendLine("${result.message}")
                    appendLine("Completed at: ${Instant.now()}")
                } else {
                    appendLine("❌ Subscription Update Failed")
                    appendLine("================================")
                    appendLine("Package Name: $packageName")
                    appendLine("Product ID: $productId")
                    appendLine("")
                    appendLine("Error: ${result.message}")
                    result.error?.let { error ->
                        appendLine("Details: ${error.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseText)))
        }

        logger.info(
            "Play Store tools registered successfully: deploy_app, promote_release, get_releases, " +
                "update_store_listing, update_app_details, upload_listing_image, set_data_safety, " +
                "create_subscription, update_subscription"
        )
    }
}

private inline fun <reified T> Map<String, Any>.getArgument(key: String, defaultValue: T): T {
    return (this[key] as? JsonPrimitive)?.content?.let {
        when (T::class) {
            String::class -> it as T
            Long::class -> it.toLongOrNull() as? T
            Double::class -> it.toDoubleOrNull() as? T
            Boolean::class -> it.toBooleanStrictOrNull() as? T
            else -> null
        }
    } ?: defaultValue
}

private inline fun <reified T> Map<String, Any>.getOptionalArgument(key: String): T? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return when (T::class) {
        String::class -> primitive.content as T
        Long::class -> primitive.content.toLongOrNull() as? T
        Double::class -> primitive.content.toDoubleOrNull() as? T
        Boolean::class -> primitive.content.toBooleanStrictOrNull() as? T
        else -> null
    }
}
