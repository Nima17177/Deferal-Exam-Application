package com.example.duckduckdefer.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.duckduckdefer.R
import com.example.duckduckdefer.components.*
import com.example.duckduckdefer.components.NotificationBell
import com.example.duckduckdefer.screens.AdminScreens.RoomSetupScreen
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.viewmodels.DashboardViewModel
import com.example.duckduckdefer.viewmodels.DashboardUiState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: UserMembersClass,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    notificationUnreadCount: Int = 0,
    onOpenMenu: () -> Unit = {},
    onCreateWeeklySessions: () -> Unit = {},
    onNavigateToCoverage: () -> Unit = {},
    onNavigateToLiveBooking: (String) -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToMyBookings: () -> Unit = {},
    onNavigateToProctorAvailability: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSummary: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var sessionToEdit by remember { mutableStateOf<SessionsClass?>(null) }
    var sessionToBook by remember { mutableStateOf<RoomData?>(null) }
    
    var showApproveDialog by remember { mutableStateOf(false) }
    var userToApprove by remember { mutableStateOf<UserMembersClass?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var userToReject by remember { mutableStateOf<UserMembersClass?>(null) }

    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    LaunchedEffect(user) {
        viewModel.loadDashboardData(user)
    }

    val selectedDateStr = today.format(formatter)
    
    val dailySessions = remember(uiState.weekSessions) { 
        uiState.weekSessions.filter { it.dateID == selectedDateStr } 
    }
    val allDailyRooms = remember(dailySessions) { 
        dailySessions.flatMap { it.rooms.values }.associateBy { it.roomName } 
    }
    val roomsNeedingProctors = remember(dailySessions) { 
        dailySessions.flatMap { it.rooms.values }.count { it.assignedProctors.isEmpty() } 
    }

    val isSubViewActive = sessionToEdit != null || sessionToBook != null
    val closeSubView = {
        sessionToEdit = null
        sessionToBook = null
    }

    BackHandler(enabled = isSubViewActive) { closeSubView() }

    // Dialogs
    if (showApproveDialog && userToApprove != null && user is AdminSubclass) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false; userToApprove = null },
            title = { Text(stringResource(R.string.confirm_approval)) },
            text = { Text(stringResource(R.string.confirm_approval_msg, userToApprove?.thisUserName ?: "")) },
            confirmButton = {
                Button(onClick = {
                    user.approveAccount(userToApprove!!) { success ->
                        if (success) viewModel.refresh(user)
                    }
                    showApproveDialog = false
                    userToApprove = null
                }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { showApproveDialog = false; userToApprove = null }) { Text("Cancel") } }
        )
    }

    if (showRejectDialog && userToReject != null && user is AdminSubclass) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false; userToReject = null },
            title = { Text(stringResource(R.string.confirm_rejection)) },
            text = { Text(stringResource(R.string.confirm_rejection_msg, userToReject?.thisUserName ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        user.rejectAccount(userToReject!!) { success ->
                            if (success) viewModel.refresh(user)
                        }
                        showRejectDialog = false
                        userToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false; userToReject = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            if (isSubViewActive) {
                TopAppBar(
                    title = { 
                        Text(when {
                            sessionToEdit != null -> "Edit Session"
                            sessionToBook != null -> "Book Session"
                            else -> ""
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = closeSubView) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    }
                )
            } else {
                // Empty spacer – bell + menu are now inside the hero card
                Spacer(modifier = Modifier.statusBarsPadding())
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (sessionToEdit != null && user is AdminSubclass) {
            val oldRoomName = sessionToEdit?.rooms?.keys?.firstOrNull() ?: ""
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)) {
                RoomSetupScreen(
                    initialSession = sessionToEdit,
                    existingRooms = allDailyRooms,
                    onAddSession = { updatedSessionClass ->
                        val updatedRoom = updatedSessionClass.rooms.values.first()
                        SessionsClass().updateRoomInSession(
                            dateID = selectedDateStr,
                            oldRoomName = oldRoomName,
                            newRoomName = updatedRoom.roomName,
                            newStartTime = updatedRoom.startTime,
                            newEndTime = updatedRoom.endTime,
                            newTotalCapacity = updatedRoom.totalCap
                        ) { if (it) { viewModel.refresh(user); closeSubView() } }
                    }
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = closeSubView, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("Cancel")
                }
            }
        } else if (sessionToBook != null) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)) {
                BookingForm(
                    user = user,
                    room = sessionToBook!!,
                    dateID = selectedDateStr,
                    onCancel = closeSubView,
                    onSuccess = { viewModel.refresh(user); closeSubView() }
                )
            }
        } else {
            DashboardContent(
                    user = user,
                    uiState = uiState,
                    dailySessions = dailySessions,
                    roomsNeedingProctors = roomsNeedingProctors,
                    notificationUnreadCount = notificationUnreadCount,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onOpenMenu = onOpenMenu,
                    onNavigateToCoverage = onNavigateToCoverage,
                    onNavigateToLiveBooking = onNavigateToLiveBooking,
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onNavigateToMyBookings = onNavigateToMyBookings,
                    onNavigateToProctorAvailability = onNavigateToProctorAvailability,
                    onNavigateToSummary = onNavigateToSummary,
                    onBook = { sessionToBook = it },
                    onEdit = { sessionToEdit = it },
                    onRefresh = { viewModel.refresh(user) },
                    onApprove = { userToApprove = it; showApproveDialog = true },
                    onDecline = { userToReject = it; showRejectDialog = true }
                )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun DashboardContent(
    user: UserMembersClass,
    uiState: DashboardUiState,
    dailySessions: List<SessionsClass>,
    roomsNeedingProctors: Int,
    notificationUnreadCount: Int = 0,
    onNavigateToNotifications: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onNavigateToCoverage: () -> Unit,
    onNavigateToLiveBooking: (String) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToMyBookings: () -> Unit,
    onNavigateToProctorAvailability: () -> Unit,
    onNavigateToSummary: () -> Unit,
    onBook: (RoomData) -> Unit,
    onEdit: (SessionsClass) -> Unit,
    onRefresh: () -> Unit,
    onApprove: (UserMembersClass) -> Unit,
    onDecline: (UserMembersClass) -> Unit
) {
    val today = LocalDate.now()
    val allRoomsToday = remember(dailySessions) { dailySessions.flatMap { s -> s.rooms.values.map { s to it } } }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Improved async receipt check
    var bookingsToReceipt by remember { mutableStateOf<List<BookingsClass>>(emptyList()) }
    var bookingsToPay by remember { mutableStateOf<List<BookingsClass>>(emptyList()) }

    LaunchedEffect(uiState.userBookings) {
        val total = uiState.userBookings.size
        if (total == 0) {
            bookingsToReceipt = emptyList()
            bookingsToPay = emptyList()
            return@LaunchedEffect
        }
        
        val receiptList = mutableListOf<BookingsClass>()
        val payList = mutableListOf<BookingsClass>()
        var processed = 0

        uiState.userBookings.forEach { bId ->
            val b = BookingsClass()
            b.getBooking(bId) { success ->
                if (success) {
                    if (user.thisRole == 1 && b.status == 5) {
                        receiptList.add(b)
                    } else if (user.thisRole == 3 && b.status == 1) {
                        payList.add(b)
                    }
                }
                processed++
                if (processed == total) {
                    bookingsToReceipt = receiptList.toList()
                    bookingsToPay = payList.toList()
                }
            }
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp + navBarBottom)
    ) {
        // --- Information Hero Section with theme-aware gradient ---
        item {
            val heroContent = DuckGradients.heroContentColor()
            val heroContentSecondary = DuckGradients.heroContentColorSecondary()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = DuckGradients.heroGradient(),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(top = 80.dp, start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToUserProfile(user.thisUID) }
                    ) {
                        ProfileAvatar(profilePictureUrl = user.profilePictureUrl, name = user.thisUserName, size = 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hello,",
                                style = MaterialTheme.typography.bodyLarge,
                                color = heroContentSecondary
                            )
                            Text(
                                text = user.thisUserName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = heroContent
                            )
                        }
                        // Notification bell + menu inside hero
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            NotificationBell(
                                unreadCount = notificationUnreadCount,
                                onClick = onNavigateToNotifications,
                                tint = heroContent
                            )
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Menu", tint = heroContent)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Stats inside Hero
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HeroStatItem("Level", user.level.toString(), Icons.Default.TrendingUp, Modifier.weight(1f))
                        HeroStatItem("Points", user.points.toString(), Icons.Default.Token, Modifier.weight(1f))
                        
                        val streakValue = if (user.dailyStreak > 0) "${user.dailyStreak}" else "0"
                        HeroStatItem("Streak", streakValue, Icons.Default.Whatshot, Modifier.weight(1f), iconColor = if (user.dailyStreak > 0) Color(0xFFFF5722) else heroContent)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // --- Quick Stats for Role ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rooms = dailySessions.flatMap { it.rooms.values }
                    if (uiState.isLoading) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(80.dp).clip(MaterialTheme.shapes.large).shimmerEffect())
                        }
                    } else {
                        when (user.thisRole) {
                            0 -> {
                                StatCard(stringResource(R.string.rooms_active), rooms.size.toString(), Modifier.weight(1f), icon = Icons.Default.MeetingRoom, onClick = onNavigateToCoverage)
                                StatCard(stringResource(R.string.bookings_total), rooms.sumOf { it.bookings.size }.toString(), Modifier.weight(1f), icon = Icons.Default.Book, onClick = onNavigateToMyBookings)
                            }
                            3 -> {
                                val myRoomsToday = rooms.filter { r -> r.bookings.any { it in uiState.userBookings } }
                                val exams = myRoomsToday.size
                                StatCard("Today's Exams", exams.toString(), Modifier.fillMaxWidth(), icon = Icons.Default.School, onClick = onNavigateToMyBookings)
                            }
                            else -> {
                                val assignmentsCount = rooms.count { it.assignedProctors.contains(user.thisUID) }
                                StatCard(stringResource(R.string.assignments), assignmentsCount.toString(), Modifier.weight(1f), icon = Icons.Default.Badge, onClick = onNavigateToProctorAvailability)
                                if (user.thisRole == 1) {
                                    val bookingsCount = rooms.sumOf { r -> r.bookings.count { it in uiState.userBookings } }
                                    StatCard(stringResource(R.string.bookings), bookingsCount.toString(), Modifier.weight(1f), icon = Icons.Default.Bookmark, onClick = onNavigateToMyBookings)
                                }
                            }
                        }
                    }
                }

                // --- Action Alerts ---
                Spacer(Modifier.height(8.dp))
                
                if (user.thisRole == 1 && bookingsToReceipt.isNotEmpty()) {
                    AlertCard(
                        title = "${bookingsToReceipt.size} Exam(s) Ready for Receipt",
                        desc = "The proctor has dropped off these exams. Tap to confirm.",
                        icon = Icons.Default.MoveToInbox,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = {
                            val firstReceipt = bookingsToReceipt.first()
                            if (firstReceipt.sessionDateID == today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
                                scope.launch { listState.animateScrollToItem(index = 3) }
                            } else {
                                onNavigateToLiveBooking(firstReceipt.bookingID)
                            }
                        }
                    )
                } else if (user.thisRole == 3 && bookingsToPay.isNotEmpty()) {
                    AlertCard(
                        title = "Payment Required",
                        desc = "You have ${bookingsToPay.size} pending fee(s). Complete payment to confirm.",
                        icon = Icons.Default.Payment,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = { onNavigateToLiveBooking(bookingsToPay.first().bookingID) }
                    )
                } else if (user.thisRole == 0 && roomsNeedingProctors > 0) {
                    AlertCard(
                        title = "System Alert",
                        desc = "$roomsNeedingProctors rooms still need proctors today!",
                        icon = Icons.Default.PriorityHigh,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = onNavigateToCoverage
                    )
                }


                // --- Todays Agenda ---
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.todays_agenda), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (uiState.isLoading) {
            items(3) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(100.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
            }
        } else {
            if (dailySessions.isEmpty() && (user.thisRole != 0 || uiState.pendingUsers.isEmpty())) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_sessions_scheduled), color = Color.Gray)
                    }
                }
            } else {
                item { Spacer(Modifier.height(4.dp)) }
                
                if (user.thisRole == 1 || user.thisRole == 3) { 
                    val myBookingsItems = allRoomsToday.flatMap { (session, roomData) ->
                        roomData.bookings
                            .filter { it in uiState.userBookings }
                            .map { bId -> Triple(session, roomData, bId) }
                    }

                    if (myBookingsItems.isNotEmpty()) {
                        item { PaddingHorizontal { SectionHeader(stringResource(R.string.my_bookings_today)) } }
                        items(myBookingsItems) { (session, roomData, bId) ->
                            PaddingHorizontal {
                                AnimatedAgendaItem {
                                    DashboardSessionItem(user, session, roomData, isBookedByMe = true, isProctoringByMe = roomData.assignedProctors.contains(user.thisUID), isPastDate = false, uiState.userBookings,
                                        bookingID = bId,
                                        onBook = { onBook(roomData) },
                                        onEdit = { onEdit(session) },
                                        onRefresh = onRefresh,
                                        onNavigateToLiveBooking = onNavigateToLiveBooking
                                    )
                                }
                            }
                        }
                    }

                    if (user.thisRole == 1) { 
                        val myAssignments = allRoomsToday.filter { it.second.assignedProctors.contains(user.thisUID) }
                        if (myAssignments.isNotEmpty()) {
                            item { PaddingHorizontal { SectionHeader(stringResource(R.string.my_proctoring_assignments)) } }
                            items(myAssignments) { (session, roomData) ->
                                PaddingHorizontal {
                                    AnimatedAgendaItem {
                                        DashboardSessionItem(user, session, roomData, isBookedByMe = false, isProctoringByMe = true, isPastDate = false, uiState.userBookings,
                                            onBook = { onBook(roomData) },
                                            onEdit = { onEdit(session) },
                                            onRefresh = onRefresh,
                                            onNavigateToLiveBooking = onNavigateToLiveBooking
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (user.thisRole == 2) { 
                    val myAssignments = allRoomsToday.filter { it.second.assignedProctors.contains(user.thisUID) }
                    if (myAssignments.isNotEmpty()) {
                        items(myAssignments) { (session, roomData) ->
                            PaddingHorizontal {
                                AnimatedAgendaItem {
                                    DashboardSessionItem(user, session, roomData, isBookedByMe = false, isProctoringByMe = true, isPastDate = false, uiState.userBookings,
                                        onRefresh = onRefresh,
                                        onNavigateToLiveBooking = onNavigateToLiveBooking
                                    )
                                }
                            }
                        }
                    }
                } else if (user.thisRole == 0) { 
                    items(allRoomsToday) { (session, roomData) ->
                        val isBookedByMe = roomData.bookings.any { it in uiState.userBookings }
                        val isProctoringByMe = roomData.assignedProctors.contains(user.thisUID)
                        
                        PaddingHorizontal {
                            AnimatedAgendaItem {
                                DashboardSessionItem(user, session, roomData, isBookedByMe, isProctoringByMe, isPastDate = false, uiState.userBookings,
                                    onBook = { onBook(roomData) },
                                    onEdit = { onEdit(session) },
                                    onRefresh = onRefresh,
                                    onNavigateToLiveBooking = onNavigateToLiveBooking
                                )
                            }
                        }
                    }
                }
            }
            
            if (user.thisRole == 0 && uiState.pendingUsers.isNotEmpty()) {
                item { PaddingHorizontal { SectionHeader(stringResource(R.string.pending_approvals)) } }
                items(uiState.pendingUsers) { pUser ->
                    PaddingHorizontal {
                        AnimatedAgendaItem {
                            UserListItem(
                                user = pUser,
                                onApprove = { onApprove(pUser) },
                                onDecline = { onDecline(pUser) }
                            )
                        }
                    }
                }
            }

            // --- My Activity Snapshot ---
            if (uiState.snapshotLoaded) {
                item {
                    PaddingHorizontal {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader("My Activity")
                    }
                }
                item {
                    PaddingHorizontal {
                        DashboardActivitySnapshot(
                            user = user,
                            uiState = uiState,
                            onViewFullSummary = onNavigateToSummary
                        )
                    }
                }
            }

            if (uiState.topUsers.isNotEmpty()) {
                item { 
                    PaddingHorizontal {
                        Spacer(Modifier.height(24.dp))
                        SectionHeader("Hall of Fame") 
                    }
                }
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.topUsers) { topUser ->
                            DashboardLeaderboardItem(topUser, onClick = { onNavigateToUserProfile(topUser.thisUID) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroStatItem(
    label: String, 
    value: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    iconColor: Color = DuckGradients.heroContentColor()
) {
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()
    Column(
        modifier = modifier
            .background(heroContent.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = heroContent)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = heroContentSecondary)
    }
}

@Composable
fun AlertCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PaddingHorizontal(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        content()
    }
}

@Composable
fun DashboardLeaderboardItem(user: UserMembersClass, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(user.profilePictureUrl, user.thisUserName, size = 60.dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(user.level.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(user.thisUserName, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
        Text("${user.experience} XP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnimatedAgendaItem(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
            animationSpec = tween(500),
            initialOffsetY = { it / 2 }
        )
    ) {
        content()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardSessionItem(
    user: UserMembersClass,
    session: SessionsClass,
    roomData: RoomData,
    isBookedByMe: Boolean,
    isProctoringByMe: Boolean,
    isPastDate: Boolean,
    userBookings: List<String>,
    bookingID: String? = null,
    onBook: () -> Unit = {},
    onEdit: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onNavigateToLiveBooking: (String) -> Unit = {}
) {
    var bookingObj by remember { mutableStateOf<BookingsClass?>(null) }
    var otherName by remember { mutableStateOf<String?>(null) }
    var otherLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(roomData, userBookings, bookingID) {
        val targetBookingID = bookingID ?: roomData.bookings.firstOrNull { it in userBookings }
        if (targetBookingID != null) {
            val b = BookingsClass()
            b.getBooking(targetBookingID) { success ->
                if (success) {
                    bookingObj = b
                    val other = UserMembersClass()
                    val otherUID = if (user.thisRole == 3) b.facultyUID else b.studentUID
                    otherLabel = if (user.thisRole == 3) "Faculty" else "Student"
                    other.getUserData(otherUID) { if (it) otherName = other.thisUserName }
                }
            }
        }
    }

    DetailedSessionItem(
        session = SessionsClass(dateID = session.dateID, rooms = mutableMapOf(roomData.roomName to roomData)),
        userRole = if (isBookedByMe) 3 else user.thisRole,
        examName = bookingObj?.examName,
        otherUserName = otherName,
        otherUserLabel = otherLabel,
        bookingData = bookingObj,
        onBook = onBook,
        onEdit = onEdit,
        onBookingDeleted = onRefresh,
        isPastDate = isPastDate,
        onNavigateToLiveBooking = onNavigateToLiveBooking,
        isAssignedToMe = isProctoringByMe
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun DashboardContentPreview() {
    val dummyUser = StudentSubclass().apply { 
        thisUserName = "Nima" 
        thisRole = 3
    }
    val dummyState = DashboardUiState(
        isLoading = false,
        userBookings = listOf("b1"),
        weekSessions = listOf(
            SessionsClass(
                dateID = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                rooms = mutableMapOf("Room A" to RoomData(roomName = "Room A", startTime = "10:00", endTime = "12:00", bookings = mutableListOf("b1")))
            )
        )
    )
    MaterialTheme {
        DashboardContent(
            user = dummyUser,
            uiState = dummyState,
            dailySessions = dummyState.weekSessions,
            roomsNeedingProctors = 0,
            onNavigateToCoverage = {},
            onNavigateToLiveBooking = {},
            onNavigateToUserProfile = {},
            onNavigateToMyBookings = {},
            onNavigateToProctorAvailability = {},
            onNavigateToSummary = {},
            onBook = {},
            onEdit = {},
            onRefresh = {},
            onApprove = {},
            onDecline = {}
        )
    }
}

// ══════════════════════════════════════════════════════════
//  Dashboard "My Activity" Snapshot Widget
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardActivitySnapshot(
    user: UserMembersClass,
    uiState: DashboardUiState,
    onViewFullSummary: () -> Unit
) {
    val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    val currentMonth = LocalDate.now().monthValue - 1

    val roleSubtitle = when (user.thisRole) {
        0 -> "Platform overview & proctor coverage"
        1 -> "Your bookings & proctoring summary"
        2 -> "Your assignments & hours logged"
        3 -> "Your exams & booking history"
        else -> "Your activity summary"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewFullSummary() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── Header: icon + description + View All ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        roleSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        "Details →",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Divider ──
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            Spacer(Modifier.height(16.dp))

            // ── Role-specific content ──
            when (user.thisRole) {
                0 -> AdminSnapshotContent(uiState)
                1 -> FacultySnapshotContent(uiState, months, currentMonth)
                2 -> ProctorSnapshotContent(uiState)
                3 -> StudentSnapshotContent(uiState, months, currentMonth)
            }
        }
    }
}

@Composable
private fun AdminSnapshotContent(uiState: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SnapshotKPI("Bookings", uiState.totalBookingsAll.toString(), Color(0xFF2196F3), Icons.Default.Book, Modifier.weight(1f))
        SnapshotKPI("Completed", uiState.completedBookingsAll.toString(), Color(0xFF4CAF50), Icons.Default.CheckCircle, Modifier.weight(1f))
        SnapshotKPI("Users", uiState.totalUsersAll.toString(), Color(0xFF9C27B0), Icons.Default.People, Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    // Coverage bar
    val rate = uiState.proctorCoverageRate
    val coverageColor = if (rate >= 80f) Color(0xFF4CAF50) else if (rate >= 50f) Color(0xFFFFA726) else Color(0xFFEF5350)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = coverageColor.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = coverageColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Proctor Coverage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text("${rate.toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = coverageColor)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (rate / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = coverageColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun FacultySnapshotContent(uiState: DashboardUiState, months: List<String>, currentMonth: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SnapshotKPI("Bookings", uiState.myTotalBookings.toString(), Color(0xFF2196F3), Icons.Default.Book, Modifier.weight(1f))
        SnapshotKPI("Completed", uiState.myCompletedBookings.toString(), Color(0xFF4CAF50), Icons.Default.CheckCircle, Modifier.weight(1f))
        if (uiState.myProctorAssignments > 0) {
            SnapshotKPI("Proctored", uiState.myProctorAssignments.toString(), Color(0xFF7E57C2), Icons.Default.SupervisorAccount, Modifier.weight(1f))
        } else {
            SnapshotKPI("Active", uiState.myActiveBookings.toString(), Color(0xFFFFA726), Icons.Default.Schedule, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(16.dp))
    MiniMonthlyChart(uiState.myBookingsByMonth, months, currentMonth)
}

@Composable
private fun ProctorSnapshotContent(uiState: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SnapshotKPI("Assigned", uiState.myProctorAssignments.toString(), Color(0xFF7E57C2), Icons.Default.Assignment, Modifier.weight(1f))
        SnapshotKPI("Hours", String.format("%.1f", uiState.myProctorHours), Color(0xFFFFA726), Icons.Default.Timer, Modifier.weight(1f))
        SnapshotKPI("Active", uiState.myActiveBookings.toString(), Color(0xFF2196F3), Icons.Default.Schedule, Modifier.weight(1f))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun StudentSnapshotContent(uiState: DashboardUiState, months: List<String>, currentMonth: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SnapshotKPI("Exams", uiState.myTotalBookings.toString(), Color(0xFF2196F3), Icons.Default.School, Modifier.weight(1f))
        SnapshotKPI("Completed", uiState.myCompletedBookings.toString(), Color(0xFF4CAF50), Icons.Default.CheckCircle, Modifier.weight(1f))
        SnapshotKPI("Active", uiState.myActiveBookings.toString(), Color(0xFFFFA726), Icons.Default.Schedule, Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    MiniMonthlyChart(uiState.myBookingsByMonth, months, currentMonth)
}

@Composable
private fun SnapshotKPI(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MiniMonthlyChart(bookingsByMonth: List<Int>, months: List<String>, currentMonth: Int) {
    val maxVal = bookingsByMonth.max().coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Monthly Activity",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                bookingsByMonth.forEachIndexed { i, v ->
                    val barHeight = if (v > 0) ((v.toFloat() / maxVal) * 38).dp.coerceAtLeast(4.dp) else 3.dp
                    val barColor = when {
                        i == currentMonth -> MaterialTheme.colorScheme.primary
                        v > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                months.forEachIndexed { i, m ->
                    Text(
                        text = m,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 8.sp,
                        color = if (i == currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = if (i == currentMonth) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

