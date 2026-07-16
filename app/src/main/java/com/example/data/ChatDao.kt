package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- User Queries ---
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserFlow(id: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username LIKE :query AND id != :currentUserId")
    suspend fun searchUsers(query: String, currentUserId: Int): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    // --- Friendship Queries ---
    @Query("SELECT * FROM friendships WHERE userId = :userId OR friendId = :userId")
    fun getFriendshipsFlow(userId: Int): Flow<List<Friendship>>

    @Query("SELECT * FROM friendships WHERE (userId = :user1 AND friendId = :user2) OR (userId = :user2 AND friendId = :user1) LIMIT 1")
    suspend fun getFriendship(user1: Int, user2: Int): Friendship?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: Friendship): Long

    @Update
    suspend fun updateFriendship(friendship: Friendship)

    @Delete
    suspend fun deleteFriendship(friendship: Friendship)

    @Query("DELETE FROM friendships WHERE (userId = :user1 AND friendId = :user2) OR (userId = :user2 AND friendId = :user1)")
    suspend fun deleteFriendshipBetween(user1: Int, user2: Int)

    // --- Message Queries ---
    @Query("SELECT * FROM chat_messages WHERE (senderId = :user1 AND recipientId = :user2) OR (senderId = :user2 AND recipientId = :user1) ORDER BY timestamp ASC")
    fun getMessagesFlow(user1: Int, user2: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET isRead = 1 WHERE senderId = :senderId AND recipientId = :recipientId")
    suspend fun markMessagesAsRead(senderId: Int, recipientId: Int)

    @Query("DELETE FROM chat_messages WHERE (senderId = :user1 AND recipientId = :user2) OR (senderId = :user2 AND recipientId = :user1)")
    suspend fun deleteMessagesBetween(user1: Int, user2: Int)

    // --- Stories Queries ---
    @Query("SELECT * FROM stories WHERE userId = :userId OR userId = :partnerId ORDER BY timestamp DESC")
    fun getStoriesFlow(userId: Int, partnerId: Int): Flow<List<Story>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: Story): Long

    @Update
    suspend fun updateStory(story: Story)

    @Query("DELETE FROM stories WHERE userId = :user1 OR userId = :user2")
    suspend fun deleteStoriesForUsers(user1: Int, user2: Int)

    // --- Call History Queries ---
    @Query("SELECT * FROM call_history WHERE (callerId = :userId OR receiverId = :userId) ORDER BY timestamp DESC")
    fun getCallHistoryFlow(userId: Int): Flow<List<CallHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistory): Long

    @Query("DELETE FROM call_history WHERE (callerId = :user1 AND receiverId = :user2) OR (callerId = :user2 AND receiverId = :user1)")
    suspend fun deleteCallHistoryBetween(user1: Int, user2: Int)

    // --- Breakup Protocol Queries ---
    @Query("SELECT * FROM breakup_requests WHERE (requesterId = :user1 AND partnerId = :user2) OR (requesterId = :user2 AND partnerId = :user1) AND isActive = 1 LIMIT 1")
    suspend fun getActiveBreakupRequest(user1: Int, user2: Int): BreakupRequest?

    @Query("SELECT * FROM breakup_requests WHERE (requesterId = :userId OR partnerId = :userId) AND isActive = 1 LIMIT 1")
    fun getActiveBreakupRequestFlow(userId: Int): Flow<BreakupRequest?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakupRequest(request: BreakupRequest): Long

    @Update
    suspend fun updateBreakupRequest(request: BreakupRequest)

    @Query("DELETE FROM breakup_requests WHERE (requesterId = :user1 AND partnerId = :user2) OR (requesterId = :user2 AND partnerId = :user1)")
    suspend fun deleteBreakupRequestsBetween(user1: Int, user2: Int)
}
