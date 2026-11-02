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
        val croppedFile = File(outputDir, "${task.id}_cropped.png")
        saveCropped(image, croppedFile, client, task.guiWidth, task.guiHeight)

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
            ScreenshotTask("smartphone_settings", "settings", 131, 207, { SmartphoneSettingsScreen(SmartphoneColor.RED) }),
            ScreenshotTask("smartphone_settings_themes", "settings", 131, 207, { SmartphoneSettingsScreen(SmartphoneColor.RED, null, SmartphoneSettingsScreen.SettingsTab.THEMES) }),

            // --- POKEINFO ---
            ScreenshotTask(
                id = "pokeinfo",
                category = "pokeinfo",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { PokeInfoScreen(SmartphoneColor.RED) },
                postInit = {
                    val list = PokeInfoDataProvider.all().ifEmpty { sampleSpecies }
                    PokeInfoClientState.injectMockList(list)
                }
            ),
            ScreenshotTask(
                id = "pokeinfo_detail",
                category = "pokeinfo",
                guiWidth = 211,
                guiHeight = 207,
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
                id = "social_feed",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { SocialScreen(SmartphoneColor.RED, null, startOnDms = false, initialTab = SocialScreen.Tab.FEED) },
                postInit = {
                    val posts = listOf(
                        samplePost,
                        SocialPostView(
                            id = 102L,
                            authorUuid = garyUuid,
                            authorName = "Blue",
                            text = "Looking for gym challengers at Cerulean Gym today!",
                            timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                            attachment = null,
                            photo = null,
                            likeCount = 18,
                            likedByMe = false,
                            commentCount = 1
                        )
                    )
                    SocialFeedCache.clear()
                    SocialFeedCache.accept(posts, false, false)
                }
            ),
            ScreenshotTask(
                id = "social_dms",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { SocialScreen(SmartphoneColor.RED, null, startOnDms = true, initialTab = SocialScreen.Tab.DMS) },
                postInit = {
                    val threads = listOf(
                        DmThreadSummary(ashUuid, "Red", "Let's trade later today!", System.currentTimeMillis() - 1000 * 60 * 15, 1),
                        DmThreadSummary(garyUuid, "Blue", "You won't beat my Blastoise!", System.currentTimeMillis() - 1000 * 60 * 60, 0)
                    )
                    SocialDmCache.clear()
                    SocialDmCache.acceptThreadList(threads, false, false)
                }
            ),
            ScreenshotTask(
                id = "social_notifications",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { SocialScreen(SmartphoneColor.RED, null, startOnDms = false, initialTab = SocialScreen.Tab.NOTIFICATIONS) },
                postInit = {
                    val notifs = listOf(
                        SocialNotificationView(1L, ashUuid, "Red", SocialNotificationType.LIKE, 101L, "liked your post", System.currentTimeMillis() - 1000 * 60 * 5, false),
                        SocialNotificationView(2L, garyUuid, "Blue", SocialNotificationType.COMMENT, 101L, "commented: Great catch!", System.currentTimeMillis() - 1000 * 60 * 30, true)
                    )
                    SocialNotificationCache.clear()
                    SocialNotificationCache.acceptList(notifs)
                }
            ),
            ScreenshotTask(
                id = "social_compose",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { SocialComposeScreen(SmartphoneColor.RED, null, "Working on some exciting Cobblemon updates! #hype") }
            ),
            ScreenshotTask(
                id = "social_post_detail",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
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
                guiWidth = 211,
                guiHeight = 207,
                createScreen = {
                    val msgs = listOf(
                        DmMessage(1L, ashUuid, "Hey, ready for a Cobblemon battle?", System.currentTimeMillis() - 1000 * 60 * 20),
                        DmMessage(2L, UUID.randomUUID(), "I'm ready! Heading to the arena now.", System.currentTimeMillis() - 1000 * 60 * 10)
                    )
                    SocialDmCache.acceptThreadPage(ashUuid, "Red", msgs, false, false)
                    DmThreadScreen(SmartphoneColor.RED, null, ashUuid, "Red")
                }
            ),
            ScreenshotTask(
                id = "dm_new",
                category = "social",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { DmNewScreen(SmartphoneColor.RED, null) }
            ),

            // --- GPS & COMPASS ---
            ScreenshotTask(
                id = "gps",
                category = "gps",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { GpsScreen(SmartphoneColor.RED, null) },
                postInit = { GpsClientState.injectMockBiomes(sampleBiomes) }
            ),
            ScreenshotTask(
                id = "structure_compass",
                category = "gps",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { StructureCompassScreen(SmartphoneColor.RED, null) },
                postInit = { StructureCompassClientState.injectMockStructures(sampleStructures) }
            ),

            // --- ROTOM ---
            ScreenshotTask(
                id = "rotom_settings",
                category = "rotom",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.SETTINGS) }
            ),
            ScreenshotTask(
                id = "rotom_targets",
                category = "rotom",
                guiWidth = 211,
                guiHeight = 207,
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
                guiWidth = 211,
                guiHeight = 207,
                createScreen = {
                    val config = com.nbp.cobblemon_smartphone.client.RotomRadarClientState.config
                    config.targets.clear()
                    RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.TARGETS, false)
                }
            ),
            ScreenshotTask(
                id = "rotom_add_target",
                category = "rotom",
                guiWidth = 211,
                guiHeight = 207,
                createScreen = { RotomAppScreen(SmartphoneColor.RED, null, RotomAppScreen.Tab.TARGETS, true) }
            )
        )
    }
}
