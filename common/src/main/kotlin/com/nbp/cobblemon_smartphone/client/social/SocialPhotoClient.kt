package com.nbp.cobblemon_smartphone.client.social

import com.mojang.blaze3d.platform.NativeImage
import com.nbp.cobblemon_smartphone.client.gui.SocialComposeScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.RequestSocialPhotoPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoChunkPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoUploadResultPacket
import com.nbp.cobblemon_smartphone.network.packet.UploadSocialPhotoPacket
import com.nbp.cobblemon_smartphone.social.SocialPhotoManager
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.world.item.ItemStack
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.concurrent.thread

/** Client-only capture, upload state and bounded runtime texture cache. */
object SocialPhotoClient {
    data class ComposeDraft(
        val color: SmartphoneColor,
        val stack: ItemStack?,
        val text: String,
        val dmTargetUuid: UUID? = null,
        val dmTargetName: String? = null
    )

    private data class Download(val total: Int, val bytes: ByteArrayOutputStream = ByteArrayOutputStream(total))
    private data class TextureEntry(val location: ResourceLocation, val texture: DynamicTexture)

    private var captureDraft: ComposeDraft? = null
    private var captureTicks = 0
    private var captureArmed = false
    private var cameraActive = false
    private var rightButtonWasDown = false
    private var previousHideGui = false
    private val uploadStates = mutableMapOf<UUID, Boolean?>()
    private val downloads = mutableMapOf<UUID, Download>()
    private val requested = mutableMapOf<UUID, Long>()
    private val textures = linkedMapOf<UUID, TextureEntry>()
    private val dimensions = mutableMapOf<UUID, Pair<Int, Int>>()

    fun isCameraActive(): Boolean = cameraActive

    fun beginCapture(
        color: SmartphoneColor,
        stack: ItemStack?,
        text: String,
        dmTargetUuid: UUID? = null,
        dmTargetName: String? = null
    ) {
        val client = Minecraft.getInstance()
        captureDraft = ComposeDraft(color, stack?.copy(), text, dmTargetUuid, dmTargetName)
        captureArmed = false
        cameraActive = true
        rightButtonWasDown = client.options.keyUse.isDown
        previousHideGui = client.options.hideGui
        client.options.hideGui = true
        client.setScreen(null)
    }

    fun takePhoto() {
        if (captureDraft == null) return
        val client = Minecraft.getInstance()
        cameraActive = false
        captureArmed = true
        captureTicks = 3
        client.setScreen(null)
    }

    fun cancelCamera() {
        val draft = captureDraft ?: return
        captureDraft = null
        captureArmed = false
        cameraActive = false
        val client = Minecraft.getInstance()
        client.options.hideGui = previousHideGui
        client.setScreen(SocialComposeScreen(draft.color, draft.stack, draft.text, dmTargetUuid = draft.dmTargetUuid, dmTargetName = draft.dmTargetName))
    }

    fun tick() {
        val draft = captureDraft ?: return
        if (cameraActive) {
            val rightButtonDown = Minecraft.getInstance().options.keyUse.isDown
            if (rightButtonDown && !rightButtonWasDown) {
                rightButtonWasDown = true
                takePhoto()
            } else {
                rightButtonWasDown = rightButtonDown
            }
            return
        }
        if (!captureArmed) return
        if (--captureTicks > 0) return
        captureArmed = false
        val client = Minecraft.getInstance()
        try {
            val source = Screenshot.takeScreenshot(client.mainRenderTarget)
            val capture = resizeNativeTo720p(source)
            captureDraft = null
            client.options.hideGui = previousHideGui
            processAndUpload(draft, capture)
        } catch (_: Exception) {
            captureDraft = null
            client.player?.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.nbp.social.photo_failed"), true)
            client.setScreen(SocialComposeScreen(draft.color, draft.stack, draft.text, dmTargetUuid = draft.dmTargetUuid, dmTargetName = draft.dmTargetName))
            client.options.hideGui = previousHideGui
        }
    }

