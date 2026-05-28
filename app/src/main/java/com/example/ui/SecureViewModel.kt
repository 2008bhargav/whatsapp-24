package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SecureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SecureRepository(application)
    
    // Screens enum
    enum class Screen {
        SIGN_IN,
        PROFILE_CREATION,
        CORE_DASHBOARD,
        ACTIVE_CHAT,
        CALL_SCREEN,
        SECURITY_CENTER
    }

    // Active Bottom Navigation Tab
    enum class Tab {
        CHATS,
        REQUESTS,
        SEARCH,
        CALLS
    }

    // Navigation and Session
    private val _currentScreen = MutableStateFlow(Screen.SIGN_IN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _activeTab = MutableStateFlow(Tab.CHATS)
    val activeTab: StateFlow<Tab> = _activeTab.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Database Reactive Flows (with clean defaults)
    val allUsers: StateFlow<List<UserEntity>> = repository.userDao.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRequests: StateFlow<List<ChatRequestEntity>> = repository.chatRequestDao.getAllRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCallLogs: StateFlow<List<CallLogEntity>> = repository.callDao.getAllCallsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserEntity>>(emptyList())
    val searchResults: StateFlow<List<UserEntity>> = _searchResults.asStateFlow()

    // Active Conversation
    private val _activeChatPeer = MutableStateFlow<UserEntity?>(null)
    val activeChatPeer: StateFlow<UserEntity?> = _activeChatPeer.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<MessageEntity>> = _chatMessages.asStateFlow()

    // Security Configurations
    private val _screenshotProtection = MutableStateFlow(false)
    val screenshotProtection: StateFlow<Boolean> = _screenshotProtection.asStateFlow()

    private val _spamShield = MutableStateFlow(true)
    val spamShield: StateFlow<Boolean> = _spamShield.asStateFlow()

    private val _twoFactorEnabled = MutableStateFlow(false)
    val twoFactorEnabled: StateFlow<Boolean> = _twoFactorEnabled.asStateFlow()

    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    // VoIP Call State Info
    data class ActiveCallState(
        val peer: UserEntity,
        val isVideo: Boolean,
        val isIncoming: Boolean,
        val status: String, // "RINGING", "CONNECTING", "CONNECTED", "ENDED"
        val durationSec: Int = 0
    )
    private val _activeCall = MutableStateFlow<ActiveCallState?>(null)
    val activeCall: StateFlow<ActiveCallState?> = _activeCall.asStateFlow()

    init {
        viewModelScope.launch {
            // Check if there is an existing primary local account already seeded / created
            // Primary local profile will be stored in SQLite with username prefix @me_
            val list = repository.userDao.getAllUsersFlow().first()
            val savedProfile = list.find { it.username.startsWith("@me_") }
            
            if (FirebaseApp.getApps(application.applicationContext).isEmpty()) {
                try {
                    val apiKey = com.example.BuildConfig.FIREBASE_API_KEY
                    val appId = com.example.BuildConfig.FIREBASE_APP_ID
                    val projectId = com.example.BuildConfig.FIREBASE_PROJECT_ID
                    if (apiKey.isNotEmpty() && !apiKey.startsWith("YOUR_")) {
                        val options = com.google.firebase.FirebaseOptions.Builder()
                            .setApiKey(apiKey)
                            .setApplicationId(appId)
                            .setProjectId(projectId)
                            .build()
                        FirebaseApp.initializeApp(application.applicationContext, options)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("SecureViewModel", "Firebase init failed: \${e.message}")
                }
            }

            // If checking fails, verify Firebase Auth
            val isFirebaseInit = FirebaseApp.getApps(application.applicationContext).isNotEmpty()
            if (isFirebaseInit && FirebaseAuth.getInstance().currentUser != null) {
                // Already authenticated via Firebase
                val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                val suggestedUsername = "@me_" + email.substringBefore("@").replace(".", "_")
                val existingLocal = list.find { it.username == suggestedUsername }
                if (existingLocal != null) {
                    _currentUser.value = existingLocal
                    repository.initializeUserSync(suggestedUsername)
                    _currentScreen.value = Screen.CORE_DASHBOARD
                } else if (email.isNotEmpty()) {
                     // Could be logged into Firebase but not saved locally. Re-initiate profile creation
                     handleGoogleAuthSuccess(email, FirebaseAuth.getInstance().currentUser?.displayName ?: "User", true)
                }
            } else {
                 if (savedProfile != null) {
                    // This local profile is stale without Firebase Auth
                    // Let them see SignIn
                 }
            }
        }

        // Active chat message flow collector setup
        viewModelScope.launch {
            combine(_currentUser, _activeChatPeer) { me, peer ->
                Pair(me, peer)
            }.flatMapLatest { (me, peer) ->
                if (me != null && peer != null) {
                    repository.getMessages(me.username, peer.username)
                } else {
                    flowOf(emptyList())
                }
            }.collect { msgs ->
                _chatMessages.value = msgs
            }
        }

        // Search engine triggers
        viewModelScope.launch {
            _searchQuery.collect { query ->
                val q = query.trim()
                if (q.isEmpty()) {
                    _searchResults.value = emptyList()
                } else {
                    val list = repository.userDao.getAllUsersFlow().first()
                    _searchResults.value = list.filter {
                        (it.username.contains(q, ignoreCase = true) || it.fullName.contains(q, ignoreCase = true)) &&
                                !it.username.startsWith("@me_")
                    }
                }
            }
        }

        // Start call timer ticking inside coroutines when call is connected
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val call = _activeCall.value
                if (call != null && call.status == "CONNECTED") {
                    _activeCall.value = call.copy(durationSec = call.durationSec + 1)
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun handleGoogleAuthSuccess(email: String, name: String, fromFirebase: Boolean = false) {
        viewModelScope.launch {
            try {
                val suggestedUsername = "@me_" + email.substringBefore("@").replace(".", "_")
                val existing = repository.userDao.getUserByUsername(suggestedUsername)
                if (existing != null) {
                    // Restore account
                    _currentUser.value = existing
                    repository.initializeUserSync(suggestedUsername)
                    _currentScreen.value = Screen.CORE_DASHBOARD
                } else {
                    // Go to Profile Creation Screen to allow them to create customized credentials!
                    _currentUser.value = UserEntity(
                        username = suggestedUsername,
                        fullName = name,
                        profilePicHex = "#FF10B981",
                        bio = "Secure E2EE Chat user"
                    )
                    _currentScreen.value = Screen.PROFILE_CREATION
                }
            } catch (e: Exception) {
                android.util.Log.e("Auth", "Error retrieving user from DB", e)
            }
        }
    }

    // Complete local profile creation with customized bio/unique username
    fun completeProfileCreation(customUsername: String, fullName: String, picResHex: String, bio: String) {
        viewModelScope.launch {
            try {
                val cleanUsername = if (customUsername.startsWith("@")) customUsername else "@$customUsername"
                
                // Generate RSA Key pair for this specific fresh device identity
                val keyPair = repository.getOrCreateLocalKeyPair()
                val pubKeyStr = SecureCrypto.publicKeyToString(keyPair.public)

                val newProfile = UserEntity(
                    username = cleanUsername,
                    fullName = fullName,
                    profilePicHex = picResHex,
                    bio = bio,
                    isOnline = true,
                    deviceVerified = true,
                    publicKeyString = pubKeyStr
                )

                // Save to database & Sync to Firestore
                repository.userDao.insertUser(newProfile)
                repository.syncMyProfile(newProfile)
                repository.initializeUserSync(cleanUsername)

                _currentUser.value = newProfile
                _currentScreen.value = Screen.CORE_DASHBOARD
            } catch (e: Exception) {
                android.util.Log.e("Auth", "Error completing profile creation", e)
            }
        }
    }

    // Request System Operations
    fun sendChatRequest(targetUsername: String) {
        viewModelScope.launch {
            val myName = _currentUser.value?.username ?: return@launch
            
            // Check if request already exists
            val existing = repository.chatRequestDao.getRequestForPair(myName, targetUsername)
            if (existing == null) {
                repository.sendRealTimeChatRequest(myName, targetUsername)
            }
        }
    }

    fun respondToChatRequest(requestId: Int, accept: Boolean) {
        viewModelScope.launch {
            val status = if (accept) "ACCEPTED" else "REJECTED"
            repository.chatRequestDao.updateRequestStatus(requestId, status)

            // If accepted, check for peer details and insert E2EE keys setup announcement
            if (accept) {
                val reqs = allRequests.value
                val req = reqs.find { it.id == requestId } ?: return@launch
                val (me, peer) = if (req.senderUsername == _currentUser.value?.username) {
                    Pair(req.senderUsername, req.receiverUsername)
                } else {
                    Pair(req.receiverUsername, req.senderUsername)
                }
                val keyHex = SecureCrypto.generateSymmetricKeyHex(me, peer)
                val encTxt = SecureCrypto.encryptAES("Secure chat request accepted! Cryptographic keys exchanged. Session secured.", keyHex)
                repository.messageDao.insertMessage(
                    MessageEntity(
                        senderUsername = peer,
                        receiverUsername = me,
                        encryptedPayloadHex = encTxt,
                        timestamp = System.currentTimeMillis(),
                        status = "SEEN"
                    )
                )
            }
        }
    }

    // Message Operations
    fun sendMessage(text: String, isVoice: Boolean = false, voiceSec: Int = 0, isDisappearing: Boolean = false) {
        viewModelScope.launch {
            val me = _currentUser.value?.username ?: return@launch
            val peer = _activeChatPeer.value?.username ?: return@launch
            
            repository.sendMessage(
                sender = me,
                receiver = peer,
                plainText = text,
                isVoice = isVoice,
                voiceSec = voiceSec,
                isDisappearing = isDisappearing
            )
        }
    }

    fun deleteMessage(msgId: Long) {
        viewModelScope.launch {
            repository.messageDao.deleteMessageById(msgId)
        }
    }

    fun reportFakeAccount(messageId: Long) {
        viewModelScope.launch {
            repository.messageDao.markFakeReported(messageId)
            // Flag first
            delay(500)
            // Auto block suspicious user
            val peer = _activeChatPeer.value?.username
            if (peer != null) {
                toggleBlockUser(peer)
            }
        }
    }

    // Search and Tabs
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTab(tab: Tab) {
        _activeTab.value = tab
    }

    fun openChat(peer: UserEntity) {
        _activeChatPeer.value = peer
        _currentScreen.value = Screen.ACTIVE_CHAT
    }

    fun closeChat() {
        _activeChatPeer.value = null
        _currentScreen.value = Screen.CORE_DASHBOARD
    }

    fun toggleScreenshotProtection() {
        val next = !_screenshotProtection.value
        _screenshotProtection.value = next
    }

    fun toggleSpamShield() {
         _spamShield.value = !_spamShield.value
    }

    fun toggle2FA() {
        _twoFactorEnabled.value = !_twoFactorEnabled.value
    }

    fun toggleBlockUser(username: String) {
        val current = _blockedUsers.value.toMutableSet()
        if (current.contains(username)) {
            current.remove(username)
        } else {
            current.add(username)
        }
        _blockedUsers.value = current
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Call Engine Simulation
    fun triggerCallSimulation(peerUsername: String, isVoiceOnly: Boolean, isIncoming: Boolean) {
        viewModelScope.launch {
            val list = repository.userDao.getAllUsersFlow().first()
            val peer = list.find { it.username == peerUsername } ?: return@launch
            
            _activeCall.value = ActiveCallState(
                peer = peer,
                isVideo = !isVoiceOnly,
                isIncoming = isIncoming,
                status = if (isIncoming) "RINGING" else "CONNECTING"
            )
            _currentScreen.value = Screen.CALL_SCREEN

            if (!isIncoming) {
                delay(1200)
                val curr = _activeCall.value
                if (curr != null && curr.status == "CONNECTING") {
                    _activeCall.value = curr.copy(status = "CONNECTED")
                }
            }
        }
    }

    fun acceptIncomingCall() {
        val call = _activeCall.value ?: return
        _activeCall.value = call.copy(status = "CONNECTED")
    }

    fun hangupCall() {
        viewModelScope.launch {
            val call = _activeCall.value ?: return@launch
            val duration = call.durationSec
            
            _activeCall.value = call.copy(status = "ENDED")
            
            // Save to call history logs
            repository.callDao.insertCall(
                CallLogEntity(
                    contactUsername = call.peer.username,
                    isVideo = call.isVideo,
                    isIncoming = call.isIncoming,
                    timestamp = System.currentTimeMillis(),
                    durationSec = duration,
                    status = if (duration > 0) "COMPLETED" else if (call.isIncoming) "MISSED" else "REJECTED"
                )
            )

            delay(1000)
            _activeCall.value = null
            // Check if we originated screen from chat or dashboard
            if (_activeChatPeer.value != null) {
                _currentScreen.value = Screen.ACTIVE_CHAT
            } else {
                _currentScreen.value = Screen.CORE_DASHBOARD
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Delete primary user logs & tables logic or clear currentUser
            _currentUser.value = null
            _currentScreen.value = Screen.SIGN_IN
        }
    }
}
