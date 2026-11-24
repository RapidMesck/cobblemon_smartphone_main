package com.nbp.cobblemon_smartphone.client.dev

import com.mojang.blaze3d.platform.NativeImage
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.client.PokeInfoClientState
import com.nbp.cobblemon_smartphone.client.StructureCompassClientState
import com.nbp.cobblemon_smartphone.client.gui.*
import com.nbp.cobblemon_smartphone.client.social.*
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.social.*
import com.nbp.cobblemon_smartphone.util.GpsDataProvider
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import com.nbp.cobblemon_smartphone.util.StructureDataProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.Screen
import java.io.File
import java.util.UUID

object GuiScreenshotHarness {
    private val rawTarget = System.getProperty("smartphone.screenshot") ?: System.getenv("SMARTPHONE_SCREENSHOT")
    val isEnabled: Boolean = !rawTarget.isNullOrBlank()

    private val outputDir: File by lazy {
        val dirProp = System.getProperty("smartphone.screenshot.dir") ?: System.getenv("SMARTPHONE_SCREENSHOT_DIR")
        val dir = if (dirProp != null) {
            File(dirProp).canonicalFile
        } else {
            val current = File(".").canonicalFile
            val base = if (current.name == "run" && current.parentFile?.name == "fabric") {
                current.parentFile.parentFile
            } else {
                current
            }
            File(base, "build/screenshots")
        }
        dir.apply { mkdirs() }
    }

    private data class ScreenshotTask(
        val id: String,
        val category: String,
        val guiWidth: Int = 131,
        val guiHeight: Int = 207,
        val createScreen: () -> Screen,
        val postInit: () -> Unit = {}
    )

    private val allTasks: List<ScreenshotTask> by lazy {
        buildTasks()
    }

    private var activeQueue: List<ScreenshotTask> = emptyList()
    private var currentIndex = 0
    private var ticksInCurrentState = 0
    private var initialized = false
    private var stage = Stage.WARMUP

    private enum class Stage {
        WARMUP,
        LOAD_SCREEN,
        RENDER_WAIT,
        CAPTURE,
        DONE
    }

    private const val WARMUP_TICKS = 25
    private const val RENDER_WAIT_TICKS = 4

    fun tick(client: Minecraft) {
        if (!isEnabled) return

        // Wait until Mojang splash overlay is gone and TitleScreen or initial screen is active
        if (client.overlay != null || client.screen == null) {
            return
        }

        if (!initialized) {
            initializeQueue()
            initialized = true
        }

        when (stage) {
            Stage.WARMUP -> {
                ticksInCurrentState++
                if (ticksInCurrentState >= WARMUP_TICKS) {
                    println("[ScreenshotHarness] Warmup complete. Starting screenshot queue (${activeQueue.size} tasks)...")
                    stage = Stage.LOAD_SCREEN
                    ticksInCurrentState = 0
                }
            }

            Stage.LOAD_SCREEN -> {
                if (currentIndex >= activeQueue.size) {
                    stage = Stage.DONE
                    return
                }

                val task = activeQueue[currentIndex]
                println("[ScreenshotHarness] [${currentIndex + 1}/${activeQueue.size}] Opening screen '${task.id}'...")

                try {
                    val screen = task.createScreen()
                    client.setScreen(screen)
                    task.postInit()
                } catch (e: Throwable) {
                    System.err.println("[ScreenshotHarness] Error opening screen for task ${task.id}: ${e.message}")
                    e.printStackTrace()
                }

                stage = Stage.RENDER_WAIT
                ticksInCurrentState = 0
            }

            Stage.RENDER_WAIT -> {
                ticksInCurrentState++
                if (ticksInCurrentState >= RENDER_WAIT_TICKS) {
                    stage = Stage.CAPTURE
                    ticksInCurrentState = 0
                }
            }

            Stage.CAPTURE -> {
                val task = activeQueue[currentIndex]
                try {
                    captureScreenshot(client, task)
                } catch (e: Throwable) {
                    System.err.println("[ScreenshotHarness] Error capturing screenshot for ${task.id}: ${e.message}")
                    e.printStackTrace()
                }

                currentIndex++
                if (currentIndex < activeQueue.size) {
                    stage = Stage.LOAD_SCREEN
                } else {
                    stage = Stage.DONE
                    println("[ScreenshotHarness] ===================================================")
                    println("[ScreenshotHarness] ALL SCREENSHOTS CAPTURED SUCCESSFULLY!")
                    println("[ScreenshotHarness] Saved to: ${outputDir.absolutePath}")
                    println("[ScreenshotHarness] ===================================================")
                    client.stop()
                }
            }

            Stage.DONE -> {
                // Done; Minecraft client is stopping.
            }
        }
    }

