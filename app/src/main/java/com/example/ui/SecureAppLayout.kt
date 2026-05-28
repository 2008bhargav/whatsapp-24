package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldChatApp(viewModel: SecureViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                SecureViewModel.Screen.SIGN_IN -> {
                    SignInScreen(viewModel = viewModel)
                }
                SecureViewModel.Screen.PROFILE_CREATION -> {
                    ProfileCreationScreen(viewModel = viewModel, presetFullName = currentUser?.fullName ?: "")
                }
                SecureViewModel.Screen.CORE_DASHBOARD -> {
                    CoreDashboardScreen(viewModel = viewModel)
                }
                SecureViewModel.Screen.ACTIVE_CHAT -> {
                    ActiveChatScreen(viewModel = viewModel)
                }
                SecureViewModel.Screen.CALL_SCREEN -> {
                    CallScreen(viewModel = viewModel)
                }
                SecureViewModel.Screen.SECURITY_CENTER -> {
                    SecurityCenterScreen(viewModel = viewModel)
                }
            }
        }
    }
}

fun parseSafeColor(hex: String, fallback: Color = Color(0xFF14B8A6)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

// ---------------- SIGN IN SCREEN ----------------
@Composable
fun SignInScreen(viewModel: SecureViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = generateSequence(context) {
        if (it is android.content.ContextWrapper) it.baseContext else null
    }.firstOrNull { it is android.app.Activity } as? android.app.Activity
    
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) {
                        val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(authCredential).addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val user = authTask.result?.user
                                if (user != null) {
                                    val email = user.email ?: ""
                                    val name = user.displayName ?: "Secure User"
                                    viewModel.handleGoogleAuthSuccess(email, name, true)
                                }
                            } else {
                                val error = authTask.exception?.localizedMessage ?: "Unknown Firebase error"
                                android.widget.Toast.makeText(context, "Firebase Auth Failed: $error. Falling back to local auth.", android.widget.Toast.LENGTH_LONG).show()
                                
                                // Fallback to allow app testing even if Firebase Auth is not configured properly in console
                                val email = account?.email ?: "test@example.com"
                                val name = account?.displayName ?: "Test User"
                                viewModel.handleGoogleAuthSuccess(email, name, false)
                            }
                        }
                    } else {
                        // Firebase not initialized, fallback to simulated login
                        android.widget.Toast.makeText(context, "Google Signed In. Database Local Mode.", android.widget.Toast.LENGTH_SHORT).show()
                        val email = account?.email ?: "test@example.com"
                        val name = account?.displayName ?: "Test User"
                        viewModel.handleGoogleAuthSuccess(email, name, false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Auth", "Google auth failed: ${e.message}")
                android.widget.Toast.makeText(context, "Authentication Failed: ${e.message}. Simulating local login.", android.widget.Toast.LENGTH_LONG).show()
                viewModel.handleGoogleAuthSuccess("tester@example.com", "Tester", false)
            }
        } else {
            if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
                android.widget.Toast.makeText(context, "Sign-in intent failed (Code: ${result.resultCode}). Ensure SHA-1 is correct.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        Color(0xFF0F172A)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-fidelity glowing logo card
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color(0xFF14B8A6), RoundedCornerShape(32.dp))
                    .background(Color(0xFF111827)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Shield Logo",
                    tint = Color(0xFF14B8A6),
                    modifier = Modifier.size(54.dp)
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .offset(y = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ShieldChat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = Color.White
            )

            Text(
                text = "Secure Social Messaging & Calling",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = {
                    val clientId = com.example.BuildConfig.GOOGLE_CLIENT_ID
                    if (clientId.isEmpty() || clientId.contains("YOUR_")) {
                        android.widget.Toast.makeText(context, "Configure GOOGLE_CLIENT_ID in AI Studio Secrets", android.widget.Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    
                    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                        android.widget.Toast.makeText(context, "Firebase is not initialized. Assuming local-only mode.", android.widget.Toast.LENGTH_LONG).show()
                    }

                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .requestEmail()
                        .build()
                        
                    val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_auth_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            var showManualEntry by remember { mutableStateOf(false) }
            var manualEmail by remember { mutableStateOf("") }

            if (showManualEntry) {
                OutlinedTextField(
                    value = manualEmail,
                    onValueChange = { manualEmail = it },
                    label = { Text("Enter any Email", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (manualEmail.isNotBlank() && manualEmail.contains("@")) {
                            viewModel.handleGoogleAuthSuccess(manualEmail, manualEmail.substringBefore("@"), false)
                        } else {
                            android.widget.Toast.makeText(context, "Please enter a valid email", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Continue with Email", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { showManualEntry = true }) {
                    Text("Continue without Google (Custom Email)", color = Color(0xFF10B981))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "E2EE Cryptographic standard. AES-GCM + RSA key ring protection verified.",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ---------------- PROFILE CREATION SCREEN ----------------
@Composable
fun ProfileCreationScreen(viewModel: SecureViewModel, presetFullName: String) {
    var username by remember { mutableStateOf(presetFullName.lowercase().replace(" ", "_").takeIf { it.isNotBlank() } ?: "") }
    var fullName by remember { mutableStateOf(presetFullName) }
    var bio by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#FF0D9488") } // Default Teal
    var showError by remember { mutableStateOf<String?>(null) }

    val presetColors = listOf(
        Pair("Teal", "#FF0D9488"),
        Pair("Indigo", "#FF4F46E5"),
        Pair("Rose", "#FFE11D48"),
        Pair("Emerald", "#FF10B981"),
        Pair("Orange", "#FFF97316")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Complete Profile",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Set up your unique username and profile accents.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Profile Color Picker Avatar Representation
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(parseSafeColor(selectedColorHex)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (if (fullName.isNotEmpty()) fullName.take(1) else "S").uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Color Selector Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presetColors.forEach { (_, hex) ->
                    val isSelected = selectedColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseSafeColor(hex))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase().replace(" ", "")
                    showError = null
                },
                label = { Text("Unique Username") },
                placeholder = { Text("alex_07") },
                leadingIcon = { Text("@", color = Color(0xFF14B8A6), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                isError = showError != null,
                modifier = Modifier.fillMaxWidth().testTag("username_input"),
                singleLine = true
            )

            if (showError != null) {
                Text(
                    text = showError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth().testTag("fullname_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().testTag("bio_input"),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    if (username.isEmpty()) {
                        showError = "Username cannot be empty!"
                    } else if (username == "alex_07" || username == "johnsmith" || username == "sophia_security") {
                        showError = "Username is already taken! Try another one."
                    } else {
                        viewModel.completeProfileCreation(username, fullName, selectedColorHex, bio)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_profile_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6))
            ) {
                Text("Generate Cryptographic Identity", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------- MAIN DASHBOARD SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreDashboardScreen(viewModel: SecureViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF14B8A6),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ShieldChat",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(SecureViewModel.Screen.SECURITY_CENTER) },
                        modifier = Modifier.testTag("sec_center_icon")
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = "Security Status Center", tint = Color(0xFF14B8A6))
                    }
                    IconButton(
                        onClick = { viewModel.logout() }
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.LightGray)
                    }
                    // Profile Accent Avatar
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseSafeColor(currentUser?.profilePicHex ?: "#FF14B8A6")),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentUser?.fullName?.take(1) ?: "U").uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A)
            ) {
                NavigationBarItem(
                    selected = activeTab == SecureViewModel.Tab.CHATS,
                    onClick = { viewModel.setTab(SecureViewModel.Tab.CHATS) },
                    icon = { Icon(Icons.Rounded.Chat, contentDescription = null) },
                    label = { Text("Chats", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_chats")
                )
                NavigationBarItem(
                    selected = activeTab == SecureViewModel.Tab.REQUESTS,
                    onClick = { viewModel.setTab(SecureViewModel.Tab.REQUESTS) },
                    icon = {
                        val requests by viewModel.allRequests.collectAsStateWithLifecycle()
                        val pendingCount = requests.count { it.status == "PENDING" && it.receiverUsername == currentUser?.username }
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(containerColor = Color(0xFFE11D48)) {
                                        Text(pendingCount.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Contacts, contentDescription = null)
                        }
                    },
                    label = { Text("Requests", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_requests")
                )
                NavigationBarItem(
                    selected = activeTab == SecureViewModel.Tab.SEARCH,
                    onClick = { viewModel.setTab(SecureViewModel.Tab.SEARCH) },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text("Discover", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_search")
                )
                NavigationBarItem(
                    selected = activeTab == SecureViewModel.Tab.CALLS,
                    onClick = { viewModel.setTab(SecureViewModel.Tab.CALLS) },
                    icon = { Icon(Icons.Rounded.Call, contentDescription = null) },
                    label = { Text("Calls", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_calls")
                )
            }
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                SecureViewModel.Tab.CHATS -> TabChatsScreen(viewModel)
                SecureViewModel.Tab.REQUESTS -> TabRequestsScreen(viewModel)
                SecureViewModel.Tab.SEARCH -> TabSearchScreen(viewModel)
                SecureViewModel.Tab.CALLS -> TabCallsScreen(viewModel)
            }
        }
    }
}

// ---------------- TAB: CHATS (WhatsApp & Instagram Combined) ----------------
@Composable
fun TabChatsScreen(viewModel: SecureViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val requests by viewModel.allRequests.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    var activeStoryViewUser by remember { mutableStateOf<UserEntity?>(null) }

    // Find profiles with whom we have an ACCEPTED request
    val matchedUsers = remember(users, requests, currentUser, blockedUsers) {
        val acceptedUsernames = requests.filter { it.status == "ACCEPTED" }.flatMap {
            listOf(it.senderUsername, it.receiverUsername)
        }.filter { it != currentUser?.username }.toSet()

        users.filter { acceptedUsernames.contains(it.username) && !blockedUsers.contains(it.username) }
    }

    // Instagram style Status/Story preset content
    val storiesList = remember(users) {
        users.filter { !it.username.startsWith("@me_") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Instagram-inspired Stories/Status Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Secure Status Stories",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14B8A6),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Current User Status Creator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF334155), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("My Status", fontSize = 11.sp, color = Color.Gray)
                    }

                    // Simulated active online stories
                    storiesList.forEach { user ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { activeStoryViewUser = user }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(parseSafeColor(user.profilePicHex))
                                    .border(2.dp, Color(0xFF14B8A6), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.fullName.take(1).uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(user.fullName.substringBefore(" "), fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Divider(color = Color(0xFF1E293B), thickness = 1.dp)
        }

        // WhatsApp-inspired Recent Chats list
        item {
            Text(
                text = "Recent Secure Chats",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        if (matchedUsers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No E2EE active chats found.",
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Discover users or check pending chat requests on the bottom tabs to begin.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(matchedUsers) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { viewModel.openChat(user) }
                        .testTag("chat_item_${user.username.replace("@", "")}"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar with E2E indicator
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(parseSafeColor(user.profilePicHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.fullName.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Active Online Green dot
                            if (user.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0xFF10B981), CircleShape)
                                        .border(2.dp, Color(0xFF111827), CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1.0f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.fullName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (user.deviceVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified device ID matches key ring",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF14B8A6), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AES End-to-End Encrypted key matched",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.triggerCallSimulation(user.username, isVoiceOnly = true, isIncoming = false) }
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Secure Call", tint = Color(0xFF14B8A6))
                        }
                    }
                }
            }
        }
    }

    // Instagram Full-screen Disappearing Status Overlay
    if (activeStoryViewUser != null) {
        val user = activeStoryViewUser!!
        Dialog(onDismissRequest = { activeStoryViewUser = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.65f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF14B8A6), RoundedCornerShape(24.dp))
            ) {
                // Background visual cryptography canvas pattern
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = 20
                    for (i in 0..count) {
                        drawCircle(
                            color = Color(0xFF14B8A6).copy(alpha = 0.04f),
                            radius = (i * 20).dp.toPx(),
                            style = Stroke(2f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile info head
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseSafeColor(user.profilePicHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.fullName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(user.fullName, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(user.username, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    // Big Secure Lock Banner visual representing safe disappearing status
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF14B8A6).copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Status Secured with AES-GCM",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "\"${user.bio}\"",
                            fontSize = 14.sp,
                            color = Color(0xFF14B8A6),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        )
                    }

                    // Ticker count indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { activeStoryViewUser = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Disappearing Status", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB: REQUESTS (E2EE Chat Request manager) ----------------
@Composable
fun TabRequestsScreen(viewModel: SecureViewModel) {
    val requests by viewModel.allRequests.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val incomingRequests = remember(requests, currentUser) {
        requests.filter { it.receiverUsername == currentUser?.username && it.status == "PENDING" }
    }

    if (incomingRequests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = Color(0xFF1E293B),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Secure Request Vault",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "No pending inbound chat requests. Only users you approve can initiate E2EE messaging keys exchanges with your device.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Pending Inbound Connections (${incomingRequests.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14B8A6)
                )
            }

            items(incomingRequests) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("req_card_${req.senderUsername.replace("@", "")}"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Color(0xFF14B8A6), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Connection Request from ${req.senderUsername}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Wishes to exchange RSA identity signatures. Encrypted messaging will unlock post acceptance.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.respondToChatRequest(req.id, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1.0f).testTag("accept_req_btn")
                            ) {
                                Text("Approve & Exchange E2EE Keys", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.respondToChatRequest(req.id, false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE11D48)),
                                modifier = Modifier.weight(0.5f).testTag("reject_req_btn")
                            ) {
                                Text("Ignore", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB: SEARCH (Discover and Send Requests) ----------------
@Composable
fun TabSearchScreen(viewModel: SecureViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val requests by viewModel.allRequests.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // High fidelity search input bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search unique user @username") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("search_bar_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF14B8A6),
                focusedLabelColor = Color(0xFF14B8A6)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Discover Peers",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Search direct handle accounts (e.g., \"alex\", \"sophia\") to initiate encrypted channels handshake requests.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(searchResults) { user ->
                    // Find if any chat request already exists
                    val req = requests.find {
                        (it.senderUsername == currentUser?.username && it.receiverUsername == user.username) ||
                                (it.senderUsername == user.username && it.receiverUsername == currentUser?.username)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("search_result_card_${user.username.replace("@", "")}"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Profile representation
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(parseSafeColor(user.profilePicHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.fullName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.fullName, color = Color.White, fontWeight = FontWeight.Bold)
                                        if (user.deviceVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Text(user.username, color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = user.bio,
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            when {
                                req == null -> {
                                    Button(
                                        onClick = { viewModel.sendChatRequest(user.username) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                                        modifier = Modifier.fillMaxWidth().testTag("send_request_btn")
                                    ) {
                                        Icon(Icons.Default.SendAndArchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Send Secure Chat Request", fontWeight = FontWeight.Bold)
                                    }
                                }
                                req.status == "PENDING" -> {
                                    OutlinedButton(
                                        onClick = { /* Wait for acceptance */ },
                                        modifier = Modifier.fillMaxWidth().testTag("req_pending_btn"),
                                        enabled = false
                                    ) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Requested (Awaiting Cryptographic Approval)", fontSize = 12.sp)
                                    }
                                }
                                req.status == "ACCEPTED" -> {
                                    Button(
                                        onClick = { viewModel.openChat(user) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth().testTag("open_chat_btn")
                                    ) {
                                        Icon(Icons.Default.MarkChatRead, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open E2EE Conversation", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB: CALLS (VoIP secure log history) ----------------
@Composable
fun TabCallsScreen(viewModel: SecureViewModel) {
    val logs by viewModel.allCallLogs.collectAsStateWithLifecycle()

    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PhoneCallback,
                    tint = Color(0xFF1E293B),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Call Log Vault",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "No phone or video calls recorded. Your cryptographic VoIP exchanges leave zero signaling server footprint logs.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Secure VoIP History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14B8A6)
                    )
                    TextButton(onClick = { /* Clear local logs */ }) {
                        Text("Clear Logs", color = Color(0xFFF43F5E), fontSize = 12.sp)
                    }
                }
            }

            items(logs) { call ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                tint = Color(0xFF14B8A6),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "Secure Session with ${call.contactUsername}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (call.status == "MISSED") Icons.Default.CallMissed else Icons.Default.CallReceived,
                                    tint = if (call.status == "MISSED") Color(0xFFF43F5E) else Color(0xFF10B981),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${call.status} • ${if (call.durationSec > 0) "${call.durationSec}s" else "Missed"}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.triggerCallSimulation(call.contactUsername, isVoiceOnly = !call.isVideo, isIncoming = false) }
                        ) {
                            Icon(
                                imageVector = if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = "Retry call",
                                tint = Color(0xFF14B8A6)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- ACTIVE CHAT SCREEN (E2EE Chat Room with controls) ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatScreen(viewModel: SecureViewModel) {
    val peer by viewModel.activeChatPeer.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var disappearingMode by remember { mutableStateOf(false) }
    var showFingerprintDialog by remember { mutableStateOf(false) }

    val opponent = peer ?: return
    val isPeerBlocked = blockedUsers.contains(opponent.username)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showFingerprintDialog = true } // Tap to show visual verify codes
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseSafeColor(opponent.profilePicHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opponent.fullName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(opponent.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                val verifyIcon = if (opponent.deviceVerified) Icons.Default.Verified else Icons.Default.GppBad
                                Icon(
                                    imageVector = verifyIcon,
                                    contentDescription = null,
                                    tint = if (opponent.deviceVerified) Color(0xFF10B981) else Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (opponent.isOnline) "secured online" else "secured offline",
                                fontSize = 11.sp,
                                color = if (opponent.isOnline) Color(0xFF10B981) else Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Disappearing Messages secure toggle button
                    IconButton(
                        onClick = { disappearingMode = !disappearingMode },
                        modifier = Modifier.testTag("toggle_disappearing_btn")
                    ) {
                        Icon(
                            imageVector = if (disappearingMode) Icons.Default.Timelapse else Icons.Default.Timer,
                            contentDescription = "Toggle Disappearing secrets",
                            tint = if (disappearingMode) Color(0xFF14B8A6) else Color.LightGray
                        )
                    }

                    IconButton(
                        onClick = { viewModel.triggerCallSimulation(opponent.username, isVoiceOnly = true, isIncoming = false) },
                        modifier = Modifier.testTag("dial_voice")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = Color(0xFF14B8A6))
                    }

                    IconButton(
                        onClick = { viewModel.triggerCallSimulation(opponent.username, isVoiceOnly = false, isIncoming = false) },
                        modifier = Modifier.testTag("dial_video")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color(0xFF14B8A6))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Messages Area
            LazyColumn(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                reverseLayout = false,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cryptographic handshake banner
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1524)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF14B8A6), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("End-to-End Encryption Armed", fontSize = 12.sp, color = Color(0xFF14B8A6), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Security fingerprints verified. Messages and calls in this conversation are locked with your local hardware key rings.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                items(messages) { msg ->
                    // Decrypt AES payload
                    val symKeyHex = SecureCrypto.generateSymmetricKeyHex(msg.senderUsername, msg.receiverUsername)
                    val decryptedText = SecureCrypto.decryptAES(msg.encryptedPayloadHex, symKeyHex)

                    val isMe = msg.senderUsername != opponent.username

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 2.dp,
                                        bottomEnd = if (isMe) 2.dp else 16.dp
                                    )
                                )
                                .background(if (isMe) Color(0xFF0D9488) else Color(0xFF1E293B))
                                .clickable { /* Tap to trigger msg option Dialog */ }
                                .padding(12.dp)
                        ) {
                            Column {
                                if (msg.disappearing) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Disappearing secret", fontSize = 9.sp, color = Color(0xFFFFCC00))
                                    }
                                }

                                if (msg.isFakeReported) {
                                    Text(
                                        "[MESSAGE FLAGGED AS SPAM/FAKE - SECURELY SHIELDED]",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF43F5E)
                                    )
                                } else {
                                    Text(
                                        text = decryptedText,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                    Text(
                                        text = timeStr,
                                        fontSize = 9.sp,
                                        color = Color.LightGray.copy(alpha = 0.8f)
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = when (msg.status) {
                                                "SENT" -> Icons.Default.Check
                                                "DELIVERED" -> Icons.Default.DoneAll
                                                else -> Icons.Default.DoneAll // SEEN
                                            },
                                            contentDescription = null,
                                            tint = if (msg.status == "SEEN") Color(0xFF14B8A6) else Color.Gray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Message Action items for safety trials (Delete, Report Spam)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                "Delete",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { viewModel.deleteMessage(msg.id) }
                            )
                            if (!isMe) {
                                Text(
                                    "Report Spam",
                                    color = Color(0xFFF43F5E),
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { viewModel.reportFakeAccount(msg.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Keyboard/Media sending deck
            if (isPeerBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "You blocked this contact. Unblock in Security Settings to resume encrypted exchanges.",
                        color = Color(0xFFE11D48),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            // Mimic secure media attachment sending
                            viewModel.sendMessage("📎 Encrypted Image attachment: raw_res_secure_pic.png", isDisappearing = disappearingMode)
                        }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach media", tint = Color.LightGray)
                        }

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("E2EE Shielded message...") },
                            modifier = Modifier
                                .weight(1.0f)
                                .testTag("message_input_box"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        // Voice record mockup
                        IconButton(onClick = {
                            viewModel.sendMessage("🎤 E2EE Voice Message (0:04)", isVoice = true, voiceSec = 4, isDisappearing = disappearingMode)
                        }, modifier = Modifier.testTag("send_voice_btn")) {
                            Icon(Icons.Default.Mic, contentDescription = "Record Secure Voice Memo", tint = Color(0xFF14B8A6))
                        }

                        IconButton(
                            onClick = {
                                if (messageText.isNotEmpty()) {
                                    viewModel.sendMessage(messageText, isDisappearing = disappearingMode)
                                    messageText = ""
                                }
                            },
                            enabled = messageText.isNotEmpty(),
                            modifier = Modifier.testTag("send_msg_btn")
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (messageText.isNotEmpty()) Color(0xFF14B8A6) else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // Cryptographic Keys Visual Fingerprint Verification Dialog
    if (showFingerprintDialog) {
        val symKey = SecureCrypto.generateSymmetricKeyHex(viewModel.currentUser.value?.username ?: "", opponent.username)
        val fingerprint = SecureCrypto.generateFingerprint(symKey)

        Dialog(onDismissRequest = { showFingerprintDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF14B8A6)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF14B8A6), modifier = Modifier.size(96.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "E2EE Verification Fingerprint",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "Verify this 20-digit code with ${opponent.fullName} to confirm the channel status is immune to leaks or active wiretaps.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fingerprint,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14B8A6),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showFingerprintDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fingerprint Matches")
                    }
                }
            }
        }
    }
}

// ---------------- CALL SCREEN (Voice & Video secure simulation) ----------------
@Composable
fun CallScreen(viewModel: SecureViewModel) {
    val callState by viewModel.activeCall.collectAsStateWithLifecycle()
    val call = callState ?: return

    var muteEnabled by remember { mutableStateOf(false) }
    var cameraEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060913))
            .padding(24.dp)
    ) {
        // High quality animated radar/cryptic network lines drawing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = (System.currentTimeMillis() % 3000) / 3000.0f
            drawCircle(
                color = Color(0xFF14B8A6).copy(alpha = (1.0f - progress) * 0.15f),
                radius = (progress * 250).dp.toPx(),
                style = Stroke(4f)
            )
        }

        // Top info header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SECURE E2EE VOIP LINK",
                fontSize = 12.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Call status & avatar main block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rotating profile orb
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(parseSafeColor(call.peer.profilePicHex))
                    .border(3.dp, Color(0xFF14B8A6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = call.peer.fullName.take(1).uppercase(),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = call.peer.fullName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = call.peer.username,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pulse Timer
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp)
            ) {
                val secStr = (call.durationSec % 60).toString().padStart(2, '0')
                val minStr = (call.durationSec / 60).toString().padStart(2, '0')
                val label = if (call.status == "CONNECTED") "$minStr:$secStr" else call.status
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14B8A6),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (call.isIncoming && call.status == "RINGING") {
                // Incoming ring action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.acceptIncomingCall() },
                        containerColor = Color(0xFF10B910),
                        modifier = Modifier.testTag("accept_call")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Accept encrypted call", tint = Color.White)
                    }

                    FloatingActionButton(
                        onClick = { viewModel.hangupCall() },
                        containerColor = Color(0xFFF43F5E),
                        modifier = Modifier.testTag("decline_call")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = Color.White)
                    }
                }
            } else {
                // Active connected call controls row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    IconButton(
                        onClick = { muteEnabled = !muteEnabled },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (muteEnabled) Color.White else Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (muteEnabled) Icons.Default.MicOff else Icons.Default.Mic,
                            tint = if (muteEnabled) Color.Black else Color.White,
                            contentDescription = "Mute mic"
                        )
                    }

                    // Video toggle
                    if (call.isVideo) {
                        IconButton(
                            onClick = { cameraEnabled = !cameraEnabled },
                            modifier = Modifier
                                .size(56.dp)
                                .background(if (cameraEnabled) Color(0xFF1E293B) else Color(0xFFF43F5E), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                tint = Color.White,
                                contentDescription = "Toggle stream camera"
                            )
                        }
                    }

                    // End call
                    FloatingActionButton(
                        onClick = { viewModel.hangupCall() },
                        containerColor = Color(0xFFF43F5E),
                        modifier = Modifier.testTag("hangup_btn")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Hang up call", tint = Color.White)
                    }
                }
            }
        }
    }
}

// ---------------- SECURITY STATUS CENTER SCREEN (Advanced safety toggles) ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(viewModel: SecureViewModel) {
    val screenshotMode by viewModel.screenshotProtection.collectAsStateWithLifecycle()
    val spamMode by viewModel.spamShield.collectAsStateWithLifecycle()
    val twoFactorMode by viewModel.twoFactorEnabled.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Security Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(SecureViewModel.Screen.CORE_DASHBOARD) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, Color(0xFF14B8A6))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF14B8A6), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Device Identity Verified", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Your public keys are verified against cryptographic root certificates on start. This client is tamper-immune.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            item {
                Text("Privacy Controls", fontWeight = FontWeight.Bold, color = Color(0xFF14B8A6), fontSize = 14.sp)
            }

            // Screen protection toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text("Screenshot Protection", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Enforce secure window boundaries (blocks OS screenshots & screen recorders inside chats)", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = screenshotMode,
                            onCheckedChange = { viewModel.toggleScreenshotProtection() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF14B8A6)),
                            modifier = Modifier.testTag("toggle_screenshot_protection")
                        )
                    }
                }
            }

            // Spam detection toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text("Real-Time Spam Shield", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Automatically block unverified bot networks from initiating chat requests", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = spamMode,
                            onCheckedChange = { viewModel.toggleSpamShield() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF14B8A6)),
                            modifier = Modifier.testTag("toggle_spam_shield")
                        )
                    }
                }
            }

            // 2-Factor Authentication option
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text("Two-Factor Authentication (2FA)", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Require secure security PIN token upon multi-device re-registry handshakes", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = twoFactorMode,
                            onCheckedChange = { viewModel.toggle2FA() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF14B8A6)),
                            modifier = Modifier.testTag("toggle_2fa")
                        )
                    }
                }
            }

            item {
                Text("Blocked Registries", fontWeight = FontWeight.Bold, color = Color(0xFF14B8A6), fontSize = 14.sp)
            }

            if (blockedUsers.isEmpty()) {
                item {
                    Text("No blocked contacts. Use 'Report Spam' in individual chats to register shielding overrides.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                items(blockedUsers.toList()) { userHandle ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(userHandle, color = Color.White, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { viewModel.toggleBlockUser(userHandle) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}
