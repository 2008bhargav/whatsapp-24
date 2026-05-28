package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String, // e.g. "@alex_07"
    val fullName: String,
    val profilePicHex: String, // Color accent/avatar representation
    val bio: String,
    val isOnline: Boolean = false,
    val deviceVerified: Boolean = true,
    val publicKeyString: String = "" // E2E RSA Public Key representation
)

@Entity(tableName = "chat_requests")
data class ChatRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val timestamp: Long,
    val status: String // "PENDING", "ACCEPTED", "REJECTED"
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val encryptedPayloadHex: String,
    val signatureHex: String = "",
    val timestamp: Long,
    val status: String, // "SENT", "DELIVERED", "SEEN"
    val isVoice: Boolean = false,
    val voiceDurationSec: Int = 0,
    val mediaUrl: String? = null,
    val disappearing: Boolean = false,
    val isFakeReported: Boolean = false
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactUsername: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val timestamp: Long,
    val durationSec: Int, // 0 for missed
    val status: String // "MISSED", "COMPLETED", "REJECTED"
)