    private fun initializeQueue() {
        val target = rawTarget?.trim() ?: "all"
        val targets = target.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        activeQueue = if (targets.contains("all")) {
            allTasks
        } else {
            allTasks.filter { task ->
                targets.any { query ->
                    task.id.lowercase().contains(query) || task.category.lowercase() == query
                }
            }
        }

        println("[ScreenshotHarness] Target selector: '$target'")
        println("[ScreenshotHarness] Matched ${activeQueue.size} screens:")
        activeQueue.forEach { println(" - ${it.id} (${it.category})") }

        if (activeQueue.isEmpty()) {
            System.err.println("[ScreenshotHarness] No matching screens found for: $target")
            Minecraft.getInstance().stop()
        }
    }

    private fun captureScreenshot(client: Minecraft, task: ScreenshotTask) {
        val renderTarget = client.mainRenderTarget
        val image = Screenshot.takeScreenshot(renderTarget)

        val fullFile = File(outputDir, "${task.id}.png")
        image.writeToFile(fullFile)
        println("[ScreenshotHarness] Saved full: ${fullFile.name} (${image.width}x${image.height})")

        // Also save cropped version focusing on the smartphone interface
        if (task.guiWidth > 0 && task.guiHeight > 0) {
            val croppedFile = File(outputDir, "${task.id}_cropped.png")
            saveCropped(image, croppedFile, client, task.guiWidth, task.guiHeight)
        }

        image.close()
    }

    private fun saveCropped(image: NativeImage, file: File, client: Minecraft, guiWidth: Int, guiHeight: Int) {
        try {
            val scale = client.window.guiScale
            val scaledW = client.window.guiScaledWidth
            val scaledH = client.window.guiScaledHeight

            val screenX = (scaledW - guiWidth) / 2
            val screenY = (scaledH - guiHeight) / 2

            val pixelX = (screenX * scale).toInt().coerceAtLeast(0)
            val pixelY = (screenY * scale).toInt().coerceAtLeast(0)
            val pixelW = (guiWidth * scale).toInt().coerceAtMost(image.width - pixelX)
            val pixelH = (guiHeight * scale).toInt().coerceAtMost(image.height - pixelY)

            if (pixelW > 0 && pixelH > 0) {
                val cropped = NativeImage(pixelW, pixelH, false)
                for (y in 0 until pixelH) {
                    for (x in 0 until pixelW) {
                        cropped.setPixelRGBA(x, y, image.getPixelRGBA(pixelX + x, pixelY + y))
                    }
                }
                cropped.writeToFile(file)
                cropped.close()
                println("[ScreenshotHarness] Saved cropped: ${file.name} (${pixelW}x${pixelH})")
            }
        } catch (e: Exception) {
            System.err.println("[ScreenshotHarness] Warning: Could not save cropped image: ${e.message}")
        }
    }