    fun renderCameraOverlay(guiGraphics: GuiGraphics) {
        if (!cameraActive) return
        val client = Minecraft.getInstance()
        if (client.screen != null) return
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val cx = width / 2
        drawCorner(guiGraphics, 10, 10, 1, 1)
        drawCorner(guiGraphics, width - 11, 10, -1, 1)
        drawCorner(guiGraphics, 10, height - 11, 1, -1)
        drawCorner(guiGraphics, width - 11, height - 11, -1, -1)
        val hint = net.minecraft.network.chat.Component.translatable("cobblemon_smartphone.social.camera_right_click").string
        val font = client.font
        guiGraphics.fill(cx - font.width(hint) / 2 - 5, height - 27, cx + font.width(hint) / 2 + 5, height - 12, 0xB0071322.toInt())
        guiGraphics.drawString(font, hint, cx - font.width(hint) / 2, height - 24, OVERLAY_WHITE, false)
    }

    private fun drawCorner(guiGraphics: GuiGraphics, x: Int, y: Int, dx: Int, dy: Int) {
        val x2 = x + dx * 18
        val y2 = y + dy * 18
        guiGraphics.fill(minOf(x, x2), y, maxOf(x, x2) + 1, y + 1, OVERLAY_WHITE)
        guiGraphics.fill(x, minOf(y, y2), x + 1, maxOf(y, y2) + 1, OVERLAY_WHITE)
    }

    private fun resizeNativeTo720p(source: NativeImage): NativeImage {
        if (source.width == SocialPhotoManager.PHOTO_WIDTH && source.height == SocialPhotoManager.PHOTO_HEIGHT) return source
        val resized = NativeImage(SocialPhotoManager.PHOTO_WIDTH, SocialPhotoManager.PHOTO_HEIGHT, false)
        source.resizeSubRectTo(0, 0, source.width, source.height, resized)
        source.close()
        return resized
    }

