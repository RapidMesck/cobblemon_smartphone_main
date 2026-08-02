package com.nbp.cobblemon_smartphone.social

import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoChunkPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoUploadResultPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.imageio.ImageIO

/** Server-owned storage and strict, contiguous assembly for social photographs. */
object SocialPhotoManager {
    const val PHOTO_WIDTH = 1280
    const val PHOTO_HEIGHT = 720
    const val MAX_DIMENSION = PHOTO_WIDTH
    const val MAX_PIXELS = PHOTO_WIDTH * PHOTO_HEIGHT
    const val MAX_BYTES = 200 * 1024
    private const val UPLOAD_TIMEOUT_MS = 20_000L
    private const val DOWNLOAD_WINDOW_MS = 1_000L
    private const val MAX_DOWNLOADS_PER_WINDOW = 4

    private data class Upload(
        val owner: UUID,
        val total: Int,
        val startedAt: Long,
        val bytes: ByteArrayOutputStream = ByteArrayOutputStream(total)
    )

    private val uploads = mutableMapOf<UUID, Upload>()
    private val ready = mutableMapOf<UUID, Pair<UUID, SocialPhotoRef>>()
    private val downloadsByPlayer = mutableMapOf<UUID, ArrayDeque<Long>>()

    fun acceptChunk(server: MinecraftServer, player: ServerPlayer, id: UUID, total: Int, offset: Int, chunk: ByteArray) {
        cleanupExpired()
        if (total !in 1..MAX_BYTES || chunk.isEmpty() || chunk.size > com.nbp.cobblemon_smartphone.network.packet.UploadSocialPhotoPacket.MAX_CHUNK_BYTES) {
            fail(player, id)
            return
        }
        val upload = uploads[id] ?: run {
            if (offset != 0 || uploads.values.any { it.owner == player.uuid }) {
                fail(player, id)
                return
            }
            Upload(player.uuid, total, System.currentTimeMillis()).also { uploads[id] = it }
        }
        if (upload.owner != player.uuid || upload.total != total || upload.bytes.size() != offset || offset + chunk.size > total) {
            uploads.remove(id)
            fail(player, id)
            return
        }
        upload.bytes.write(chunk)
        if (upload.bytes.size() != total) return
        uploads.remove(id)

        val bytes = upload.bytes.toByteArray()
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
        if (image == null || image.width != PHOTO_WIDTH || image.height != PHOTO_HEIGHT) {
            fail(player, id)
            return
        }
        val path = photoPath(server, id)
        runCatching {
            Files.createDirectories(path.parent)
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW)
        }.onSuccess {
            val ref = SocialPhotoRef(id, image.width, image.height)
            ready[id] = player.uuid to ref
            SocialPhotoUploadResultPacket(id, true, ref.width, ref.height).sendToPlayer(player)
        }.onFailure { fail(player, id) }
    }

    fun claim(player: ServerPlayer, id: UUID?): SocialPhotoRef? {
        if (id == null) return null
        val entry = ready[id] ?: return null
        if (entry.first != player.uuid) return null
        ready.remove(id)
        return entry.second
    }

    fun send(server: MinecraftServer, player: ServerPlayer, id: UUID) {
        val now = System.currentTimeMillis()
        val recent = downloadsByPlayer.getOrPut(player.uuid) { ArrayDeque() }
        while (recent.isNotEmpty() && now - recent.first() >= DOWNLOAD_WINDOW_MS) recent.removeFirst()
        if (recent.size >= MAX_DOWNLOADS_PER_WINDOW) return
        if (!SocialData.get(server).canViewPhoto(player.uuid, id)) return
        val bytes = runCatching { Files.readAllBytes(photoPath(server, id)) }.getOrNull() ?: return
        if (bytes.size > MAX_BYTES) return
        recent.addLast(now)
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + com.nbp.cobblemon_smartphone.network.packet.UploadSocialPhotoPacket.MAX_CHUNK_BYTES).coerceAtMost(bytes.size)
            SocialPhotoChunkPacket(id, bytes.size, offset, bytes.copyOfRange(offset, end)).sendToPlayer(player)
            offset = end
        }
    }

    fun delete(server: MinecraftServer, id: UUID) {
        ready.remove(id)
        runCatching { Files.deleteIfExists(photoPath(server, id)) }
    }

    private fun fail(player: ServerPlayer, id: UUID) {
        SocialPhotoUploadResultPacket(id, false, 0, 0).sendToPlayer(player)
    }

    private fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - UPLOAD_TIMEOUT_MS
        uploads.entries.removeIf { it.value.startedAt < cutoff }
    }

    private fun photoPath(server: MinecraftServer, id: UUID) =
        server.getWorldPath(LevelResource.ROOT).resolve("cobblemon_smartphone").resolve("social_photos").resolve("$id.jpg")
}
