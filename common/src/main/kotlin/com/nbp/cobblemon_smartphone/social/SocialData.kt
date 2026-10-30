package com.nbp.cobblemon_smartphone.social

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * World-level store for the Social app, persisted alongside the world in `data/[NAME].dat`.
 *
 * Everything lives in memory and the whole object is re-serialized on each save, so growth is
 * bounded by `config.social.maxPosts` (ring buffer: oldest posts are evicted first).
 *
 * Every mutation must call [setDirty] or the change is silently lost on restart.
 */
class SocialData : SavedData() {

    private val posts = mutableListOf<SocialPost>()
    private var nextPostId: Long = 1L
    private var nextCommentId: Long = 1L

    private val threads = mutableMapOf<ThreadKey, DmThread>()
    private var nextMessageId: Long = 1L

    private val profiles = mutableMapOf<UUID, SocialProfile>()

    private val notifications = mutableMapOf<UUID, MutableList<SocialNotification>>()
    private var nextNotificationId: Long = 1L

    /** Players an operator has banned from posting. */
    private val postBans = mutableSetOf<UUID>()

    /** Players an operator has banned from sending DMs. */
    private val dmBans = mutableSetOf<UUID>()

    // --- Feed ---

    /** Posts newest-first. */
    fun feed(): List<SocialPost> = posts.asReversed()

    fun postById(id: Long): SocialPost? = posts.firstOrNull { it.id == id }

    fun addPost(
        authorUuid: UUID,
        authorName: String,
        text: String,
        attachment: PokemonAttachment?,
        photo: SocialPhotoRef? = null,
        poll: SocialPoll? = null
    ): SocialPost {
        val post = SocialPost(
            id = nextPostId++,
            authorUuid = authorUuid,
            authorName = authorName,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachment = attachment,
            photo = photo,
            poll = poll
        )
        posts.add(post)
        enforceRetention()
        setDirty()
        return post
    }

    fun removePost(id: Long): Boolean {
        val removed = posts.removeIf { it.id == id }
        if (removed) setDirty()
        return removed
    }

    /** Returns true when the post exists and the like state actually changed. */
    fun setLike(postId: Long, uuid: UUID, liked: Boolean): Boolean {
        val post = postById(postId) ?: return false
        val changed = if (liked) post.likes.add(uuid) else post.likes.remove(uuid)
        if (changed) setDirty()
        return changed
    }

    /** Registers or moves a vote on a post's poll. Returns true if vote state changed. */
    fun votePoll(postId: Long, uuid: UUID, optionIndex: Int): Boolean {
        val post = postById(postId) ?: return false
        val poll = post.poll ?: return false
        val changed = poll.vote(uuid, optionIndex)
        if (changed) setDirty()
        return changed
    }

    // --- Comments ---

