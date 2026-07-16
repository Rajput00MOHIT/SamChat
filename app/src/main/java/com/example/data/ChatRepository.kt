package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ChatRepository(private val chatDao: ChatDao) {

    // --- User Operations ---
    fun getUserFlow(userId: Int): Flow<User?> = chatDao.getUserFlow(userId)

    fun getAllUsersFlow(): Flow<List<User>> = chatDao.getAllUsersFlow()

    suspend fun getUserById(userId: Int): User? = chatDao.getUserById(userId)

    suspend fun getUserByUsername(username: String): User? = chatDao.getUserByUsername(username)

    suspend fun searchUsers(query: String, currentUserId: Int): List<User> {
        return chatDao.searchUsers("%$query%", currentUserId)
    }

    suspend fun registerUser(username: String, passwordHash: String): Long {
        val existing = chatDao.getUserByUsername(username)
        if (existing != null) return -1
        val newUser = User(username = username, passwordHash = passwordHash)
        return chatDao.insertUser(newUser)
    }

    suspend fun updateUser(user: User) {
        chatDao.updateUser(user)
    }

    // --- Friend & Couple Pairing ---
    fun getFriendshipsFlow(userId: Int): Flow<List<Friendship>> = chatDao.getFriendshipsFlow(userId)

    suspend fun getFriendship(user1: Int, user2: Int): Friendship? = chatDao.getFriendship(user1, user2)

    suspend fun sendPartnerRequest(senderId: Int, receiverId: Int): Boolean {
        // Check if relationship already exists
        val existing = chatDao.getFriendship(senderId, receiverId)
        if (existing != null) {
            if (!existing.isCoupleAccepted) {
                // If the other user already sent a request, accept it automatically!
                if (existing.userId == receiverId) {
                    val updated = existing.copy(isCoupleAccepted = true, isCouplePending = false)
                    chatDao.updateFriendship(updated)
                    // Link the users
                    val u1 = chatDao.getUserById(senderId)
                    val u2 = chatDao.getUserById(receiverId)
                    if (u1 != null && u2 != null) {
                        chatDao.updateUser(u1.copy(currentPartnerId = receiverId))
                        chatDao.updateUser(u2.copy(currentPartnerId = senderId))
                    }
                    return true
                }
            }
            return false
        }
        // Create new friendship in pending state
        val friendship = Friendship(userId = senderId, friendId = receiverId, isCouplePending = true)
        chatDao.insertFriendship(friendship)
        return true
    }

    suspend fun acceptPartnerRequest(senderId: Int, receiverId: Int) {
        val friendship = chatDao.getFriendship(senderId, receiverId) ?: return
        val updated = friendship.copy(isCoupleAccepted = true, isCouplePending = false)
        chatDao.updateFriendship(updated)

        val u1 = chatDao.getUserById(senderId)
        val u2 = chatDao.getUserById(receiverId)
        if (u1 != null && u2 != null) {
            chatDao.updateUser(u1.copy(currentPartnerId = receiverId))
            chatDao.updateUser(u2.copy(currentPartnerId = senderId))
        }
    }

    suspend fun declinePartnerRequest(senderId: Int, receiverId: Int) {
        val friendship = chatDao.getFriendship(senderId, receiverId) ?: return
        chatDao.deleteFriendship(friendship)
    }

    // --- Message & Stories ---
    fun getMessagesFlow(user1: Int, user2: Int): Flow<List<ChatMessage>> {
        if (user1 == 0 || user2 == 0) return flowOf(emptyList())
        return chatDao.getMessagesFlow(user1, user2)
    }

    suspend fun sendMessage(senderId: Int, recipientId: Int, content: String, isScreenshotAlert: Boolean = false): Long {
        val msg = ChatMessage(
            senderId = senderId,
            recipientId = recipientId,
            content = content,
            isScreenshotAlert = isScreenshotAlert
        )
        return chatDao.insertMessage(msg)
    }

    suspend fun markMessagesAsRead(senderId: Int, recipientId: Int) {
        chatDao.markMessagesAsRead(senderId, recipientId)
    }

    fun getStoriesFlow(userId: Int, partnerId: Int): Flow<List<Story>> {
        return chatDao.getStoriesFlow(userId, partnerId)
    }

    suspend fun postStory(
        userId: Int,
        content: String,
        gradientIndex: Int,
        textStyleIndex: Int = 0,
        stickerType: String = "none",
        stickerQuestion: String = "",
        imageUrl: String? = null
    ): Long {
        val story = Story(
            userId = userId,
            content = content,
            gradientIndex = gradientIndex,
            textStyleIndex = textStyleIndex,
            stickerType = stickerType,
            stickerQuestion = stickerQuestion,
            imageUrl = imageUrl
        )
        return chatDao.insertStory(story)
    }

    suspend fun updateStory(story: Story) {
        chatDao.updateStory(story)
    }

    // --- Calling ---
    fun getCallHistoryFlow(userId: Int): Flow<List<CallHistory>> {
        return chatDao.getCallHistoryFlow(userId)
    }

    suspend fun logCall(callerId: Int, receiverId: Int, type: String, durationSec: Int, wasAnswered: Boolean): Long {
        val call = CallHistory(
            callerId = callerId,
            receiverId = receiverId,
            type = type,
            durationSec = durationSec,
            wasAnswered = wasAnswered
        )
        return chatDao.insertCall(call)
    }

    // --- Breakup Protocol ---
    fun getActiveBreakupRequestFlow(userId: Int): Flow<BreakupRequest?> = chatDao.getActiveBreakupRequestFlow(userId)

    suspend fun getActiveBreakupRequest(user1: Int, user2: Int): BreakupRequest? = chatDao.getActiveBreakupRequest(user1, user2)

    suspend fun requestBreakup(requesterId: Int, partnerId: Int): Boolean {
        val existing = chatDao.getActiveBreakupRequest(requesterId, partnerId)
        if (existing != null) return false
        val req = BreakupRequest(requesterId = requesterId, partnerId = partnerId)
        chatDao.insertBreakupRequest(req)
        return true
    }

    suspend fun cancelBreakup(user1: Int, user2: Int) {
        chatDao.deleteBreakupRequestsBetween(user1, user2)
    }

    suspend fun executeBreakup(user1: Int, user2: Int) {
        // 1. Wipe all messages
        chatDao.deleteMessagesBetween(user1, user2)
        // 2. Wipe friendship
        chatDao.deleteFriendshipBetween(user1, user2)
        // 3. Wipe stories
        chatDao.deleteStoriesForUsers(user1, user2)
        // 4. Wipe call history
        chatDao.deleteCallHistoryBetween(user1, user2)
        // 5. Delete breakup requests
        chatDao.deleteBreakupRequestsBetween(user1, user2)

        // 6. Set partner pointers back to null
        val u1 = chatDao.getUserById(user1)
        if (u1 != null) {
            chatDao.updateUser(u1.copy(currentPartnerId = null))
        }
        val u2 = chatDao.getUserById(user2)
        if (u2 != null) {
            chatDao.updateUser(u2.copy(currentPartnerId = null))
        }
    }
}
