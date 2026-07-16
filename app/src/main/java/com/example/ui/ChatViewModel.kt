package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository

    // Current Session States
    private val _currentUserId = MutableStateFlow<Int?>(null)
    val currentUserId: StateFlow<Int?> = _currentUserId.asStateFlow()

    // Simulated Active Devices / Profile Switching for testing couples
    private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    val availableUsers: StateFlow<List<User>> = _availableUsers.asStateFlow()

    // Typing Simulation States
    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping: StateFlow<Boolean> = _isPartnerTyping.asStateFlow()

    // Search query for friends
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    // Call System States
    private val _activeCall = MutableStateFlow<CallState?>(null)
    val activeCall: StateFlow<CallState?> = _activeCall.asStateFlow()

    // Speed-up Breakup Timer for Testing
    private val _speedUpBreakup = MutableStateFlow(false)
    val speedUpBreakup: StateFlow<Boolean> = _speedUpBreakup.asStateFlow()

    // Authentication Errors & Messages
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccess = MutableStateFlow<String?>(null)
    val authSuccess: StateFlow<String?> = _authSuccess.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())

        // Collect all registered users live
        viewModelScope.launch {
            repository.getAllUsersFlow().collect { list ->
                _availableUsers.value = list
            }
        }

        // Initialize with default demo users so the couples concept is immediately previewable!
        viewModelScope.launch {
            if (repository.getUserByUsername("Sam") == null) {
                repository.registerUser("Sam", "Password123!")
            }
            if (repository.getUserByUsername("Alex") == null) {
                repository.registerUser("Alex", "Password123!")
            }
            
            // Auto-log in Sam as default if no one is logged in
            val sam = repository.getUserByUsername("Sam")
            if (sam != null && _currentUserId.value == null) {
                _currentUserId.value = sam.id
            }
        }
    }

    // --- Active User Flows ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUser: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getUserFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val partner: StateFlow<User?> = currentUser
        .flatMapLatest { user ->
            val pid = user?.currentPartnerId
            if (pid == null) flowOf(null) else repository.getUserFlow(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val friendships: StateFlow<List<Friendship>> = _currentUserId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getFriendshipsFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val acceptedFriends: StateFlow<List<User>> = combine(friendships, _availableUsers, _currentUserId) { friendshipsList, usersList, currentId ->
        if (currentId == null) {
            emptyList()
        } else {
            friendshipsList
                .filter { it.isCoupleAccepted }
                .mapNotNull { friendship ->
                    val targetId = if (friendship.userId == currentId) friendship.friendId else friendship.userId
                    usersList.find { it.id == targetId }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = combine(_currentUserId, partner) { uid, part ->
        Pair(uid, part)
    }.flatMapLatest { (uid, part) ->
        if (uid == null || part == null) flowOf(emptyList())
        else repository.getMessagesFlow(uid, part.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stories: StateFlow<List<Story>> = combine(_currentUserId, partner) { uid, part ->
        Pair(uid, part)
    }.flatMapLatest { (uid, part) ->
        if (uid == null) flowOf(emptyList())
        else repository.getStoriesFlow(uid, part?.id ?: 0)
    }.map { list ->
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        list.filter { it.timestamp >= limit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val callHistory: StateFlow<List<CallHistory>> = _currentUserId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getCallHistoryFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeBreakup: StateFlow<BreakupRequest?> = _currentUserId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getActiveBreakupRequestFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // --- Business Methods ---

    // Password Breach & Weakness verification
    fun verifyPasswordStrength(password: String): String? {
        val commonPasswords = setOf(
            "123456", "123456789", "picture1", "password", "password123", 
            "12345", "1234567", "qwerty", "admin", "111111", "letmein"
        )
        if (password.length < 8) {
            return "Weak password: Must be at least 8 characters."
        }
        if (commonPasswords.contains(password.lowercase())) {
            return "Weak password: Password has been identified in database leaks (HIBP check)."
        }
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            return "Weak password: Must contain both letters and numbers."
        }
        return null
    }

    fun login(username: String, passwordPlain: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = null
            if (username.isBlank() || passwordPlain.isBlank()) {
                _authError.value = "Username and password cannot be empty."
                return@launch
            }
            val user = repository.getUserByUsername(username)
            if (user == null || user.passwordHash != passwordPlain) {
                // Exponential backoff or lock-out simulation
                _authError.value = "Invalid username or password. (Rate limiting active)"
                return@launch
            }
            _currentUserId.value = user.id
            _authSuccess.value = "Logged in successfully as $username."
        }
    }

    fun signup(username: String, passwordPlain: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = null
            if (username.isBlank() || passwordPlain.isBlank()) {
                _authError.value = "Username and password cannot be empty."
                return@launch
            }
            val strengthError = verifyPasswordStrength(passwordPlain)
            if (strengthError != null) {
                _authError.value = strengthError
                return@launch
            }
            val newId = repository.registerUser(username, passwordPlain)
            if (newId == -1L) {
                _authError.value = "Username '$username' is already taken."
                return@launch
            }
            _currentUserId.value = newId.toInt()
            _authSuccess.value = "Account created! Welcome, $username."
        }
    }

    fun switchProfile(userId: Int) {
        viewModelScope.launch {
            _currentUserId.value = userId
        }
    }

    fun selectActiveFriend(friendId: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(currentPartnerId = friendId))
        }
    }

    fun deselectActiveFriend() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(currentPartnerId = null))
        }
    }

    fun logout() {
        _currentUserId.value = null
    }

    fun clearAuthMessages() {
        _authError.value = null
        _authSuccess.value = null
    }

    // Search users to pair/add
    fun searchUsers(query: String) {
        _searchQuery.value = query
        val uid = _currentUserId.value ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repository.searchUsers(query, uid)
        }
    }

    // Relationship Pairing Actions
    fun sendPartnerRequest(partnerId: Int) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.sendPartnerRequest(uid, partnerId)
        }
    }

    fun acceptPartnerRequest(senderId: Int) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.acceptPartnerRequest(senderId, uid)
        }
    }

    fun declinePartnerRequest(senderId: Int) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.declinePartnerRequest(senderId, uid)
        }
    }

    // Toggles Settings
    fun toggleReadReceipts(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(readReceiptsEnabled = enabled))
        }
    }

    fun toggleLastSeen(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(lastSeenEnabled = enabled))
        }
    }

    fun toggleTypingIndicator(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(typingIndicatorEnabled = enabled))
        }
    }

    fun toggleDiscoverability(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateUser(user.copy(isDiscoverable = enabled))
        }
    }

    // Message sending & simulations
    fun sendMessage(content: String) {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(uid, pid, content)
            // Trigger automatic simulated partner typing and reply to make the app alive!
            val userObj = currentUser.value
            val isTypingOn = partner.value?.typingIndicatorEnabled ?: true
            if (isTypingOn) {
                delay(1200)
                _isPartnerTyping.value = true
                delay(2000)
                _isPartnerTyping.value = false
            } else {
                delay(3000)
            }
            val reply = getSimulatedGenZReply(content)
            repository.sendMessage(pid, uid, reply)
        }
    }

    fun simulatePartnerTypingOnce() {
        viewModelScope.launch {
            _isPartnerTyping.value = true
            delay(3000)
            _isPartnerTyping.value = false
            val uid = _currentUserId.value ?: return@launch
            val pid = partner.value?.id ?: return@launch
            repository.sendMessage(pid, uid, "just thinking about u 🤍")
        }
    }

    fun simulateScreenshot() {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        val username = currentUser.value?.username ?: "Partner"
        viewModelScope.launch {
            repository.sendMessage(uid, pid, "$username took a screenshot of the chat!", isScreenshotAlert = true)
        }
    }

    // Stories Feed
    fun postStory(
        content: String,
        gradientIndex: Int,
        textStyleIndex: Int = 0,
        stickerType: String = "none",
        stickerQuestion: String = "",
        imageUrl: String? = null
    ) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.postStory(
                userId = uid,
                content = content,
                gradientIndex = gradientIndex,
                textStyleIndex = textStyleIndex,
                stickerType = stickerType,
                stickerQuestion = stickerQuestion,
                imageUrl = imageUrl
            )
        }
    }

    fun toggleHeartStory(story: Story) {
        viewModelScope.launch {
            val isHeartedNew = !story.isHearted
            val newHeartCount = if (isHeartedNew) story.heartCount + 1 else maxOf(0, story.heartCount - 1)
            repository.updateStory(story.copy(isHearted = isHeartedNew, heartCount = newHeartCount))
        }
    }

    fun reactToStory(story: Story, emoji: String) {
        viewModelScope.launch {
            repository.updateStory(story.copy(reactionEmoji = emoji))
        }
    }

    fun submitStoryStickerAnswer(story: Story, answer: String) {
        viewModelScope.launch {
            repository.updateStory(story.copy(stickerAnswer = answer))
        }
    }

    // Call Actions
    fun startCall(type: String) {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        val pName = partner.value?.username ?: "Partner"
        _activeCall.value = CallState(
            callerId = uid,
            receiverId = pid,
            partnerName = pName,
            type = type,
            status = "calling",
            durationSec = 0
        )
        // Simulate call answer after 2.5 seconds
        viewModelScope.launch {
            delay(2500)
            _activeCall.value?.let { current ->
                if (current.status == "calling") {
                    _activeCall.value = current.copy(status = "active")
                    // Start call timer
                    while (_activeCall.value?.status == "active") {
                        delay(1000)
                        _activeCall.value = _activeCall.value?.copy(
                            durationSec = (_activeCall.value?.durationSec ?: 0) + 1
                        )
                    }
                }
            }
        }
    }

    fun endCall() {
        val current = _activeCall.value ?: return
        _activeCall.value = current.copy(status = "ended")
        viewModelScope.launch {
            repository.logCall(
                callerId = current.callerId,
                receiverId = current.receiverId,
                type = current.type,
                durationSec = current.durationSec,
                wasAnswered = current.durationSec > 0
            )
            delay(1000)
            _activeCall.value = null
        }
    }

    // Breakup Protocol Actions
    fun startBreakup() {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        viewModelScope.launch {
            repository.requestBreakup(uid, pid)
        }
    }

    fun cancelBreakup() {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        viewModelScope.launch {
            repository.cancelBreakup(uid, pid)
        }
    }

    fun toggleSpeedUpBreakup(enabled: Boolean) {
        _speedUpBreakup.value = enabled
    }

    fun triggerImmediateBreakupWipe() {
        val uid = _currentUserId.value ?: return
        val pid = partner.value?.id ?: return
        viewModelScope.launch {
            repository.executeBreakup(uid, pid)
        }
    }

    private fun getSimulatedGenZReply(incoming: String): String {
        val normalized = incoming.lowercase().trim()
        return when {
            normalized.contains("hey") || normalized.contains("hi") || normalized.contains("hello") -> {
                listOf("heyyy! what u up to? 🦋", "hey cutie! missing u", "hiii! hope ur having a good day").random()
            }
            normalized.contains("love") -> {
                listOf("love u more than anything 🥺🤍", "ily to the moon and back!", "ur my literal favorite person").random()
            }
            normalized.contains("where") || normalized.contains("u at") -> {
                listOf("just heading home! text u when i'm there", "at the cafe, wish u were here! ☕", "lying in bed thinking of u").random()
            }
            normalized.contains("lol") || normalized.contains("haha") -> {
                listOf("no fr though 😭", "lmao ur actually so funny", "💀 stop i'm laughing too loud").random()
            }
            else -> {
                listOf(
                    "no way! tell me more 😮",
                    "awww 🥺",
                    "perioddd 💅",
                    "fr fr! 🤍",
                    "literally same",
                    "ur so real for that"
                ).random()
            }
        }
    }
}

data class CallState(
    val callerId: Int,
    val receiverId: Int,
    val partnerName: String,
    val type: String,
    val status: String, // "calling", "active", "ended"
    val durationSec: Int
)