    fun addComment(postId: Long, authorUuid: UUID, authorName: String, text: String): SocialComment? {
        val post = postById(postId) ?: return null
        val comment = SocialComment(
            id = nextCommentId++,
            postId = postId,
            authorUuid = authorUuid,
            authorName = authorName,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        post.comments.add(comment)
        enforceCommentRetention(post)
        setDirty()
        return comment
    }

    fun removeComment(postId: Long, commentId: Long): Boolean {
        val post = postById(postId) ?: return false
        val removed = post.comments.removeIf { it.id == commentId }
        if (removed) setDirty()
        return removed
    }

    fun commentsForPost(postId: Long): List<SocialComment> =
        postById(postId)?.comments ?: emptyList()

    private fun enforceCommentRetention(post: SocialPost) {
        val max = CobblemonSmartphone.config.social.maxCommentsPerPost
        if (max <= 0) return
        while (post.comments.size > max) {
            post.comments.removeAt(0)
        }
    }

    private fun enforceRetention() {
        val max = CobblemonSmartphone.config.social.maxPosts
        if (max <= 0) return
        while (posts.size > max) {
            posts.removeAt(0)
        }
    }

    // --- Direct messages ---

    fun thread(key: ThreadKey): DmThread? = threads[key]

    fun addMessage(
        sender: ServerPlayer,
        targetUuid: UUID,
        targetName: String,
        text: String,
        attachment: PokemonAttachment? = null,
        photo: SocialPhotoRef? = null
    ): DmMessage {
        val key = ThreadKey.of(sender.uuid, targetUuid)
        val thread = threads.getOrPut(key) { DmThread(key) }
        thread.rememberName(sender.uuid, sender.gameProfile.name)
        thread.rememberName(targetUuid, targetName)

        val message = DmMessage(
            id = nextMessageId++,
            senderUuid = sender.uuid,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachment = attachment,
            photo = photo
        )
        thread.messages.add(message)

        // Sending implies having read everything before it.
        thread.markRead(sender.uuid)
        enforceThreadRetention(thread)
        setDirty()
        return message
    }

    fun markThreadRead(viewer: UUID, other: UUID): Boolean {
        val thread = threads[ThreadKey.of(viewer, other)] ?: return false
        if (!thread.markRead(viewer)) return false
        setDirty()
        return true
    }

    /** Thread list for [viewer], most recently active first. Threads with no messages are skipped. */
    fun threadSummaries(viewer: UUID, server: MinecraftServer? = null): List<DmThreadSummary> =
        threads.values
            .filter { it.key.involves(viewer) && it.messages.isNotEmpty() }
            .map { DmThreadSummary.of(it, viewer, server) }
            .sortedByDescending { it.lastTimestamp }

    fun totalUnreadFor(viewer: UUID): Int =
        threads.values.filter { it.key.involves(viewer) }.sumOf { it.unreadCountFor(viewer) }

    fun canViewPhoto(viewer: UUID, photoId: UUID): Boolean =
        posts.any { it.photo?.id == photoId } ||
            threads.values.any { thread ->
                thread.key.involves(viewer) && thread.messages.any { it.photo?.id == photoId }
            }

    private fun enforceThreadRetention(thread: DmThread) {
        val max = CobblemonSmartphone.config.social.maxMessagesPerThread
        if (max <= 0) return
        while (thread.messages.size > max) {
            thread.messages.removeAt(0)
        }
    }

    // --- Moderation ---

    fun isPostBanned(uuid: UUID): Boolean = postBans.contains(uuid)

    fun isDmBanned(uuid: UUID): Boolean = dmBans.contains(uuid)

    fun setPostBanned(uuid: UUID, banned: Boolean) {
        val changed = if (banned) postBans.add(uuid) else postBans.remove(uuid)
        if (changed) setDirty()
    }

    fun setDmBanned(uuid: UUID, banned: Boolean) {
        val changed = if (banned) dmBans.add(uuid) else dmBans.remove(uuid)
        if (changed) setDirty()
    }

    // --- Profiles ---

    fun profileFor(uuid: UUID, fallbackName: String = ""): SocialProfile {
        return profiles.getOrPut(uuid) {
            SocialProfile(uuid = uuid, name = fallbackName)
        }.also {
            if (fallbackName.isNotBlank() && it.name.isBlank()) it.name = fallbackName
        }
    }

    fun updateBio(uuid: UUID, name: String, bio: String) {
        val profile = profileFor(uuid, name)
        profile.name = name
        profile.bio = bio
        setDirty()
    }

    fun setFavoritePokemon(uuid: UUID, name: String, species: String?, aspects: Set<String>) {
        val profile = profileFor(uuid, name)
        profile.name = name
        profile.favoriteSpecies = species
        profile.favoriteAspects = aspects
        setDirty()
    }

    fun toggleFollow(followerUuid: UUID, followerName: String, targetUuid: UUID, targetName: String): Boolean {
        if (followerUuid == targetUuid) return false
        val followerProfile = profileFor(followerUuid, followerName)
        val targetProfile = profileFor(targetUuid, targetName)

        val isNowFollowing = if (targetProfile.followers.contains(followerUuid)) {
            targetProfile.followers.remove(followerUuid)
            followerProfile.following.remove(targetUuid)
            false
        } else {
            targetProfile.followers.add(followerUuid)
            followerProfile.following.add(targetUuid)
            true
        }
        setDirty()
        return isNowFollowing
    }

    fun isFollowing(followerUuid: UUID, targetUuid: UUID): Boolean {
        return profiles[targetUuid]?.followers?.contains(followerUuid) ?: false
    }

    fun profileByName(name: String): SocialProfile? =
        profiles.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun allProfiles(): Collection<SocialProfile> = profiles.values

    // --- Notifications ---

    fun addNotification(
        targetUuid: UUID,
        actorUuid: UUID,
        actorName: String,
        type: SocialNotificationType,
        referenceId: Long = 0L,
        message: String = ""
    ): SocialNotification? {
        if (targetUuid == actorUuid) return null
        val notif = SocialNotification(
            id = nextNotificationId++,
            targetUuid = targetUuid,
            actorUuid = actorUuid,
            actorName = actorName,
            type = type,
            referenceId = referenceId,
            message = message,
            timestamp = System.currentTimeMillis(),
            read = false
        )
        val list = notifications.getOrPut(targetUuid) { mutableListOf() }
        list.add(notif)
        while (list.size > 50) list.removeAt(0)
        setDirty()
        return notif
    }

    fun notificationsFor(targetUuid: UUID): List<SocialNotification> =
        notifications[targetUuid]?.asReversed() ?: emptyList()

    fun markNotificationRead(targetUuid: UUID, notificationId: Long): Boolean {
        val list = notifications[targetUuid] ?: return false
        val changed = if (notificationId == 0L) {
            var any = false
            list.forEach { if (!it.read) { it.read = true; any = true } }
            any
        } else {
            val notif = list.firstOrNull { it.id == notificationId } ?: return false
            if (!notif.read) { notif.read = true; true } else false
        }
        if (changed) setDirty()
        return changed
    }

    fun unreadNotificationCountFor(targetUuid: UUID): Int =
        notifications[targetUuid]?.count { !it.read } ?: 0

    // --- Persistence ---

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val postList = ListTag()
        posts.forEach { postList.add(it.toNbt()) }
        tag.put(POSTS_KEY, postList)
        tag.putLong(NEXT_ID_KEY, nextPostId)
        tag.putLong(NEXT_COMMENT_ID_KEY, nextCommentId)

        val threadList = ListTag()
        threads.values.forEach { threadList.add(it.toNbt()) }
        tag.put(THREADS_KEY, threadList)
        tag.putLong(NEXT_MESSAGE_ID_KEY, nextMessageId)

        val profileList = ListTag()
        profiles.values.forEach { profileList.add(it.toNbt()) }
        tag.put(PROFILES_KEY, profileList)

        val notifList = ListTag()
        notifications.values.flatten().forEach { notifList.add(it.toNbt()) }
        tag.put(NOTIFICATIONS_KEY, notifList)
        tag.putLong(NEXT_NOTIFICATION_ID_KEY, nextNotificationId)

        tag.put(POST_BANS_KEY, uuidListTag(postBans))
        tag.put(DM_BANS_KEY, uuidListTag(dmBans))
        return tag
    }

