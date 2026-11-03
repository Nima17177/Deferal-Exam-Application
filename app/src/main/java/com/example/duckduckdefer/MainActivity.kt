
package com.example.duckduckdefer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.duckduckdefer.components.AppDrawer
import com.example.duckduckdefer.components.chatbot.ChatbotRoutes
import com.example.duckduckdefer.screens.*
import com.example.duckduckdefer.screens.AdminScreens.ProctorAssignmentScreen
import com.example.duckduckdefer.screens.AdminScreens.ViewFeedbackScreen
import com.example.duckduckdefer.screens.SettingsScreens.AccessibilitySettingsScreen
import com.example.duckduckdefer.screens.SettingsScreens.EditProfileScreen
import com.example.duckduckdefer.screens.SettingsScreens.HelpOnboardingScreen
import com.example.duckduckdefer.screens.SettingsScreens.FeedbackScreen
import com.example.duckduckdefer.screens.SettingsScreens.NotificationSettingsScreen
import com.example.duckduckdefer.screens.SettingsScreens.ProfileScreen
import com.example.duckduckdefer.screens.messaging.ChatScreen
import com.example.duckduckdefer.screens.messaging.ConversationsListScreen
import com.example.duckduckdefer.screens.messaging.DuckBotChatScreen
import com.example.duckduckdefer.screens.messaging.NewConversationScreen
import com.example.duckduckdefer.screens.NotificationCenterScreen
import com.example.duckduckdefer.services.NotificationRegistry
import com.example.duckduckdefer.services.SoundPreferenceCache
import com.example.duckduckdefer.ui.theme.DuckDuckDeferTheme
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.viewmodels.ChatbotViewModel
import com.example.duckduckdefer.viewmodels.MessagingViewModel
import com.example.duckduckdefer.viewmodels.NotificationCenterViewModel
import com.example.duckduckdefer.utils.NfcAttendanceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var credentialsManager: CredentialsManager

    /** Whether NFC foreground dispatch is currently active (a dialog is listening). */
    private var nfcForegroundActive = false

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "AppPrefs"
        private const val KEY_NOTIFICATION_DENIED_ONCE = "notification_denied_once"

        data class GooglePayResult(val resultCode: Int, val data: Intent?)
        val googlePayResultFlow = MutableSharedFlow<GooglePayResult>(extraBufferCapacity = 1)

        /** Flow that emits raw NFC intents so Compose screens can react.
         *  replay = 1 ensures the latest intent isn't lost if the collector
         *  subscribes slightly after emission (common race during recomposition). */
        private val _nfcIntentFlow = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 2)
        val nfcIntentFlow = _nfcIntentFlow

        /** Observable NFC availability state */
        private val _nfcAvailable = MutableStateFlow(false)
        val nfcAvailable = _nfcAvailable.asStateFlow()

        private val _nfcEnabled = MutableStateFlow(false)
        val nfcEnabled = _nfcEnabled.asStateFlow()
    }

    private val pendingConversationId = mutableStateOf<String?>(null)
    private val pendingBookingId = mutableStateOf<String?>(null)
    private val pendingNavigationType = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Check if this is an NFC intent
        if (intent.action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            val emitted = _nfcIntentFlow.tryEmit(intent)
            if (!emitted) {
                Log.w("MainActivity", "NFC intent dropped — flow buffer full!")
            }
        } else {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            pendingConversationId.value = it.getStringExtra("conversationId")
            pendingBookingId.value = it.getStringExtra("bookingId")
            pendingNavigationType.value = it.getStringExtra("type")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 999) {
            lifecycleScope.launch {
                googlePayResultFlow.emit(GooglePayResult(resultCode, data))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        NotificationRegistry.setAppForeground(true)
    }

    override fun onResume() {
        super.onResume()
        // Refresh NFC state
        _nfcAvailable.value = NfcAttendanceManager.isNfcAvailable(this)
        _nfcEnabled.value = NfcAttendanceManager.isNfcEnabled(this)
        // Enable NFC foreground dispatch if a dialog requested it
        if (nfcForegroundActive) {
            NfcAttendanceManager.enableForegroundDispatch(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Always disable foreground dispatch on pause
        if (nfcForegroundActive) {
            NfcAttendanceManager.disableForegroundDispatch(this)
        }
    }

    override fun onStop() {
        super.onStop()
        NotificationRegistry.setAppForeground(false)
    }

    /** Called by composables to start/stop NFC foreground dispatch. */
    fun setNfcForegroundActive(active: Boolean) {
        nfcForegroundActive = active
        if (active) {
            NfcAttendanceManager.enableForegroundDispatch(this)
        } else {
            NfcAttendanceManager.disableForegroundDispatch(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // CRITICAL: Initialize Firestore workaround FIRST - handles GMS broker errors
        com.example.duckduckdefer.utils.FirestoreBrokerWorkaround.initializeFirestoreWithWorkaround()

        // Initialize Firebase and check GMS availability
        com.example.duckduckdefer.utils.FirebaseGMSHelper.initializeFirestore()
        val isGMSAvailable = com.example.duckduckdefer.utils.FirebaseGMSHelper.isGooglePlayServicesAvailable(this)
        if (!isGMSAvailable) {
            Log.w("MainActivity", "Google Play Services may not be fully available. Some features may not work correctly.")
        }

        biometricAuthManager = BiometricAuthManager(this)
        credentialsManager = CredentialsManager(this)

        requestNotificationPermission()
        handleIntent(intent)

        setContent {
            var currentUser by remember { mutableStateOf<UserMembersClass?>(null) }
            var appThemePreference by remember { mutableIntStateOf(0) }
            var appFontSizePreference by remember { mutableIntStateOf(1) }

            // Gamification UI state
            var showLevelUpDialog by remember { mutableStateOf(false) }
            var newLevelReached by remember { mutableIntStateOf(1) }
            var streakInfo by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }

            LaunchedEffect(currentUser?.themePreference, currentUser?.fontSizePreference) {
                currentUser?.let {
                    appThemePreference = it.themePreference
                    appFontSizePreference = it.fontSizePreference
                }
            }


            DuckDuckDeferTheme(
                themePreference = appThemePreference,
                fontSizePreference = appFontSizePreference
            ) {
                val navController = rememberNavController()
                var isRestoringSession by remember { mutableStateOf(true) }
                val messagingViewModel: MessagingViewModel = viewModel()
                val notificationCenterViewModel: NotificationCenterViewModel = viewModel()
                val chatbotViewModel: ChatbotViewModel = viewModel()
                val totalUnreadCount by messagingViewModel.totalUnreadCount.collectAsState()
                val notificationUnreadCount by notificationCenterViewModel.unreadCount.collectAsState()
                val context = this@MainActivity

                val conversationToOpen by pendingConversationId
                val bookingToOpen by pendingBookingId
                val navType by pendingNavigationType

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                LaunchedEffect(navBackStackEntry) {
                    val route = navBackStackEntry?.destination?.route
                    if (route?.startsWith("chat/") == true) {
                        val convId = navBackStackEntry?.arguments?.getString("conversationId")
                        NotificationRegistry.setActiveConversation(convId)
                    } else {
                        NotificationRegistry.setActiveConversation(null)
                    }
                }

                LaunchedEffect(Unit) {
                    launch {
                        val firebaseUser = FirebaseAuth.getInstance().currentUser
                        if (firebaseUser != null) {
                            val uid = firebaseUser.uid
                            FirebaseFirestore.getInstance().collection("UserAccounts").document(uid).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val role = document.getLong("thisRole")?.toInt() ?: 3
                                        val status = document.getLong("thisApprovalStatus")?.toInt() ?: 0

                                        if (status == 1) {
                                            val user = UserMembersClass.create(role)
                                            user.getUserData(uid) { success ->
                                                if (success) {
                                                    currentUser = user
                                                    // Cache notification sound preference for FCM service
                                                    SoundPreferenceCache.save(this@MainActivity, user.notificationSound)
                                                    // Check daily streak on login
                                                    user.checkDailyStreak { streakSuccess, streakVal ->
                                                        if (streakSuccess) streakInfo = true to streakVal
                                                    }
                                                }
                                                isRestoringSession = false
                                            }
                                        } else {
                                            FirebaseAuth.getInstance().signOut()
                                            isRestoringSession = false
                                        }
                                    } else {
                                        isRestoringSession = false
                                    }
                                }
                                .addOnFailureListener { isRestoringSession = false }
                        } else {
                            isRestoringSession = false
                        }
                    }
                }

                LaunchedEffect(conversationToOpen, bookingToOpen, navType, currentUser, isRestoringSession) {
                    if (currentUser != null && !isRestoringSession) {
                        if (conversationToOpen != null) {
                            navController.navigate("chat/$conversationToOpen") { popUpTo("dashboard") { inclusive = false } }
                            pendingConversationId.value = null
                        } else if (bookingToOpen != null) {
                            navController.navigate("live_booking/$bookingToOpen") { popUpTo("dashboard") { inclusive = false } }
                            pendingBookingId.value = null
                        } else if (navType == "proctor_assigned") {
                            navController.navigate("proctor_availability") { popUpTo("dashboard") { inclusive = false } }
                            pendingNavigationType.value = null
                        } else if (navType == "proctor_needed") {
                            navController.navigate("proctor_assignment") { popUpTo("dashboard") { inclusive = false } }
                            pendingNavigationType.value = null
                        } else if (navType == "manage_users") {
                            navController.navigate("manage_users") { popUpTo("dashboard") { inclusive = false } }
                            pendingNavigationType.value = null
                        }
                    }
                }

                LaunchedEffect(currentUser) {
                    currentUser?.let { user ->
                        messagingViewModel.initialize(user.thisUID)
                        notificationCenterViewModel.initialize(user.thisUID)
                        chatbotViewModel.initialize(user.thisRole, user.thisUID, user.thisUserName)
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            MessagingRepository().updateFcmToken(user.thisUID, token)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(DuckGradients.heroGradient())) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        // --- Gamification Dialogs ---
                        if (showLevelUpDialog) {
                            AlertDialog(
                                onDismissRequest = { showLevelUpDialog = false },
                                title = { Text("Level Up! 🎉") },
                                text = { Text("Congratulations! You've reached Level $newLevelReached.") },
                                confirmButton = { Button(onClick = { showLevelUpDialog = false }) { Text("Awesome!") } }
                            )
                        }

                        if (streakInfo != null && streakInfo?.first == true) {
                            val streak = streakInfo?.second ?: 0
                            if (streak > 1) {
                                LaunchedEffect(streakInfo) {
                                    delay(3000)
                                    streakInfo = null
                                }
                                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, shadowElevation = 8.dp) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Streak: $streak Days! 🔥", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            } else {
                                streakInfo = null
                            }
                        }

                        AppDrawer(
                            user = currentUser,
                            navController = navController,
                            unreadCount = totalUnreadCount,
                            notificationUnreadCount = notificationUnreadCount,
                            onLogout = {
                                currentUser?.thisUID?.let(MessagingRepository()::clearFcmToken)
                                FirebaseMessaging.getInstance().deleteToken()
                                (context.getSystemService(NotificationManager::class.java)).cancelAll()
                                FirebaseAuth.getInstance().signOut()
                                messagingViewModel.cleanup()
                                chatbotViewModel.cleanup()
                                currentUser = null
                                navController.navigate("login") { popUpTo(0) { inclusive = true } }
                            }
                        ) { onOpenMenu ->
                            NavHost(navController = navController, startDestination = "splash") {
                                composable("splash") {
                                    SplashScreen(onTimeout = {
                                        if (!isRestoringSession) {
                                            if (currentUser != null) {
                                                navController.navigate("dashboard") { popUpTo("splash") { inclusive = true } }
                                            } else {
                                                navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                                            }
                                        }
                                    })
                                }

                                composable("login") {
                                    LoginScreen(
                                        onLoginSuccess = { user ->
                                            currentUser = user
                                            // Cache notification sound preference for FCM service
                                            SoundPreferenceCache.save(this@MainActivity, user.notificationSound)
                                            appThemePreference = user.themePreference
                                            appFontSizePreference = user.fontSizePreference
                                            navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                                        },
                                        onGoToRegister = { navController.navigate("register") },
                                        onForgotPassword = { },
                                        biometricAuthManager = biometricAuthManager,
                                        credentialsManager = credentialsManager,
                                        activity = this@MainActivity
                                    )
                                }

                                composable("register") {
                                    RegisterScreen(
                                        onBackToLogin = { navController.popBackStack() },
                                        onRegistrationSuccess = {
                                            navController.navigate("login") {
                                                popUpTo("register") { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable("dashboard") {
                                    currentUser?.let { user ->
                                        DashboardScreen(
                                            user = user,
                                            notificationUnreadCount = notificationUnreadCount,
                                            onOpenMenu = onOpenMenu,
                                            onCreateWeeklySessions = { navController.navigate("detailed_weekly_sessions") },
                                            onNavigateToCoverage = { navController.navigate("proctor_assignment") },
                                            onNavigateToLiveBooking = { navController.navigate("live_booking/$it") },
                                            onNavigateToUserProfile = { navController.navigate("profile/$it") },
                                            onNavigateToMyBookings = { navController.navigate("my_bookings") },
                                            onNavigateToProctorAvailability = { navController.navigate("proctor_availability") },
                                            onNavigateToNotifications = { navController.navigate("notification_center") },
                                            onNavigateToSummary = { navController.navigate("data_summary") }
                                        )
                                    }
                                }

                                composable("leaderboard") {
                                    currentUser?.let { user ->
                                        LeaderboardScreen(
                                            currentUser = user,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu,
                                            onRefreshUser = {
                                                user.getUserData(user.thisUID) { if (it) currentUser = user }
                                            },
                                            onNavigateToProfile = { userId ->
                                                navController.navigate("profile/$userId")
                                            }
                                        )
                                    }
                                }

                                composable("data_summary") {
                                    currentUser?.let { user ->
                                        DataSummaryScreen(
                                            user = user,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }

                                composable("live_booking/{bookingID}", arguments = listOf(navArgument("bookingID") { type = NavType.StringType })) { backStackEntry ->
                                    val bookingID = backStackEntry.arguments?.getString("bookingID") ?: return@composable
                                    currentUser?.let { user ->
                                        LiveBookingScreen(
                                            bookingID = bookingID,
                                            currentUser = user,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToChat = { navController.navigate("chat/$it") }
                                        )
                                    }
                                }

                                composable("detailed_weekly_sessions") {
                                    currentUser?.let { user ->
                                        DetailedWeeklySessionsScreen(user = user, isMyBookingsMode = false, onBack = { navController.popBackStack() }, onOpenMenu = onOpenMenu, onNavigateToLiveBooking = { navController.navigate("live_booking/$it") })
                                    }
                                }

                                composable("my_bookings") {
                                    currentUser?.let { user ->
                                        DetailedWeeklySessionsScreen(user = user, isMyBookingsMode = true, onBack = { navController.popBackStack() }, onOpenMenu = onOpenMenu, onNavigateToLiveBooking = { navController.navigate("live_booking/$it") })
                                    }
                                }

                                composable("proctor_availability") {
                                    currentUser?.let { user ->
                                        ProctorAvailabilityScreen(user = user, onBack = { navController.popBackStack() }, onOpenMenu = onOpenMenu)
                                    }
                                }

                                composable("manage_users") {
                                    (currentUser as? AdminSubclass)?.let { admin ->
                                        ManageUsersScreen(user = admin, onBack = { navController.popBackStack() }, onOpenMenu = onOpenMenu)
                                    }
                                }

                                composable("proctor_assignment") {
                                    (currentUser as? AdminSubclass)?.let { admin ->
                                        ProctorAssignmentScreen(user = admin, onBack = { navController.popBackStack() }, onOpenMenu = onOpenMenu)
                                    }
                                }

                                composable("settings") {
                                    currentUser?.let { user ->
                                        SettingsScreen(
                                            user = user,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu,
                                            onNavigateToProfile = { navController.navigate("profile/${user.thisUID}") },
                                            onNavigateToNotificationSettings = { navController.navigate("notificationSettings") },
                                            onNavigateToAccessibilitySettings = { navController.navigate("accessibilitySettings") },
                                            onNavigateToSummary = { navController.navigate("data_summary") },
                                            onNavigateToHelp = { navController.navigate("help_onboarding") },
                                            onNavigateToFeedback = { navController.navigate("feedback") },
                                            onLogout = {
                                                MessagingRepository().clearFcmToken(user.thisUID)
                                                FirebaseMessaging.getInstance().deleteToken()
                                                (context.getSystemService(NotificationManager::class.java)).cancelAll()
                                                FirebaseAuth.getInstance().signOut()
                                                messagingViewModel.cleanup()
                                                chatbotViewModel.cleanup()
                                                currentUser = null
                                                navController.navigate("login") { popUpTo(0) { inclusive = true } }
                                            }
                                        )
                                    }
                                }

                                composable("notificationSettings") {
                                    currentUser?.let {
                                        NotificationSettingsScreen(
                                            user = it,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }

                                composable("notification_center") {
                                    currentUser?.let { user ->
                                        NotificationCenterScreen(
                                            user = user,
                                            viewModel = notificationCenterViewModel,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu,
                                            onNavigateToChat = { conversationId ->
                                                navController.navigate("chat/$conversationId")
                                            },
                                            onNavigateToBooking = { bookingId ->
                                                navController.navigate("live_booking/$bookingId")
                                            },
                                            onNavigateToProctorAssignment = {
                                                navController.navigate("proctor_assignment")
                                            },
                                            onNavigateToManageUsers = {
                                                navController.navigate("manage_users")
                                            }
                                        )
                                    }
                                }

                                composable("accessibilitySettings") {
                                    currentUser?.let {
                                        AccessibilitySettingsScreen(
                                            user = it,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu,
                                            onThemeChanged = { newTheme -> appThemePreference = newTheme },
                                            onFontSizeChanged = { newSize -> appFontSizePreference = newSize }
                                        )
                                    }
                                }

                                composable("help_onboarding") {
                                    currentUser?.let { user ->
                                        HelpOnboardingScreen(
                                            user = user,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu,
                                            onNavigate = { route -> navController.navigate(route) }
                                        )
                                    }
                                }

                                composable("feedback") {
                                    currentUser?.let { user ->
                                        FeedbackScreen(
                                            user = user,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }

                                composable("view_feedback") {
                                    (currentUser as? AdminSubclass)?.let { admin ->
                                        ViewFeedbackScreen(
                                            user = admin,
                                            onBack = { navController.popBackStack() },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }

                                composable("messages") {
                                    currentUser?.let { user ->
                                        ConversationsListScreen(user = user, viewModel = messagingViewModel, onOpenMenu = onOpenMenu, onNavigateToChat = { navController.navigate("chat/$it") }, onNavigateToNewConversation = { navController.navigate("newConversation") }, onNavigateToDuckBot = { navController.navigate("duckbot_chat") }, onBack = { navController.popBackStack() })
                                    }
                                }

                                composable("chat/{conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { backStackEntry ->
                                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                                    currentUser?.let { user ->
                                        ChatScreen(conversationId = conversationId, currentUser = user, viewModel = messagingViewModel, onBack = { navController.popBackStack() }, onNavigateToProfile = { navController.navigate("profile/$it") })
                                    }
                                }

                                composable("newConversation") {
                                    currentUser?.let { user ->
                                        NewConversationScreen(currentUser = user, viewModel = messagingViewModel, onBack = { navController.popBackStack() }, onConversationCreated = { navController.navigate("chat/$it") { popUpTo("newConversation") { inclusive = true } } })
                                    }
                                }

                                composable("duckbot_chat") {
                                    currentUser?.let {
                                        DuckBotChatScreen(
                                            chatbotViewModel = chatbotViewModel,
                                            onBack = { navController.popBackStack() },
                                            onNavigate = { routeId ->
                                                val mappedRoute = ChatbotRoutes.findRoute(routeId) ?: return@DuckBotChatScreen
                                                val targetRoute = when (mappedRoute.routeId) {
                                                    "profile_self" -> "profile/${currentUser?.thisUID ?: return@DuckBotChatScreen}"
                                                    else -> mappedRoute.navRoute
                                                }
                                                navController.navigate(targetRoute) { launchSingleTop = true }
                                            }
                                        )
                                    }
                                }

                                composable("profile/{userId}", arguments = listOf(navArgument("userId") { type = NavType.StringType })) { backStackEntry ->
                                    val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                                    currentUser?.let { user ->
                                        ProfileScreen(
                                            userId = userId,
                                            currentUser = user,
                                            messagingViewModel = messagingViewModel,
                                            onBack = { navController.popBackStack() },
                                            onEditProfile = { navController.navigate("editProfile") },
                                            onNavigateToChat = { navController.navigate("chat/$it") },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }

                                composable("editProfile") {
                                    currentUser?.let { user ->
                                        EditProfileScreen(
                                            currentUser = user,
                                            onBack = { navController.popBackStack() },
                                            onProfileUpdated = { currentUser = it; navController.popBackStack() },
                                            onOpenMenu = onOpenMenu
                                        )
                                    }
                                }
                            }
                            }
                        }

                    }
                }
            }
        }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasBeenDeniedOnce = sharedPrefs.getBoolean(KEY_NOTIFICATION_DENIED_ONCE, false)

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Only request if they haven't denied it before, OR if the system thinks we should show rationale (which means they've denied it once)
                if (!hasBeenDeniedOnce || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // User denied the permission, save this so we don't nag them again on next launch
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NOTIFICATION_DENIED_ONCE, true)
                    .apply()
            }
        }
    }
}
