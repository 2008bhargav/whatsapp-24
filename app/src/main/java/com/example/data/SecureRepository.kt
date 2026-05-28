package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.security.KeyPair

class SecureRepository(private val context: Context) {

    // Database Instance
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "shield_chat.db"
        ).fallbackToDestructiveMigration().build()
    }

    val userDao get() = db.userDao
    val chatRequestDao get() = db.chatRequestDao
    val messageDao get() = db.messageDao
    val callDao get() = db.callDao

    // Generate custom RSA Key Pair for our local device
    private var localKeyPair: KeyPair? = null

    // Realtime Database Integration
    private val database: FirebaseDatabase? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                // Manually initialize using variables from Secrets panel
                try {
                    val apiKey = com.example.BuildConfig.FIREBASE_API_KEY
                    val appId = com.example.BuildConfig.FIREBASE_APP_ID
                    val projectId = com.example.BuildConfig.FIREBASE_PROJECT_ID
                    val dbUrl = "https://sheild-76698-default-rtdb.asia-southeast1.firebasedatabase.app/"
                    if (apiKey.isNotEmpty() && !apiKey.startsWith("YOUR_")) {
                        val options = com.google.firebase.FirebaseOptions.Builder()
                            .setApiKey(apiKey)
                            .setApplicationId(appId)
                            .setProjectId(projectId)
                            .setDatabaseUrl(dbUrl)
                            .build()
                        FirebaseApp.initializeApp(context, options)
                    }
                } catch (e: Exception) {
                    Log.e("SecureRepository", "Internal Firebase Init Error: \${e.message}")
                }
            }
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseDatabase.getInstance("https://sheild-76698-default-rtdb.asia-southeast1.firebasedatabase.app/")
            } else null
        } catch (e: Exception) {
            Log.e("SecureRepository", "RTDB not initialized: \${e.message}")
            null
        }
    }
    
    private var usersListener: ValueEventListener? = null
    private var requestsListener: ValueEventListener? = null
    private var messagesListener: ValueEventListener? = null

    init {
        // Start listening to real-time updates if Firebase is ready
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val db = database ?: return
        
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val users = snapshot.children.mapNotNull { doc ->
                            val username = doc.child("username").getValue(String::class.java) ?: return@mapNotNull null
                            UserEntity(
                                username = username,
                                fullName = doc.child("fullName").getValue(String::class.java) ?: "",
                                profilePicHex = doc.child("profilePicHex").getValue(String::class.java) ?: "#FF14B8A6",
                                bio = doc.child("bio").getValue(String::class.java) ?: "",
                                isOnline = doc.child("isOnline").getValue(Boolean::class.java) ?: false,
                                deviceVerified = doc.child("deviceVerified").getValue(Boolean::class.java) ?: true,
                                publicKeyString = doc.child("publicKeyString").getValue(String::class.java) ?: ""
                            )
                        }
                        userDao.insertUsers(users)
                    } catch (e: Exception) {
                        Log.e("SecureRepository", "Error syncing users: ${e.message}")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SecureRepository", "Users sync cancelled: ${error.message}")
            }
        }
        db.getReference("users").addValueEventListener(usersListener!!)
    }
    
    // Begin listening for personal requests and messages once we have a username
    fun initializeUserSync(myUsername: String) {
        val db = database ?: return
        
        requestsListener?.let { db.getReference("chat_requests").removeEventListener(it) }
        messagesListener?.let { db.getReference("messages").removeEventListener(it) }

        requestsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { doc ->
                    if (doc.child("receiverUsername").getValue(String::class.java) == myUsername) {
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val status = doc.child("status").getValue(String::class.java) ?: "PENDING"
                                chatRequestDao.insertRequest(
                                    ChatRequestEntity(
                                        senderUsername = doc.child("senderUsername").getValue(String::class.java) ?: "",
                                        receiverUsername = myUsername,
                                        timestamp = doc.child("timestamp").getValue(Long::class.java) ?: 0L,
                                        status = status
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("SecureRepository", "Error syncing chat requests: ${e.message}")
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SecureRepository", "Requests sync cancelled: ${error.message}")
            }
        }
        db.getReference("chat_requests").addValueEventListener(requestsListener!!)

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { doc ->
                    if (doc.child("receiverUsername").getValue(String::class.java) == myUsername) {
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                messageDao.insertMessage(
                                    MessageEntity(
                                        senderUsername = doc.child("senderUsername").getValue(String::class.java) ?: "",
                                        receiverUsername = myUsername,
                                        encryptedPayloadHex = doc.child("encryptedPayloadHex").getValue(String::class.java) ?: "",
                                        timestamp = doc.child("timestamp").getValue(Long::class.java) ?: 0L,
                                        status = doc.child("status").getValue(String::class.java) ?: "SEEN",
                                        disappearing = doc.child("disappearing").getValue(Boolean::class.java) ?: false
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("SecureRepository", "Error syncing messages: ${e.message}")
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SecureRepository", "Messages sync cancelled: ${error.message}")
            }
        }
        db.getReference("messages").addValueEventListener(messagesListener!!)
    }

    suspend fun getOrCreateLocalKeyPair(): KeyPair = withContext(Dispatchers.IO) {
        if (localKeyPair == null) {
            localKeyPair = SecureCrypto.generateRSAKeyPair()
        }
        localKeyPair!!
    }

    // No mock seeding anymore as per user request to remove demo data
    suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        // removed
    }

    // Fetch Messages
    fun getMessages(userA: String, userB: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForPairFlow(userA, userB)
    }

    // Send encrypted message real-time via Database
    suspend fun sendMessage(
        sender: String,
        receiver: String,
        plainText: String,
        isVoice: Boolean = false,
        voiceSec: Int = 0,
        mediaUrl: String? = null,
        isDisappearing: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val symmetricKey = SecureCrypto.generateSymmetricKeyHex(sender, receiver)
        val encryptedPayload = SecureCrypto.encryptAES(plainText, symmetricKey)
        val ts = System.currentTimeMillis()

        val message = MessageEntity(
            senderUsername = sender,
            receiverUsername = receiver,
            encryptedPayloadHex = encryptedPayload,
            timestamp = ts,
            status = "SENT",
            isVoice = isVoice,
            voiceDurationSec = voiceSec,
            mediaUrl = mediaUrl,
            disappearing = isDisappearing
        )
        // Save locally
        val id = messageDao.insertMessage(message)
        
        // Push to Database
        database?.getReference("messages")?.push()?.setValue(
            mapOf(
                "senderUsername" to sender,
                "receiverUsername" to receiver,
                "encryptedPayloadHex" to encryptedPayload,
                "timestamp" to ts,
                "status" to "SENT",
                "isVoice" to isVoice,
                "voiceDurationSec" to voiceSec,
                "mediaUrl" to mediaUrl,
                "disappearing" to isDisappearing
            )
        )
        
        id
    }

    // Mock reply simulation removed in production implementation
    suspend fun simulateMockReply(
        opponentUsername: String,
        myUsername: String,
        triggerText: String,
        onIncomingCall: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        // Left empty as we integrate Firebase for real E2EE responses!
    }

    // Publish Chat Request to Database
    suspend fun sendRealTimeChatRequest(sender: String, receiver: String) = withContext(Dispatchers.IO) {
        val req = ChatRequestEntity(
            senderUsername = sender,
            receiverUsername = receiver,
            timestamp = System.currentTimeMillis(),
            status = "PENDING"
        )
        chatRequestDao.insertRequest(req)

        database?.getReference("chat_requests")?.push()?.setValue(
            mapOf(
                "senderUsername" to sender,
                "receiverUsername" to receiver,
                "timestamp" to req.timestamp,
                "status" to req.status
            )
        )
    }

    suspend fun syncMyProfile(user: UserEntity) = withContext(Dispatchers.IO) {
        database?.getReference("users")?.child(user.username.replace(".", "_"))?.setValue(
            mapOf(
                "username" to user.username,
                "fullName" to user.fullName,
                "profilePicHex" to user.profilePicHex,
                "bio" to user.bio,
                "isOnline" to user.isOnline,
                "deviceVerified" to user.deviceVerified,
                "publicKeyString" to user.publicKeyString
            )
        )
    }

    // Auto accept request simulation to make search request fully functional & interactive in the emulator
    suspend fun simulateRequestAcceptance(sender: String, receiver: String) = withContext(Dispatchers.IO) {
        // Mock removed from here
    }
}
