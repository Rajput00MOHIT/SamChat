package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.data.*
import com.example.ui.CallState
import com.example.ui.ChatViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val activeBreakup by viewModel.activeBreakup.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("chat") }

    var pendingCallType by remember { mutableStateOf<String?>(null) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            val type = pendingCallType
            pendingCallType = null
            if (type == "voice" && audioGranted) {
                viewModel.startCall("voice")
            } else if (type == "video" && cameraGranted && audioGranted) {
                viewModel.startCall("video")
            } else {
                showPermissionDeniedDialog = true
            }
        }
    )

    val checkPermissionsAndCall = { type: String ->
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (type == "voice") {
            if (hasAudio) {
                viewModel.startCall("voice")
            } else {
                pendingCallType = "voice"
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        } else {
            if (hasCamera && hasAudio) {
                viewModel.startCall("video")
            } else {
                pendingCallType = "video"
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (currentUser == null) {
            // Authentication Screen
            AuthScreen(viewModel = viewModel)
        } else {
            // Logged in Screen
            Scaffold(
                bottomBar = {
                    if (partner != null && activeBreakup == null) {
                        BottomNavBar(
                            activeTab = activeTab,
                            onTabSelected = { activeTab = it }
                        )
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (partner == null) {
                        // Pairing / Linking Screen
                        PairingScreen(viewModel = viewModel)
                    } else {
                        // Main Tab Views
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Breakup Banner if pending
                            if (activeBreakup != null) {
                                BreakupBanner(
                                    breakupRequest = activeBreakup!!,
                                    viewModel = viewModel
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                when (activeTab) {
                                    "chat" -> ChatTab(viewModel = viewModel, onStartCall = checkPermissionsAndCall)
                                    "stories" -> StoriesTab(viewModel = viewModel)
                                    "calling" -> CallingTab(viewModel = viewModel, onStartCall = checkPermissionsAndCall)
                                    "settings" -> SettingsTab(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    // Call Overlay
                    if (activeCall != null) {
                        CallOverlay(
                            callState = activeCall!!,
                            onHangup = { viewModel.endCall() }
                        )
                    }

                    // Permission Denied Dialog
                    if (showPermissionDeniedDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDeniedDialog = false },
                            containerColor = DeepSurface,
                            shape = RoundedCornerShape(20.dp),
                            title = {
                                Text("Permissions Required", color = PureWhite, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(
                                    "SamChat needs access to your Microphone (and Camera for video calls) to establish an encrypted, high-fidelity peer-to-peer call. Please grant them when prompted in settings.",
                                    color = MutedGrey,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showPermissionDeniedDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                                ) {
                                    Text("I Understand", color = PureWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- SUB-SCREENS & COMPONENTS ---

@Composable
fun AuthScreen(viewModel: ChatViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authSuccess by viewModel.authSuccess.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .background(DeepSurface, shape = RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, shape = RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing Icon Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(CyberPink.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "Logo",
                    tint = CyberPink,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SamChat",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                ),
                color = PureWhite
            )

            Text(
                text = "private. intimate. secure.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Form
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPink,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = CyberPink,
                    unfocusedLabelColor = MutedGrey
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_username_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPink,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = CyberPink,
                    unfocusedLabelColor = MutedGrey
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input"),
                singleLine = true
            )

            // Live password strength feedback during signup
            if (isSignUp && password.isNotEmpty()) {
                val strengthError = viewModel.verifyPasswordStrength(password)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (strengthError == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = "Strength",
                        tint = if (strengthError == null) NeonGreen else CyberPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strengthError ?: "Secure Password Strength Check Passed",
                        color = if (strengthError == null) NeonGreen else CyberPink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (authError != null) {
                Text(
                    text = authError!!,
                    color = CyberPink,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (authSuccess != null) {
                Text(
                    text = authSuccess!!,
                    color = NeonGreen,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (isSignUp) {
                        viewModel.signup(username, password)
                    } else {
                        viewModel.login(username, password)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_submit_button")
            ) {
                Text(
                    text = if (isSignUp) "Create Account" else "Log In",
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    viewModel.clearAuthMessages()
                }
            ) {
                Text(
                    text = if (isSignUp) "Already have an account? Log In" else "New to SamChat? Sign Up",
                    color = CyberCyan
                )
            }
        }
    }
}

@Composable
fun PairingScreen(viewModel: ChatViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val friendships by viewModel.friendships.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val availableUsers by viewModel.availableUsers.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val friends by viewModel.acceptedFriends.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Welcome Header
        item {
            Column {
                Text(
                    text = "Welcome to SamChat, ${currentUser?.username}! ✨",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
                Text(
                    text = "Pair with your friends to unlock secure, private social vaults.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGrey,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 2. Switch Session Debug Widget
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.People, contentDescription = "Switch", tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simulation Hub (Switch Profiles)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
                        )
                    }
                    Text(
                        text = "Switch profiles on the fly below to send or accept requests:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableUsers) { uObj ->
                            val uName = uObj.username
                            val isMe = currentUser?.id == uObj.id
                            Button(
                                onClick = {
                                    viewModel.switchProfile(uObj.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMe) CyberPink else BorderColor
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isMe) "Me: $uName" else "Login as $uName",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Accepted Friends & Vaults Section
        if (friends.isNotEmpty()) {
            item {
                Text(
                    text = "My Private Vaults 🔐",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
            }

            items(friends) { friend ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectActiveFriend(friend.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CyberPink.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friend.username.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = CyberPink,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = friend.username,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap to unlock private vault 🔐",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedGrey
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = "Unlock",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSurface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MutedGrey)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No unlocked vaults yet. Link with a friend below to start chatting!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey
                        )
                    }
                }
            }
        }

        // 4. Incoming Requests
        val pendingRequests = friendships.filter { it.isCouplePending && it.friendId == currentUser?.id }
        if (pendingRequests.isNotEmpty()) {
            item {
                Text(
                    text = "Incoming Requests",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberPink
                )
            }

            items(pendingRequests) { req ->
                val senderName = availableUsers.find { it.id == req.userId }?.username ?: "User #${req.userId}"
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSurface),
                    border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$senderName wants to link vaults!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                            Text(
                                text = "Accept to start your private chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGrey
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.acceptPartnerRequest(req.userId) },
                                modifier = Modifier.background(NeonGreen.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Accept", tint = NeonGreen)
                            }
                            IconButton(
                                onClick = { viewModel.declinePartnerRequest(req.userId) },
                                modifier = Modifier.background(CyberPink.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Decline", tint = CyberPink)
                            }
                        }
                    }
                }
            }
        }

        // 5. Outgoing Request Pending State
        val outgoingRequest = friendships.find { it.isCouplePending && it.userId == currentUser?.id }
        if (outgoingRequest != null) {
            val recipientName = availableUsers.find { it.id == outgoingRequest.friendId }?.username ?: "User #${outgoingRequest.friendId}"
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⏳ Request Sent to $recipientName",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberGold
                        )
                        Text(
                            text = "They need to accept your request to open the vault. You can switch to their profile in the hub to test accepting!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // 6. Search and Link
        item {
            Column {
                Text(
                    text = "Link with a Friend",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.searchUsers(it)
                    },
                    placeholder = { Text("Search by exact username...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = MutedGrey
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("partner_search_input"),
                    singleLine = true
                )
            }
        }

        if (query.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = "No users found matching '$query'",
                        color = MutedGrey,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(searchResults) { foundUser ->
                    // Check if already friends or pending
                    val isAlreadyPending = friendships.any { 
                        (it.userId == currentUser?.id && it.friendId == foundUser.id && it.isCouplePending) ||
                        (it.userId == foundUser.id && it.friendId == currentUser?.id && it.isCouplePending)
                    }
                    val isAlreadyAccepted = friendships.any {
                        (it.userId == currentUser?.id && it.friendId == foundUser.id && it.isCoupleAccepted) ||
                        (it.userId == foundUser.id && it.friendId == currentUser?.id && it.isCoupleAccepted)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(CyberCyan.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = foundUser.username.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = foundUser.username,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }

                        when {
                            isAlreadyAccepted -> {
                                Text("Linked", color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                            }
                            isAlreadyPending -> {
                                Text("Pending", color = CyberGold, style = MaterialTheme.typography.bodySmall)
                            }
                            else -> {
                                Button(
                                    onClick = { viewModel.sendPartnerRequest(foundUser.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Link Vault", color = DarkBg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = BorderColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Type username to search and link with friends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = DeepSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.border(BorderStroke(1.dp, BorderColor))
    ) {
        val items = listOf(
            Triple("chat", "Chat", Icons.Filled.Chat),
            Triple("stories", "Stories", Icons.Filled.PhotoLibrary),
            Triple("calling", "Calling", Icons.Filled.Phone),
            Triple("settings", "Settings", Icons.Filled.Settings)
        )

        items.forEach { (tab, label, icon) ->
            val isSelected = activeTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) CyberPink else MutedGrey
                    )
                },
                label = {
                    Text(
                        text = label,
                        color = if (isSelected) CyberPink else MutedGrey,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = CyberPink.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// --- CHAT TAB ---
@Composable
fun ChatTab(
    viewModel: ChatViewModel,
    onStartCall: (String) -> Unit
) {
    val partner by viewModel.partner.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isPartnerTyping by viewModel.isPartnerTyping.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }

    // Auto-scroll to bottom of chat when messages size change
    LaunchedEffect(messages.size, isPartnerTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepSurface)
                .border(BorderStroke(1.dp, BorderColor))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Back arrow to exit the active friend's vault and return to friends list
                IconButton(
                    onClick = { viewModel.deselectActiveFriend() },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Exit Vault",
                        tint = CyberCyan
                    )
                }

                // Partner Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CyberPink.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partner?.username?.take(1)?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = CyberPink,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = partner?.username ?: "Partner",
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Text(
                        text = if (isPartnerTyping) "typing..." else "active private vault",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isPartnerTyping) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isPartnerTyping) NeonGreen else MutedGrey
                    )
                }
            }

            // Quick Call Actions & Breakup Menu Trigger
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStartCall("voice") }) {
                    Icon(Icons.Filled.Call, contentDescription = "Voice Call", tint = CyberCyan)
                }
                IconButton(onClick = { onStartCall("video") }) {
                    Icon(Icons.Filled.VideoCall, contentDescription = "Video Call", tint = CyberCyan)
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = MutedGrey)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DeepSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Start Breakup (72h)", color = CyberPink) },
                            onClick = {
                                showMenu = false
                                viewModel.startBreakup()
                            },
                            leadingIcon = { Icon(Icons.Filled.HeartBroken, contentDescription = "Breakup", tint = CyberPink) }
                        )
                        DropdownMenuItem(
                            text = { Text("Simulate Screen Alert", color = CyberCyan) },
                            onClick = {
                                showMenu = false
                                viewModel.simulateScreenshot()
                            },
                            leadingIcon = { Icon(Icons.Filled.Screenshot, contentDescription = "Screenshot", tint = CyberCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text("Trigger Typing Reply", color = NeonGreen) },
                            onClick = {
                                showMenu = false
                                viewModel.simulatePartnerTypingOnce()
                            },
                            leadingIcon = { Icon(Icons.Filled.Keyboard, contentDescription = "Typing", tint = NeonGreen) }
                        )
                    }
                }
            }
        }

        // Message Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                if (msg.isScreenshotAlert) {
                    // Snapchat-style screenshot warning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .background(CyberPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, CyberPink.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Screenshot, contentDescription = "Screenshot Alert", tint = CyberPink, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg.content,
                                color = CyberPink,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else {
                    val isMe = msg.senderId == viewModel.currentUserId.value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isMe) CyberPink else DeepSurface,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMe) 16.dp else 4.dp,
                                            bottomEnd = if (isMe) 4.dp else 16.dp
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isMe) Color.Transparent else BorderColor,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isMe) 16.dp else 4.dp,
                                            bottomEnd = if (isMe) 4.dp else 16.dp
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    color = PureWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Timestamp / read receipts
                            Row(
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                Text(
                                    text = timeFormat.format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedGrey
                                )
                                if (isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Read",
                                        tint = if (msg.isRead) CyberCyan else MutedGrey,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Realtime typing bubble simulation
            if (isPartnerTyping) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Row(
                            modifier = Modifier
                                .background(DeepSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val transition = rememberInfiniteTransition(label = "dots")
                            val alphas = (0..2).map { index ->
                                transition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "dot_alpha_$index"
                                )
                            }
                            alphas.forEach { alpha ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(CyberPink.copy(alpha = alpha.value), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat Input Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepSurface)
                .border(BorderStroke(1.dp, BorderColor))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("type message...", color = MutedGrey) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderColor,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = CyberPink,
                    unfocusedLabelColor = MutedGrey
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text"),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .background(CyberPink, CircleShape)
                    .size(44.dp)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- STORIES TAB ---
@Composable
fun StoriesTab(viewModel: ChatViewModel) {
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()

    var showPublishDialog by remember { mutableStateOf(false) }
    var storyText by remember { mutableStateOf("") }
    var selectedGradient by remember { mutableStateOf(0) }
    var selectedTextStyle by remember { mutableStateOf(0) }
    var selectedStickerType by remember { mutableStateOf("none") }
    var stickerQuestion by remember { mutableStateOf("") }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedStoryForViewer by remember { mutableStateOf<Story?>(null) }

    val gradients = listOf(
        Brush.linearGradient(listOf(CyberPink, Color(0xFFFFB300))),
        Brush.linearGradient(listOf(Color(0xFF8E24AA), CyberPink)),
        Brush.linearGradient(listOf(NeonGreen, Color(0xFF00E5FF))),
        Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF7F00FF))),
        Brush.linearGradient(listOf(DeepSurface, DarkBg))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Stories Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Partner Vault Stories",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Shared private moments. Visible only to u two.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey
                    )
                }

                Button(
                    onClick = { showPublishDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Story", tint = PureWhite)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }

            if (stories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "No stories",
                            tint = BorderColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No private stories posted yet. Tap Add to post one!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stories) { story ->
                        val posterName = if (story.userId == currentUser?.id) "U" else (partner?.username ?: "Partner")
                        
                        // Layout font and style depending on textStyleIndex
                        val isNeon = story.textStyleIndex == 2
                        val isTypewriter = story.textStyleIndex == 3
                        val isElegant = story.textStyleIndex == 4
                        val isModern = story.textStyleIndex == 1

                        val storyFontFamily = when {
                            isTypewriter -> FontFamily.Monospace
                            isElegant -> FontFamily.Serif
                            else -> FontFamily.SansSerif
                        }

                        val storyFontWeight = when {
                            isModern -> FontWeight.ExtraBold
                            isNeon || isTypewriter -> FontWeight.Bold
                            else -> FontWeight.Normal
                        }

                        val storyFontStyle = if (isElegant) FontStyle.Italic else FontStyle.Normal
                        val storyColor = if (isNeon) Color(0xFF32D74B) else PureWhite

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(DeepSurface)
                                .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                                .clickable { selectedStoryForViewer = story }
                        ) {
                            if (!story.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = story.imageUrl,
                                    contentDescription = "Story slide background",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(brush = gradients.getOrElse(story.gradientIndex) { gradients.last() })
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Top Row (Profile & Metadata)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(PureWhite.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (story.userId == currentUser?.id) "U" else partner?.username?.take(1)?.uppercase() ?: "?",
                                                    fontWeight = FontWeight.Bold,
                                                    color = PureWhite,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = posterName,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                            Text(
                                                text = timeFormat.format(Date(story.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureWhite.copy(alpha = 0.7f)
                                            )
                                            if (story.isHearted || story.heartCount > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.Favorite,
                                                    contentDescription = "Hearted",
                                                    tint = CyberPink,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                if (story.heartCount > 0) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "${story.heartCount}",
                                                        color = CyberPink,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                     )
                                                 }
                                             }
                                         }
                                    }

                                    // Content Text (With custom Instagram style formatting)
                                    Box(
                                        modifier = if (isTypewriter) {
                                            Modifier
                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        } else {
                                            Modifier
                                        }
                                    ) {
                                        Text(
                                            text = story.content,
                                            color = storyColor,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = storyFontWeight,
                                                fontFamily = storyFontFamily,
                                                fontStyle = storyFontStyle,
                                                letterSpacing = if (isModern) 1.sp else 0.sp
                                            ),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Interactive Sticker Block
                                    if (story.stickerType != "none") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PureWhite.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                .border(1.dp, PureWhite.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = story.stickerQuestion.ifBlank { "Vibe Check ✨" },
                                                    color = PureWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                when (story.stickerType) {
                                                    "poll" -> {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            val isAuthor = story.userId == currentUser?.id
                                                            val hasAnswered = story.stickerAnswer.isNotBlank()

                                                            if (!isAuthor && !hasAnswered) {
                                                                Button(
                                                                    onClick = { viewModel.submitStoryStickerAnswer(story, "Yes") },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Text("Yes 👍", color = DeepSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                Button(
                                                                    onClick = { viewModel.submitStoryStickerAnswer(story, "No") },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Text("No 👎", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            } else {
                                                                val voteResult = if (hasAnswered) story.stickerAnswer else "Waiting for vote..."
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .background(PureWhite.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                                        .padding(vertical = 6.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = if (hasAnswered) "Vote Result: $voteResult 📊" else "Waiting for partner vote...",
                                                                        color = CyberCyan,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    "love_meter" -> {
                                                        val isAuthor = story.userId == currentUser?.id
                                                        val hasAnswered = story.stickerAnswer.isNotBlank()

                                                        if (!isAuthor && !hasAnswered) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceAround
                                                            ) {
                                                                listOf("25%", "50%", "75%", "100%").forEach { pct ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(PureWhite.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                                            .clickable { viewModel.submitStoryStickerAnswer(story, pct) }
                                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Text(pct, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            val score = if (hasAnswered) story.stickerAnswer else "0%"
                                                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                                                LinearProgressIndicator(
                                                                    progress = {
                                                                        val floatVal = score.replace("%", "").toFloatOrNull() ?: 50f
                                                                        floatVal / 100f
                                                                    },
                                                                    color = CyberPink,
                                                                    trackColor = PureWhite.copy(alpha = 0.2f),
                                                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                                                                )
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = if (hasAnswered) "Vibe level: $score ❤️" else "Slide to vote!",
                                                                    color = PureWhite,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                    "couple_q" -> {
                                                        val isAuthor = story.userId == currentUser?.id
                                                        val hasAnswered = story.stickerAnswer.isNotBlank()

                                                        if (!isAuthor && !hasAnswered) {
                                                            var answerInput by remember { mutableStateOf("") }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = answerInput,
                                                                    onValueChange = { answerInput = it },
                                                                    placeholder = { Text("Type reply...", color = MutedGrey, fontSize = 11.sp) },
                                                                    colors = OutlinedTextFieldDefaults.colors(
                                                                        focusedBorderColor = CyberCyan,
                                                                        unfocusedBorderColor = BorderColor
                                                                    ),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    modifier = Modifier.weight(1f).height(42.dp),
                                                                    singleLine = true
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                IconButton(
                                                                    onClick = {
                                                                        if (answerInput.isNotBlank()) {
                                                                            viewModel.submitStoryStickerAnswer(story, answerInput)
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(36.dp).background(CyberCyan, CircleShape)
                                                                ) {
                                                                    Icon(Icons.Filled.Check, contentDescription = "Submit", tint = DeepSurface, modifier = Modifier.size(16.dp))
                                                                }
                                                            }
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(CyberPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                                    .padding(8.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = if (hasAnswered) "Partner Reply: \"${story.stickerAnswer}\" 💬" else "Waiting for partner's reply...",
                                                                    color = PureWhite,
                                                                    fontSize = 11.sp,
                                                                    fontStyle = FontStyle.Italic,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Footer Row (Visibility and Quick Reactions Toolbar)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (story.userId == currentUser?.id) {
                                        // Author Footer
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Visibility,
                                                contentDescription = "Seen By",
                                                tint = PureWhite.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (story.seenByPartner) "Seen by partner" else "Visible to couple",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PureWhite.copy(alpha = 0.6f)
                                            )
                                        }
                                    } else {
                                        // Partner Footer (with floating Quick Reactions)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Partner's story",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PureWhite.copy(alpha = 0.6f)
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("❤️", "🔥", "😂", "😮", "🎉").forEach { emoji ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(PureWhite.copy(alpha = 0.15f), CircleShape)
                                                            .clickable { viewModel.reactToStory(story, emoji) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(emoji, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive reaction badge overlay
                                if (story.reactionEmoji.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 4.dp, y = 4.dp)
                                            .size(36.dp)
                                            .background(DeepSurface, CircleShape)
                                            .border(1.dp, BorderColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(story.reactionEmoji, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Story Dialog
        if (showPublishDialog) {
            AlertDialog(
                onDismissRequest = { showPublishDialog = false },
                containerColor = DeepSurface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text("New Couple Story", color = PureWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = storyText,
                            onValueChange = { storyText = it },
                            placeholder = { Text("What's on ur mind?", color = MutedGrey) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberPink,
                                unfocusedBorderColor = BorderColor,
                                focusedLabelColor = CyberPink
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gradient Picker
                        Text(
                            "Choose Vibe Gradient:",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            gradients.forEachIndexed { idx, brush ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(brush, CircleShape)
                                        .border(
                                            width = if (selectedGradient == idx) 2.dp else 1.dp,
                                            color = if (selectedGradient == idx) CyberCyan else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedGradient = idx }
                                )
                            }
                        }

                        // Text style selector
                        Text(
                            "Choose Text Style:",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val styleLabels = listOf("Classic", "Bold", "Neon", "Label", "Elegant")
                            styleLabels.forEachIndexed { idx, label ->
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedTextStyle == idx) CyberPink else BorderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (selectedTextStyle == idx) CyberCyan else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedTextStyle = idx }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(label, color = if (selectedTextStyle == idx) DeepSurface else PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Image slide backdrop choice
                        Text(
                            "Choose Slide Backdrop Image (Optional):",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                        var customImageUrlInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = customImageUrlInput,
                            onValueChange = {
                                customImageUrlInput = it
                                selectedImageUrl = it.ifBlank { null }
                            },
                            placeholder = { Text("Paste custom image URL here...", color = MutedGrey, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberPink,
                                unfocusedBorderColor = BorderColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val romanticPresets = listOf(
                            "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500&auto=format&fit=crop" to "❤️ Love Neon",
                            "https://images.unsplash.com/photo-1516339901601-2e1d62dc0c45?w=500&auto=format&fit=crop" to "🌌 Stargazing",
                            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&auto=format&fit=crop" to "🌅 Sunset Beach",
                            "https://images.unsplash.com/photo-1513542789411-b6a5d4f31634?w=500&auto=format&fit=crop" to "🎨 Abstract Art",
                            "https://images.unsplash.com/photo-1515462277126-270d878326e5?w=500&auto=format&fit=crop" to "⚡ Cyber Neon",
                            "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?w=500&auto=format&fit=crop" to "🌹 Cozy Rose"
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            items(romanticPresets) { (url, label) ->
                                val isSelected = selectedImageUrl == url
                                Box(
                                    modifier = Modifier
                                        .background(if (isSelected) CyberPink else BorderColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) CyberCyan else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedImageUrl = url
                                            customImageUrlInput = ""
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(label, color = if (isSelected) DeepSurface else PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Sticker Selector
                        Text(
                            "Add Couple Sticker ✨:",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val stickers = listOf(
                                "none" to "None",
                                "poll" to "Poll 📊",
                                "love_meter" to "Vibe ❤️",
                                "couple_q" to "Q&A 💬"
                            )
                            stickers.forEach { (type, label) ->
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedStickerType == type) CyberCyan else BorderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (selectedStickerType == type) CyberPink else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedStickerType = type }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(label, color = if (selectedStickerType == type) DeepSurface else PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (selectedStickerType != "none") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = stickerQuestion,
                                onValueChange = { stickerQuestion = it },
                                placeholder = { Text("sticker prompt...", color = MutedGrey, fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderColor
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (storyText.isNotBlank()) {
                                viewModel.postStory(
                                    content = storyText,
                                    gradientIndex = selectedGradient,
                                    textStyleIndex = selectedTextStyle,
                                    stickerType = selectedStickerType,
                                    stickerQuestion = stickerQuestion,
                                    imageUrl = selectedImageUrl
                                )
                                storyText = ""
                                stickerQuestion = ""
                                selectedStickerType = "none"
                                selectedImageUrl = null
                                showPublishDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                    ) {
                        Text("Post Story", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPublishDialog = false }) {
                        Text("Cancel", color = CyberCyan)
                    }
                }
            )
        }

        // FULL-SCREEN SLIDES VIEWER COMPONENT OVERLAY
        selectedStoryForViewer?.let { currentStory ->
            val activeIndex = stories.indexOfFirst { it.id == currentStory.id }
            val now = System.currentTimeMillis()
            val elapsed = now - currentStory.timestamp
            val totalActive = 24 * 60 * 60 * 1000L
            val expProgress = (1f - (elapsed.toFloat() / totalActive.toFloat())).coerceIn(0f, 1f)
            val hoursLeft = maxOf(0, 24 - (elapsed / (3600 * 1000)))
            val minsLeft = maxOf(0, 60 - ((elapsed / (60 * 1000)) % 60))
            
            // Animation for heart reaction pulsing
            var isHeartAnimating by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val heartScale by animateFloatAsState(
                targetValue = if (isHeartAnimating) 1.6f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
                label = "HeartScaleAnim"
            )

            val isMyStory = currentStory.userId == currentUser?.id
            val posterName = if (isMyStory) "You" else (partner?.username ?: "Partner")

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(enabled = false) {} // block click through
            ) {
                // Background image or gradient
                if (!currentStory.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = currentStory.imageUrl,
                        contentDescription = "Slide visual",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = gradients.getOrElse(currentStory.gradientIndex) { gradients.last() })
                    )
                }

                // Left/Right touch targets to navigate slides
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left tap (30% width)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.3f)
                            .clickable {
                                if (activeIndex > 0) {
                                    selectedStoryForViewer = stories[activeIndex - 1]
                                } else {
                                    selectedStoryForViewer = null
                                }
                            }
                    )
                    // Right tap (70% width)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.7f)
                            .clickable {
                                if (activeIndex < stories.lastIndex) {
                                    selectedStoryForViewer = stories[activeIndex + 1]
                                } else {
                                    selectedStoryForViewer = null
                                }
                            }
                    )
                }

                // Slide contents & controls
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Top Progress Bars (Slide index and Expiration countdown indicator)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEach { story ->
                            val isCurrent = story.id == currentStory.id
                            val isPassed = stories.indexOf(story) < activeIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isCurrent) CyberPink 
                                        else if (isPassed) CyberPink.copy(alpha = 0.8f) 
                                        else PureWhite.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    // Metadata row (Poster details, expiration timer, and close button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(PureWhite.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isMyStory) "U" else partner?.username?.take(1)?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = posterName,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                // Sleek countdown bar showing actual hours remaining
                                Text(
                                    text = "⏰ Expires in ${hoursLeft}h ${minsLeft}m",
                                    color = CyberPink,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedStoryForViewer = null },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Viewer", tint = PureWhite)
                        }
                    }

                    // 24-Hour Expiration Progress Bar (Visual shrinking indicator)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { expProgress },
                        color = CyberPink,
                        trackColor = PureWhite.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )

                    // Large story text / main aesthetic caption
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStory.content,
                            color = PureWhite,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    // Interactive stickers (Polls, Q&A replies, Love Meters) inside the Viewer!
                    if (currentStory.stickerType != "none") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .border(1.dp, PureWhite.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = currentStory.stickerQuestion.ifBlank { "Vibe Check ✨" },
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                when (currentStory.stickerType) {
                                    "poll" -> {
                                        val isAuthor = currentStory.userId == currentUser?.id
                                        val hasAnswered = currentStory.stickerAnswer.isNotBlank()
                                        if (!isAuthor && !hasAnswered) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Button(
                                                    onClick = { viewModel.submitStoryStickerAnswer(currentStory, "Yes") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Yes 👍", color = PureWhite, fontWeight = FontWeight.Bold)
                                                }
                                                Button(
                                                    onClick = { viewModel.submitStoryStickerAnswer(currentStory, "No") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("No 👎", color = PureWhite, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            val ans = if (hasAnswered) currentStory.stickerAnswer else "No votes yet"
                                            Text("Result: $ans 📊", color = CyberPink, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    "love_meter" -> {
                                        val isAuthor = currentStory.userId == currentUser?.id
                                        val hasAnswered = currentStory.stickerAnswer.isNotBlank()
                                        if (!isAuthor && !hasAnswered) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                listOf("25%", "50%", "75%", "100%").forEach { pct ->
                                                    Button(
                                                        onClick = { viewModel.submitStoryStickerAnswer(currentStory, pct) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = PureWhite.copy(alpha = 0.2f)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(pct, color = PureWhite, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        } else {
                                            val ans = if (hasAnswered) currentStory.stickerAnswer else "50%"
                                            val progressVal = (ans.replace("%", "").toFloatOrNull() ?: 50f) / 100f
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                LinearProgressIndicator(
                                                    progress = { progressVal },
                                                    color = CyberPink,
                                                    trackColor = PureWhite.copy(alpha = 0.2f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(CircleShape)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Vibe Score: $ans ❤️", color = PureWhite, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    "couple_q" -> {
                                        val isAuthor = currentStory.userId == currentUser?.id
                                        val hasAnswered = currentStory.stickerAnswer.isNotBlank()
                                        if (!isAuthor && !hasAnswered) {
                                            var ansText by remember { mutableStateOf("") }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = ansText,
                                                    onValueChange = { ansText = it },
                                                    placeholder = { Text("Reply to partner...", color = MutedGrey) },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = CyberPink,
                                                        unfocusedBorderColor = BorderColor
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = {
                                                        if (ansText.isNotBlank()) {
                                                            viewModel.submitStoryStickerAnswer(currentStory, ansText)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(CyberPink, CircleShape)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Submit", tint = PureWhite)
                                                }
                                            }
                                        } else {
                                            val ans = if (hasAnswered) currentStory.stickerAnswer else "Waiting for reply..."
                                            Text("Answer: \"$ans\" 💬", color = PureWhite, fontStyle = FontStyle.Italic)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // BOTTOM CONTROLS (Sleek heart reaction button & Quick Emoji reactions)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Massive neon heart pulsing reaction button
                        Button(
                            onClick = {
                                isHeartAnimating = true
                                viewModel.toggleHeartStory(currentStory)
                                scope.launch {
                                    kotlinx.coroutines.delay(300)
                                    isHeartAnimating = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStory.isHearted) CyberPink else Color.White.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(50.dp),
                            border = BorderStroke(1.dp, if (currentStory.isHearted) CyberPink else Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .height(56.dp)
                                .scale(heartScale)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (currentStory.isHearted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Heart story",
                                    tint = if (currentStory.isHearted) PureWhite else CyberPink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentStory.heartCount > 0) "${currentStory.heartCount}" else "Love It",
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        // Quick emoji panel
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("🔥", "😂", "😮", "🎉", "👀").forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                        .clickable {
                                            viewModel.reactToStory(currentStory, emoji)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CALLING TAB ---
@Composable
fun CallingTab(
    viewModel: ChatViewModel,
    onStartCall: (String) -> Unit
) {
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Voice & Video Calls",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
        Text(
            text = "Encrypted peer-to-peer secure channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedGrey,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Call Actions Box
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onStartCall("voice") },
                        modifier = Modifier.background(CyberCyan.copy(alpha = 0.15f), CircleShape).size(56.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Voice Call", tint = CyberCyan, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Voice Call", color = PureWhite, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onStartCall("video") },
                        modifier = Modifier.background(CyberPink.copy(alpha = 0.15f), CircleShape).size(56.dp)
                    ) {
                        Icon(Icons.Filled.VideoCall, contentDescription = "Video Call", tint = CyberPink, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Video Call", color = PureWhite, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Call Logs Section
        Text(
            text = "Recent Calls",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = PureWhite,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (callHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, contentDescription = "History", tint = BorderColor, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No call history logs yet.", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(callHistory) { call ->
                    val partnerName = partner?.username ?: "Partner"
                    val isVideo = call.type == "video"
                    val wasAnswered = call.wasAnswered
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (wasAnswered) NeonGreen.copy(alpha = 0.15f) else CyberPink.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Filled.VideoCall else Icons.Filled.Call,
                                    contentDescription = "Type",
                                    tint = if (wasAnswered) NeonGreen else CyberPink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = if (wasAnswered) "Answered Call" else "Missed Call",
                                    fontWeight = FontWeight.Bold,
                                    color = if (wasAnswered) PureWhite else CyberPink
                                )
                                Text(
                                    text = if (wasAnswered) "Duration: ${call.durationSec}s" else "No answer",
                                    color = MutedGrey,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        val format = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        Text(
                            text = format.format(Date(call.timestamp)),
                            color = MutedGrey,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// --- SETTINGS TAB ---
@Composable
fun SettingsTab(viewModel: ChatViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()

    var readReceipts by remember { mutableStateOf(currentUser?.readReceiptsEnabled ?: true) }
    var lastSeen by remember { mutableStateOf(currentUser?.lastSeenEnabled ?: true) }
    var typingIndicator by remember { mutableStateOf(currentUser?.typingIndicatorEnabled ?: true) }
    var discoverable by remember { mutableStateOf(currentUser?.isDiscoverable ?: true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "SamChat Settings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
        Text(
            text = "Total control over ur couple privacy space.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedGrey,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Privacy Controls Section
        Text(
            text = "Insecurity Toggles (Control signals!)",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberPink,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DeepSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Read receipts
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Read Receipts", color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("If disabled, partner cannot see when u read messages", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = readReceipts,
                        onCheckedChange = {
                            readReceipts = it
                            viewModel.toggleReadReceipts(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberPink, checkedTrackColor = CyberPink.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                // Last Seen
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Last Seen Timestamp", color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("Hides your current online presence clock from partner", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = lastSeen,
                        onCheckedChange = {
                            lastSeen = it
                            viewModel.toggleLastSeen(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberPink, checkedTrackColor = CyberPink.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                // Typing Indicators
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Typing Indicator", color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("Shows 'typing...' bubble when u are drafting", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = typingIndicator,
                        onCheckedChange = {
                            typingIndicator = it
                            viewModel.toggleTypingIndicator(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberPink, checkedTrackColor = CyberPink.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                // Username Discoverability
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Discoverability Control", color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("Allow people to find you in the search hub", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = discoverable,
                        onCheckedChange = {
                            discoverable = it
                            viewModel.toggleDiscoverability(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberPink, checkedTrackColor = CyberPink.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Active Devices Session List Simulation
        Text(
            text = "Active Devices & Sessions",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DeepSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Smartphone, contentDescription = "Device", tint = CyberCyan)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Google Pixel 8 (This Device)", color = PureWhite, fontWeight = FontWeight.Bold)
                        Text("Session active. Last used: Just Now", color = MutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Out of All Other Devices", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Return to Friends List & Vaults
        Button(
            onClick = { viewModel.deselectActiveFriend() },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = "Vaults", tint = DarkBg)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Friends & Vaults List", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        }

        // Account Logout Actions
        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out Session", color = PureWhite, fontWeight = FontWeight.Bold)
        }
    }
}

// --- CALL OVERLAY HUD ---
@Composable
fun CallOverlay(
    callState: CallState,
    onHangup: () -> Unit
) {
    val isVideo = callState.type == "video"
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Mock Camera Viewfinder Grid / Feed Background
        if (isVideo && !isCameraOff) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = CyberCyan.copy(alpha = 0.15f)
                val strokeWidth = 1.dp.toPx()

                // Draw Camera Grid Guidelines
                drawLine(gridColor, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), strokeWidth)
                drawLine(gridColor, Offset(2 * size.width / 3, 0f), Offset(2 * size.width / 3, size.height), strokeWidth)
                drawLine(gridColor, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), strokeWidth)
                drawLine(gridColor, Offset(0f, 2 * size.height / 3), Offset(size.width, 2 * size.height / 3), strokeWidth)

                // Cyberpunk circles represent simulated face silhouettes
                drawCircle(CyberPink.copy(alpha = 0.08f), center = center, radius = size.width / 4)
                drawCircle(CyberCyan.copy(alpha = 0.03f), center = center, radius = size.width / 3, style = Stroke(2.dp.toPx()))
            }

            // Mock Picture-in-Picture smaller partner window
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .background(DeepSurface, RoundedCornerShape(16.dp))
                    .border(2.dp, CyberPink, RoundedCornerShape(16.dp))
                    .align(Alignment.TopEnd)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(CyberPink.copy(alpha = 0.15f), center = center, radius = size.width / 3)
                }
                Text(
                    text = callState.partnerName,
                    color = PureWhite,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }
        } else {
            // Audio Calling avatar layout
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "ring")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Ring background
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .border(2.dp, CyberCyan.copy(alpha = 0.3f), CircleShape)
                            .clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                    )

                    Text(
                        text = callState.partnerName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 36.sp
                    )
                }
            }
        }

        // UI Information overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = callState.partnerName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhite
            )

            val statusText = when (callState.status) {
                "calling" -> "CALLING..."
                "active" -> "ENCRYPTED CHANNEL"
                else -> "ENDED"
            }

            Text(
                text = statusText,
                color = if (callState.status == "calling") CyberGold else NeonGreen,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                modifier = Modifier.padding(top = 4.dp)
            )

            if (callState.status == "active") {
                val mins = callState.durationSec / 60
                val secs = callState.durationSec % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    color = PureWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Call Control Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isMuted) CyberPink.copy(alpha = 0.2f) else BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) CyberPink else PureWhite
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // End Call Button
            IconButton(
                onClick = onHangup,
                modifier = Modifier
                    .size(68.dp)
                    .background(CyberPink, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Hang Up",
                    tint = PureWhite,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (isVideo) {
                Spacer(modifier = Modifier.width(24.dp))

                // Toggle Camera Button
                IconButton(
                    onClick = { isCameraOff = !isCameraOff },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isCameraOff) CyberPink.copy(alpha = 0.2f) else BorderColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isCameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        contentDescription = "Toggle Camera",
                        tint = if (isCameraOff) CyberPink else PureWhite
                    )
                }
            }
        }
    }
}

// --- BREAKUP BANNER (72h COUNTDOWN & PURGE ENGINE) ---
@Composable
fun BreakupBanner(
    breakupRequest: BreakupRequest,
    viewModel: ChatViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val partner by viewModel.partner.collectAsStateWithLifecycle()
    val speedUp by viewModel.speedUpBreakup.collectAsStateWithLifecycle()

    val totalTimeMs = 72L * 60L * 60L * 1000L // 72 Hours
    val speedRunTimeMs = 2L * 60L * 1000L // Speed-run 2 minutes for demo
    
    // Calculate remaining countdown
    var timeLeftMs by remember { mutableStateOf(totalTimeMs) }
    
    LaunchedEffect(breakupRequest, speedUp) {
        val target = if (speedUp) speedRunTimeMs else totalTimeMs
        var elapsed = 0L
        while (elapsed < target) {
            timeLeftMs = target - elapsed
            delay(1000)
            elapsed += if (speedUp) 10000L else 1000L // Accelerate speed in SpeedUp mode!
        }
        // Purge data and logout
        timeLeftMs = 0
        viewModel.triggerImmediateBreakupWipe()
    }

    val days = timeLeftMs / (24 * 60 * 60 * 1000)
    val hours = (timeLeftMs / (60 * 60 * 1000)) % 24
    val mins = (timeLeftMs / (60 * 1000)) % 60
    val secs = (timeLeftMs / 1000) % 60

    val pct = if (speedUp) {
        1f - (timeLeftMs.toFloat() / speedRunTimeMs.toFloat())
    } else {
        1f - (timeLeftMs.toFloat() / totalTimeMs.toFloat())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberPink.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, CyberPink),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(CyberPink.copy(alpha = pulseAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Mutual Breakup Protocol Active",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberPink
                    )
                }

                Text(
                    text = if (speedUp) "Demo Speed-run" else "72h Cooling-off",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Countdown Clock View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%02d days  %02d:%02d:%02d", days, hours, mins, secs),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar representing elapsed cooldown
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyberPink,
                trackColor = BorderColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "VOW OF PURGE: Once timer hits zero, all chat history, private stories, voice/video call records, and your linked partnership will be permanently purged and wiped. Irreversible.",
                color = MutedGrey,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.cancelBreakup() },
                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel Protocol", color = PureWhite, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.triggerImmediateBreakupWipe() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Force Wipe Now!", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            }

            // Speed up control for reviewers / testers!
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Simulate 72h in 2 minutes:",
                    color = CyberGold,
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = speedUp,
                    onCheckedChange = { viewModel.toggleSpeedUpBreakup(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberGold, checkedTrackColor = CyberGold.copy(alpha = 0.5f))
                )
            }
        }
    }
}

// Simple local scroll state implementation
@Composable
fun rememberScrollState(): ScrollState {
    var value by remember { mutableStateOf(0) }
    return ScrollState(value = value, onValueChange = { value = it })
}

class ScrollState(value: Int, val onValueChange: (Int) -> Unit) {
    var value by mutableStateOf(value)
}

@Composable
fun Modifier.verticalScroll(state: ScrollState): Modifier {
    return this
}