    private fun buildTasks(): List<ScreenshotTask> {
        val ashUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val garyUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")

        val sampleSpecies = listOf(
            PokeInfoDataProvider.SpeciesInfo("pikachu", "Pikachu", 25, "electric", null),
            PokeInfoDataProvider.SpeciesInfo("charizard", "Charizard", 6, "fire", "flying"),
            PokeInfoDataProvider.SpeciesInfo("bulbasaur", "Bulbasaur", 1, "grass", "poison"),
            PokeInfoDataProvider.SpeciesInfo("blastoise", "Blastoise", 9, "water", null),
            PokeInfoDataProvider.SpeciesInfo("gengar", "Gengar", 94, "ghost", "poison")
        )

        val sampleBiomes = listOf(
            GpsDataProvider.BiomeInfo("minecraft:plains", listOf("minecraft:is_overworld"), listOf("minecraft:overworld"), "Minecraft"),
            GpsDataProvider.BiomeInfo("minecraft:forest", listOf("minecraft:is_overworld"), listOf("minecraft:overworld"), "Minecraft"),
            GpsDataProvider.BiomeInfo("minecraft:desert", listOf("minecraft:is_overworld"), listOf("minecraft:overworld"), "Minecraft"),
            GpsDataProvider.BiomeInfo("minecraft:mountains", listOf("minecraft:is_overworld"), listOf("minecraft:overworld"), "Minecraft"),
            GpsDataProvider.BiomeInfo("minecraft:snowy_tundra", listOf("minecraft:is_overworld"), listOf("minecraft:overworld"), "Minecraft")
        )

        val sampleStructures = listOf(
            StructureDataProvider.StructureInfo("minecraft:village_plains", listOf("minecraft:overworld"), "Minecraft"),
            StructureDataProvider.StructureInfo("minecraft:desert_pyramid", listOf("minecraft:overworld"), "Minecraft"),
            StructureDataProvider.StructureInfo("minecraft:ancient_city", listOf("minecraft:overworld"), "Minecraft"),
            StructureDataProvider.StructureInfo("minecraft:monument", listOf("minecraft:overworld"), "Minecraft")
        )

        val samplePost = SocialPostView(
            id = 101L,
            authorUuid = ashUuid,
            authorName = "Red",
            text = "Just caught a wild Pikachu in Viridian Forest! #Pokemon #Cobblemon",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 30,
            attachment = PokemonAttachment("pikachu", emptySet(), 25, "Sparky"),
            photo = null,
            likeCount = 42,
            likedByMe = true,
            commentCount = 2
        )

        return listOf(
            // --- SMARTPHONE MAIN ---
            ScreenshotTask("smartphone_red", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.RED) }),
            ScreenshotTask("smartphone_blue", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.BLUE) }),
            ScreenshotTask("smartphone_green", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.GREEN) }),
            ScreenshotTask("smartphone_yellow", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.YELLOW) }),
            ScreenshotTask("smartphone_black", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.BLACK) }),
            ScreenshotTask("smartphone_white", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.WHITE) }),
            ScreenshotTask("smartphone_pink", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.PINK) }),
            ScreenshotTask("smartphone_cyan", "smartphone", 131, 207, { SmartphoneScreen(SmartphoneColor.CYAN) }),
            ScreenshotTask("smartphone_rotom_red", "smartphone", 131, 207, {
                SmartphoneActionOrder.setOrder(listOf("cobblemon_smartphone:rotom", "cobblemon_smartphone:pokeinfo", "cobblemon_smartphone:social", "cobblemon_smartphone:gps"))
                val phone = net.minecraft.world.item.ItemStack(com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems.RED_SMARTPHONE)
                com.nbp.cobblemon_smartphone.util.SmartphoneHelper.run { phone.setRotomPhone(true) }
                SmartphoneScreen(SmartphoneColor.RED, phone)
            }),

            // --- SETTINGS ---
            ScreenshotTask("smartphone_settings", "settings", 0, 0, { SmartphoneSettingsScreen(SmartphoneColor.RED) }),
            ScreenshotTask("smartphone_settings_hd", "settings", 0, 0, { SmartphoneSettingsScreen(SmartphoneColor.RED, null, SmartphoneSettingsScreen.SettingsTab.APPS, 1.0f, com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD) }),
            ScreenshotTask("smartphone_settings_themes", "settings", 0, 0, { SmartphoneSettingsScreen(SmartphoneColor.RED, null, SmartphoneSettingsScreen.SettingsTab.THEMES) }),
            ScreenshotTask("smartphone_settings_skins", "settings", 0, 0, { SmartphoneSettingsScreen(SmartphoneColor.RED, null, SmartphoneSettingsScreen.SettingsTab.SKINS) }),
            ScreenshotTask("smartphone_settings_display", "settings", 0, 0, { SmartphoneSettingsScreen(SmartphoneColor.RED, null, SmartphoneSettingsScreen.SettingsTab.DISPLAY, 1.0f, com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD) }),

            // --- POKEINFO ---
            ScreenshotTask(
                id = "pokeinfo",
                category = "pokeinfo",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { PokeInfoScreen(SmartphoneColor.RED) },
                postInit = {
                    val list = PokeInfoDataProvider.all().ifEmpty { sampleSpecies }
                    PokeInfoClientState.injectMockList(list)
                }
            ),
            ScreenshotTask(
                id = "pokeinfo_detail",
                category = "pokeinfo",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { PokeInfoDetailScreen(SmartphoneColor.RED, null, "pikachu", 25) },
                postInit = {
                    val details = PokeInfoDataProvider.getAllDetails("pikachu").ifEmpty {
                        listOf(
                            PokeInfoDataProvider.SpeciesDetail(
                                speciesId = "pikachu",
                                name = "Pikachu",
                                dexNumber = 25,
                                primaryType = "electric",
                                secondaryType = null,
                                height = 0.4f,
                                weight = 6.0f,
                                baseStats = PokeInfoDataProvider.StatsInfo(35, 55, 40, 50, 50, 90),
                                evYield = PokeInfoDataProvider.StatsInfo(0, 0, 0, 0, 0, 2),
                                abilities = listOf(PokeInfoDataProvider.AbilityInfo("Static", false, "cobblemon.ability.static.desc")),
                                preEvolution = "pichu",
                                evolutions = listOf(PokeInfoDataProvider.EvoInfo("Raichu", listOf(PokeInfoDataProvider.LocalizedText("item:thunder_stone")))),
                                moves = listOf(PokeInfoDataProvider.MoveInfo(1, "Thunder Shock", "level_up", "electric", "special", 40, 100)),
                                catchRate = 190,
                                baseExp = 112,
                                growthRate = "medium_fast",
                                baseFriendship = 50,
                                eggGroups = listOf("field", "fairy"),
                                eggCycles = 10,
                                maleRatio = 0.5f,
                                spawnEntries = emptyList(),
                                spawnBiomes = listOf("minecraft:plains", "minecraft:forest"),
                                availableForms = emptyList(),
                                selectedForm = "normal"
                            )
                        )
                    }
                    PokeInfoClientState.injectMockDetail(details)
                }
            ),

            // --- SOCIAL ---
            ScreenshotTask(
                id = "social_feed_hd",
                category = "social_preview",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialScreen(
                        SmartphoneColor.RED,
                        null,
                        startOnDms = false,
                        initialTab = SocialScreen.Tab.FEED,
                        scaleOverride = 1.0f,
                        styleOverride = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD
                    )
                },
                postInit = {
                    val posts = listOf(
                        samplePost.copy(
                            attachment = PokemonAttachment(
                                species = "cobblemon:pikachu",
                                aspects = setOf("shiny"),
                                level = 25,
                                nickname = "Sparky",
                                gender = "MALE",
                                ivs = listOf(31, 28, 25, 31, 30, 31),
                                types = listOf("electric")
                            )
                        ),
                        SocialPostView(
                            id = 102L,
                            authorUuid = garyUuid,
                            authorName = "Blue",
                            text = "Hey @Red, who takes the Cerulean Gym challenge today? #GymBattle",
                            timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                            attachment = null,
                            photo = null,
                            poll = SocialPollView(
                                question = "Best starter for Kanto?",
                                options = listOf("Bulbasaur", "Charmander", "Squirtle"),
                                voteCounts = listOf(12, 27, 19),
                                totalVotes = 58,
                                userVotedOption = 1
                            ),
                            likeCount = 18,
                            likedByMe = false,
                            commentCount = 4
                        )
                    )
                    SocialFeedCache.clear()
                    SocialFeedCache.accept(posts, false, false)
                    SocialDmCache.clear()
                    SocialDmCache.acceptThreadList(
                        listOf(DmThreadSummary(ashUuid, "Red", "Let's trade later today!", System.currentTimeMillis() - 1000 * 60 * 15, 2)),
                        false,
                        false
                    )
                }
            ),
            ScreenshotTask(
                id = "social_feed_pixel",
                category = "social_preview",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialScreen(
                        SmartphoneColor.RED,
                        null,
                        startOnDms = false,
                        initialTab = SocialScreen.Tab.FEED,
                        scaleOverride = 1.0f,
                        styleOverride = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.PIXEL
                    )
                }
            ),
            ScreenshotTask(
                id = "social_dms_hd",
                category = "social_preview",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialScreen(
                        SmartphoneColor.BLUE,
                        null,
                        startOnDms = true,
                        initialTab = SocialScreen.Tab.DMS,
                        scaleOverride = 1.0f,
                        styleOverride = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD
                    )
                },
                postInit = {
                    val threads = listOf(
                        DmThreadSummary(ashUuid, "Red", "Let's trade later today!", System.currentTimeMillis() - 1000 * 60 * 15, 2),
                        DmThreadSummary(garyUuid, "Blue", "You won't beat my Blastoise!", System.currentTimeMillis() - 1000 * 60 * 60, 0)
                    )
                    SocialDmCache.clear()
                    SocialDmCache.acceptThreadList(threads, false, false)
                }
            ),
            ScreenshotTask(
                id = "social_dms_pixel",
                category = "social_preview",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialScreen(
                        SmartphoneColor.BLUE,
                        null,
                        startOnDms = true,
                        initialTab = SocialScreen.Tab.DMS,
                        scaleOverride = 1.0f,
                        styleOverride = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.PIXEL
                    )
                }
            ),
            ScreenshotTask(
                id = "social_feed",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { SocialScreen(SmartphoneColor.RED, null, startOnDms = false, initialTab = SocialScreen.Tab.FEED, scaleOverride = 1.0f) },
                postInit = {
                    val posts = listOf(
                        samplePost.copy(
                            attachment = PokemonAttachment(
                                species = "cobblemon:pikachu",
                                aspects = setOf("shiny"),
                                level = 25,
                                nickname = "Sparky",
                                gender = "MALE",
                                ivs = listOf(31, 28, 25, 31, 30, 31),
                                types = listOf("electric")
                            )
                        ),
                        SocialPostView(
                            id = 102L,
                            authorUuid = garyUuid,
                            authorName = "Blue",
                            text = "Hey @Red, who takes the Cerulean Gym challenge today? #GymBattle",
                            timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                            attachment = null,
                            photo = null,
                            poll = SocialPollView(
                                question = "Best starter for Kanto?",
                                options = listOf("Bulbasaur", "Charmander", "Squirtle"),
                                voteCounts = listOf(12, 27, 19),
                                totalVotes = 58,
                                userVotedOption = 1
                            ),
                            likeCount = 18,
                            likedByMe = false,
                            commentCount = 4
                        )
                    )
                    SocialFeedCache.clear()
                    SocialFeedCache.accept(posts, false, false)
                }
            ),
            ScreenshotTask(
                id = "social_dms",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { SocialScreen(SmartphoneColor.BLUE, null, startOnDms = true, initialTab = SocialScreen.Tab.DMS, scaleOverride = 1.0f) },
                postInit = {
                    val threads = listOf(
                        DmThreadSummary(ashUuid, "Red", "Let's trade later today!", System.currentTimeMillis() - 1000 * 60 * 15, 2),
                        DmThreadSummary(garyUuid, "Blue", "You won't beat my Blastoise!", System.currentTimeMillis() - 1000 * 60 * 60, 0)
                    )
                    SocialDmCache.clear()
                    SocialDmCache.acceptThreadList(threads, false, false)
                }
            ),
            ScreenshotTask(
                id = "social_notifications",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { SocialScreen(SmartphoneColor.BLACK, null, startOnDms = false, initialTab = SocialScreen.Tab.NOTIFICATIONS, scaleOverride = 1.15f) },
                postInit = {
                    val notifs = listOf(
                        SocialNotificationView(1L, ashUuid, "Red", SocialNotificationType.LIKE, 101L, "liked your post", System.currentTimeMillis() - 1000 * 60 * 5, false),
                        SocialNotificationView(2L, garyUuid, "Blue", SocialNotificationType.COMMENT, 101L, "Great shiny catch!", System.currentTimeMillis() - 1000 * 60 * 30, true)
                    )
                    SocialNotificationCache.clear()
                    SocialNotificationCache.acceptList(notifs)
                }
            ),
            ScreenshotTask(
                id = "social_notif_settings",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialScreen(
                        SmartphoneColor.RED,
                        null,
                        startOnDms = false,
                        initialTab = SocialScreen.Tab.NOTIFICATIONS,
                        initialNotifSubTab = SocialScreen.NotificationSubTab.SETTINGS
                    )
                }
            ),
            ScreenshotTask(
                id = "social_compose",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { SocialComposeScreen(SmartphoneColor.RED, null, "Working on some exciting Cobblemon updates! #hype") }
            ),
            ScreenshotTask(
                id = "social_post_detail",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    SocialCommentCache.accept(101L, listOf(
                        SocialCommentView(1L, 101L, garyUuid, "Blue", "Pikachu is overrated, check my Eevee!", System.currentTimeMillis() - 1000 * 60 * 20),
                        SocialCommentView(2L, 101L, ashUuid, "Red", "Volt Tackle incoming!", System.currentTimeMillis() - 1000 * 60 * 10)
                    ))
                    SocialPostDetailScreen(SmartphoneColor.RED, null, samplePost)
                }
            ),
            ScreenshotTask(
                id = "dm_thread",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { DmThreadScreen(SmartphoneColor.RED, null, ashUuid, "Red") },
                postInit = {
                    val msgs = listOf(
                        DmMessage(1L, ashUuid, "Hey, ready for a Cobblemon battle?", System.currentTimeMillis() - 1000 * 60 * 20),
                        DmMessage(2L, UUID.randomUUID(), "I'm ready! Heading to the arena now.", System.currentTimeMillis() - 1000 * 60 * 10)
                    )
                    SocialDmCache.acceptThreadPage(ashUuid, "Red", msgs, false, false)
                }
            ),
            ScreenshotTask(
                id = "dm_new",
                category = "social",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { DmNewScreen(SmartphoneColor.RED, null) }
            ),

            // --- GPS & COMPASS ---
            ScreenshotTask(
                id = "gps",
                category = "gps",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { GpsScreen(SmartphoneColor.RED, null) },
                postInit = { GpsClientState.injectMockBiomes(sampleBiomes) }
            ),
            ScreenshotTask(
                id = "structure_compass",
                category = "gps",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { StructureCompassScreen(SmartphoneColor.RED, null) },
                postInit = { StructureCompassClientState.injectMockStructures(sampleStructures) }
            ),

            // --- ROTOM ---
            ScreenshotTask(
                id = "rotom_settings",
                category = "rotom",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.SETTINGS) }
            ),
            ScreenshotTask(
                id = "rotom_targets",
                category = "rotom",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    val config = com.nbp.cobblemon_smartphone.client.RotomRadarClientState.config
                    config.targets.clear()
                    config.targets.add(
                        com.nbp.cobblemon_smartphone.rotom.RotomTarget(
                            species = "pikachu",
                            minIvPercent = 80,
                            minPerfectIvs = 2,
                            nature = "timid",
                            shinyOnly = true,
                            enabled = true
                        )
                    )
                    config.targets.add(
                        com.nbp.cobblemon_smartphone.rotom.RotomTarget(
                            species = "charizard",
                            minIvPercent = 90,
                            minPerfectIvs = 3,
                            nature = "jolly",
                            shinyOnly = false,
                            enabled = false
                        )
                    )
                    RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.TARGETS, false)
                }
            ),
            ScreenshotTask(
                id = "rotom_targets_empty",
                category = "rotom",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    val config = com.nbp.cobblemon_smartphone.client.RotomRadarClientState.config
                    config.targets.clear()
                    RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.TARGETS, false)
                }
            ),
            ScreenshotTask(
                id = "rotom_add_target",
                category = "rotom",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = { RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.TARGETS, true) }
            ),

            // --- DATAPACK HD DEMO ---
            ScreenshotTask(
                id = "datapack_demo",
                category = "datapack",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    val demoJson = """
                        {
                          "id": "demo:trainer_hub",
                          "viewport": "large",
                          "default_state": { "tab": "party" },
                          "actions": {
                            "heal": { "effects": [ { "type": "close_modal" } ] }
                          },
                          "body": [
                            {
                              "type": "column",
                              "padding": { "horizontal": 8, "vertical": 5.5 },
                              "width": "fill",
                              "background": "header",
                              "gap": 5,
                              "children": [
                                {
                                  "type": "row",
                                  "gap": 6,
                                  "cross_align": "center",
                                  "width": "fill",
                                  "children": [
                                    { "type": "icon", "icon": "SPARKLES", "size": 13, "color": "gold" },
                                    { "type": "text", "text": "Trainer Hub - {player.name}", "size": 11.2, "weight": "bold", "color": "text_primary", "width": "weight(1)" },
                                    { "type": "text", "text": "{coins} G", "size": 9.4, "weight": "bold", "color": "gold" }
                                  ]
                                },
                                {
                                  "type": "tabs",
                                  "state_key": "tab",
                                  "tabs": [
                                    { "id": "party", "label": "Party ({lists.party.size})" },
                                    { "id": "rewards", "label": "Rewards" }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "scroll",
                              "scroll_id": "demo_party",
                              "visible_if": "state.tab == 'party'",
                              "padding": 8,
                              "gap": 6,
                              "for_each": "lists.party",
                              "children": [
                                {
                                  "type": "card",
                                  "padding": 7,
                                  "gap": 4,
                                  "children": [
                                    {
                                      "type": "row",
                                      "gap": 8,
                                      "cross_align": "center",
                                      "width": "fill",
                                      "children": [
                                        {
                                          "type": "column",
                                          "gap": 3,
                                          "width": "weight(1)",
                                          "children": [
                                            { "type": "text", "text": "#{item.index} {item.name}  Lv.{item.level}", "size": 10.0, "weight": "bold", "color": "text_dark" },
                                            { "type": "progress_bar", "progress": "{item.hp_ratio}", "h": 6.5, "fill_color": "success" },
                                            { "type": "text", "text": "HP {item.hp}/{item.max_hp} - {item.nature}", "size": 8.2, "color": "text_dark_muted" }
                                          ]
                                        },
                                        { "type": "button", "label": "Heal", "variant": "primary", "size": 8.8, "on_click": "heal" }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent()
                    val def = com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenLoader.parseScreen(demoJson)!!
                    com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState.registerLocal(def)
                    com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState.acceptData(
                        screenId = def.id,
                        placeholders = mapOf("player.name" to "Red", "coins" to "1250"),
                        lists = mapOf(
                            "party" to listOf(
                                mapOf("index" to "1", "name" to "Pikachu", "level" to "25", "hp" to "48", "max_hp" to "52", "hp_ratio" to "0.92", "nature" to "Timid"),
                                mapOf("index" to "2", "name" to "Charizard", "level" to "36", "hp" to "80", "max_hp" to "120", "hp_ratio" to "0.67", "nature" to "Jolly"),
                                mapOf("index" to "3", "name" to "Lapras", "level" to "30", "hp" to "115", "max_hp" to "115", "hp_ratio" to "1.0", "nature" to "Modest")
                            )
                        )
                    )
                    com.nbp.cobblemon_smartphone.client.gui.DatapackScreen(SmartphoneColor.RED, null, def.id)
                }
            ),
            ScreenshotTask(
                id = "datapack_demo_hd",
                category = "datapack",
                guiWidth = 0,
                guiHeight = 0,
                createScreen = {
                    com.nbp.cobblemon_smartphone.client.gui.DatapackScreen(
                        SmartphoneColor.RED,
                        null,
                        "demo:trainer_hub",
                        styleOverride = com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle.HD
                    )
                }
            )
        )
    }
}
