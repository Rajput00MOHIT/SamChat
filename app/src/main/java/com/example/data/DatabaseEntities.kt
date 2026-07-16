package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val passwordHash: String, // Plain-text or simple base64 since it's a secure local DB
    val currentPartnerId: Int? = null,
    val readReceiptsEnabled: Boolean = true,
    val lastSeenEnabled: Boolean = true,
    val typingIndicatorEnabled: Boolean = true,
    val isDiscoverable: Boolean = true,
    val lastSeenTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "friendships")
data class Friendship(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val friendId: Int,
    val isCouplePending: Boolean = false,
    val isCoupleAccepted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderId: Int,
    val recipientId: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isScreenshotAlert: Boolean = false
)

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val content: String,
    val imageUrl: String? = null, // Optional story slide image URL for ephemeral image slides
    val gradientIndex: Int = 0, // Visual style index for beautiful GenZ stories
    val timestamp: Long = System.currentTimeMillis(),
    val seenByPartner: Boolean = false,
    val textStyleIndex: Int = 0, // 0: Classic, 1: Modern Bold, 2: Cyber Neon, 3: Retro Typewriter, 4: Elegant Serif
    val stickerType: String = "none", // "none", "poll", "love_meter", "couple_q"
    val stickerQuestion: String = "", // Custom sticker prompt
    val stickerAnswer: String = "", // Partner response
    val reactionEmoji: String = "", // Partner quick reaction
    val isHearted: Boolean = false, // Heart reaction state
    val heartCount: Int = 0 // Heart reactions counter
)

@Entity(tableName = "call_history")
data class CallHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val callerId: Int,
    val receiverId: Int,
    val type: String, // "voice" or "video"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSec: Int = 0,
    val wasAnswered: Boolean = true
)

@Entity(tableName = "breakup_requests")
data class BreakupRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requesterId: Int,
    val partnerId: Int,
    val requestTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