    private fun processAndUpload(draft: ComposeDraft, source: NativeImage) {
        thread(name = "SmartphonePhotoProcessor", isDaemon = true) {
            try {
                val buffered = nativeToBuffered(source)
                source.close()
                val bytes = compressJpeg(buffered, SocialPhotoManager.MAX_BYTES)
                val preview = bufferedToNative(scalePreview(buffered))
                Minecraft.getInstance().execute {
                    val id = UUID.randomUUID()
                    dimensions[id] = preview.width to preview.height
                    registerTexture(id, preview)
                    uploadStates[id] = null
                    var offset = 0
                    while (offset < bytes.size) {
                        val end = (offset + UploadSocialPhotoPacket.MAX_CHUNK_BYTES).coerceAtMost(bytes.size)
                        UploadSocialPhotoPacket(id, bytes.size, offset, bytes.copyOfRange(offset, end)).sendToServer()
                        offset = end
                    }
                    Minecraft.getInstance().setScreen(
                        SocialComposeScreen(draft.color, draft.stack, draft.text, id, draft.dmTargetUuid, draft.dmTargetName)
                    )
                }
            } catch (_: Exception) {
                runCatching { source.close() }
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().player?.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.nbp.social.photo_failed"), true)
                    Minecraft.getInstance().setScreen(
                        SocialComposeScreen(draft.color, draft.stack, draft.text, dmTargetUuid = draft.dmTargetUuid, dmTargetName = draft.dmTargetName)
                    )
                }
            }
        }
    }

    private fun nativeToBuffered(source: NativeImage): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val abgr = source.getPixelRGBA(x, y)
                result.setRGB(x, y, FastColor.ARGB32.color(
                    FastColor.ABGR32.alpha(abgr),
                    FastColor.ABGR32.red(abgr),
                    FastColor.ABGR32.green(abgr),
                    FastColor.ABGR32.blue(abgr)
                ))
            }
        }
        return result
    }

    private fun decodePreview(bytes: ByteArray): NativeImage {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("Invalid photo")
        return bufferedToNative(scalePreview(image))
    }

    private fun scalePreview(source: BufferedImage): BufferedImage {
        val largest = maxOf(source.width, source.height)
        if (largest <= PREVIEW_MAX_DIMENSION) return source
        val scale = PREVIEW_MAX_DIMENSION.toDouble() / largest.toDouble()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = result.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun bufferedToNative(source: BufferedImage): NativeImage {
        val result = NativeImage(source.width, source.height, false)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                result.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(source.getRGB(x, y)))
            }
        }
        return result
    }

    private fun compressJpeg(image: BufferedImage, maxBytes: Int): ByteArray {
        var quality = 0.85f
        while (quality >= 0.35f) {
            val output = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val params = writer.defaultWriteParam.also {
                it.compressionMode = ImageWriteParam.MODE_EXPLICIT
                it.compressionQuality = quality
            }
            MemoryCacheImageOutputStream(output).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), params)
            }
            writer.dispose()
            val bytes = output.toByteArray()
            if (bytes.size <= maxBytes) return bytes
            quality -= 0.05f
        }
        throw IllegalStateException("Photo exceeds upload limit")
    }

    fun acceptUploadResult(packet: SocialPhotoUploadResultPacket) {
        uploadStates[packet.photoId] = packet.success
    }

    fun isUploadReady(id: UUID): Boolean = uploadStates[id] == true
    fun didUploadFail(id: UUID): Boolean = uploadStates[id] == false
    fun dimensions(id: UUID): Pair<Int, Int>? = dimensions[id]

    fun texture(id: UUID): ResourceLocation? = textures[id]?.location

    fun request(id: UUID) {
        if (textures.containsKey(id)) return
        val now = System.currentTimeMillis()
        if (now - (requested[id] ?: 0L) >= REQUEST_RETRY_MS) {
            requested[id] = now
            RequestSocialPhotoPacket(id).sendToServer()
        }
    }

    fun isRequested(id: UUID): Boolean = requested.containsKey(id)

    fun acceptDownload(packet: SocialPhotoChunkPacket) {
        if (packet.totalBytes !in 1..SocialPhotoManager.MAX_BYTES || packet.data.isEmpty()) return
        val download = downloads[packet.photoId] ?: run {
            if (packet.offset != 0) return
            Download(packet.totalBytes).also { downloads[packet.photoId] = it }
        }
        if (download.total != packet.totalBytes || download.bytes.size() != packet.offset || packet.offset + packet.data.size > packet.totalBytes) {
            downloads.remove(packet.photoId)
            return
        }
        download.bytes.write(packet.data)
        if (download.bytes.size() != download.total) return
        downloads.remove(packet.photoId)
        val bytes = download.bytes.toByteArray()
        thread(name = "SmartphonePhotoPreview", isDaemon = true) {
            runCatching { decodePreview(bytes) }
                .onSuccess { preview ->
                    Minecraft.getInstance().execute {
                        dimensions[packet.photoId] = preview.width to preview.height
                        registerTexture(packet.photoId, preview)
                    }
                }
                .onFailure { Minecraft.getInstance().execute { requested.remove(packet.photoId) } }
        }
    }

    fun removeDraft(id: UUID) {
        uploadStates.remove(id)
        releaseTexture(id)
        dimensions.remove(id)
        requested.remove(id)
    }

    private fun registerTexture(id: UUID, image: NativeImage) {
        val client = Minecraft.getInstance()
        releaseTexture(id)
        val texture = DynamicTexture(image)
        val location = client.textureManager.register("cobblemon_smartphone_social_$id", texture)
        textures[id] = TextureEntry(location, texture)
        while (textures.size > MAX_TEXTURES) {
            val oldest = textures.entries.first()
            releaseTexture(oldest.key)
            requested.remove(oldest.key)
            dimensions.remove(oldest.key)
        }
    }

    private fun releaseTexture(id: UUID) {
        val entry = textures.remove(id) ?: return
        Minecraft.getInstance().textureManager.release(entry.location)
    }

    private const val MAX_TEXTURES = 4
    private const val PREVIEW_MAX_DIMENSION = 640
    private const val REQUEST_RETRY_MS = 1_500L
    private const val OVERLAY_WHITE = 0xFFFFFFFF.toInt()
}
