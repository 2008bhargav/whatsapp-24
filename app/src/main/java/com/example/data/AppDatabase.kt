package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET isOnline = :isOnline WHERE username = :username")
    suspend fun setUserOnlineStatus(username: String, isOnline: Boolean)
}

@Dao
interface ChatRequestDao {
    @Query("SELECT * FROM chat_requests ORDER BY timestamp DESC")
    fun getAllRequestsFlow(): Flow<List<ChatRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ChatRequestEntity): Long

    @Query("UPDATE chat_requests SET status = :status WHERE id = :id")
    suspend fun updateRequestStatus(id: Int, status: String)

    @Query("SELECT * FROM chat_requests WHERE (senderUsername = :userA AND receiverUsername = :userB) OR (senderUsername = :userB AND receiverUsername = :userA) LIMIT 1")
    suspend fun getRequestForPair(userA: String, userB: String): ChatRequestEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderUsername = :userA AND receiverUsername = :userB) OR (senderUsername = :userB AND receiverUsername = :userA) ORDER BY timestamp ASC")
    fun getMessagesForPairFlow(userA: String, userB: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("UPDATE messages SET isFakeReported = 1 WHERE id = :id")
    suspend fun markFakeReported(id: Long)

    @Query("DELETE FROM messages WHERE disappearing = 1 AND timestamp + :expiryDuration < :currentTime")
    suspend fun purgeExpiredDisappearingMessages(expiryDuration: Long, currentTime: Long)
}

@Dao
interface CallDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallsFlow(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallLogEntity): Long

    @Query("DELETE FROM call_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [
        UserEntity::class,
        ChatRequestEntity::class,
        MessageEntity::class,
        CallLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val chatRequestDao: ChatRequestDao
    abstract val messageDao: MessageDao
    abstract val callDao: CallDao
}