    companion object {
        private const val NAME = "cobblemon_smartphone_social"
        private const val POSTS_KEY = "posts"
        private const val NEXT_ID_KEY = "next_post_id"
        private const val NEXT_COMMENT_ID_KEY = "next_comment_id"
        private const val THREADS_KEY = "threads"
        private const val NEXT_MESSAGE_ID_KEY = "next_message_id"
        private const val PROFILES_KEY = "profiles"
        private const val NOTIFICATIONS_KEY = "notifications"
        private const val NEXT_NOTIFICATION_ID_KEY = "next_notification_id"
        private const val POST_BANS_KEY = "post_bans"
        private const val DM_BANS_KEY = "dm_bans"

        private fun uuidListTag(uuids: Set<UUID>): ListTag {
            val list = ListTag()
            uuids.forEach { list.add(NbtUtils.createUUID(it)) }
            return list
        }

        private fun readUuidList(tag: CompoundTag, key: String): MutableSet<UUID> =
            tag.getList(key, Tag.TAG_INT_ARRAY.toInt())
                .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                .toMutableSet()

        private fun load(tag: CompoundTag): SocialData {
            val data = SocialData()
            tag.getList(POSTS_KEY, Tag.TAG_COMPOUND.toInt()).forEach { element ->
                SocialPost.fromNbt(element as CompoundTag)?.let { data.posts.add(it) }
            }
            data.nextPostId = tag.getLong(NEXT_ID_KEY).coerceAtLeast(1L)
            data.nextCommentId = tag.getLong(NEXT_COMMENT_ID_KEY).coerceAtLeast(1L)

            tag.getList(THREADS_KEY, Tag.TAG_COMPOUND.toInt()).forEach { element ->
                DmThread.fromNbt(element as CompoundTag)?.let { data.threads[it.key] = it }
            }
            data.nextMessageId = tag.getLong(NEXT_MESSAGE_ID_KEY).coerceAtLeast(1L)

            if (tag.contains(PROFILES_KEY)) {
                tag.getList(PROFILES_KEY, Tag.TAG_COMPOUND.toInt()).forEach { element ->
                    SocialProfile.fromNbt(element as CompoundTag)?.let { data.profiles[it.uuid] = it }
                }
            }

            data.nextNotificationId = tag.getLong(NEXT_NOTIFICATION_ID_KEY).coerceAtLeast(1L)
            if (tag.contains(NOTIFICATIONS_KEY)) {
                tag.getList(NOTIFICATIONS_KEY, Tag.TAG_COMPOUND.toInt()).forEach { element ->
                    SocialNotification.fromNbt(element as CompoundTag)?.let { notif ->
                        data.notifications.getOrPut(notif.targetUuid) { mutableListOf() }.add(notif)
                    }
                }
            }

            data.postBans.addAll(readUuidList(tag, POST_BANS_KEY))
            data.dmBans.addAll(readUuidList(tag, DM_BANS_KEY))
            return data
        }

        // dataFixTypes is @Nullable but has no Kotlin-visible default, so it must be passed.
        private val factory: Factory<SocialData>
            get() = Factory({ SocialData() }, { tag, _ -> load(tag) }, null)

        fun get(server: MinecraftServer): SocialData =
            server.overworld().dataStorage.computeIfAbsent(factory, NAME)
    }
}
