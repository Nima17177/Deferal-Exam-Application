package com.example.duckduckdefer.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.components.*
import com.example.duckduckdefer.screens.AdminScreens.RoomSetupScreen
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DetailedWeeklySessionsScreen(
    user: UserMembersClass,
    isMyBookingsMode: Boolean = false,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onNavigateToLiveBooking: (String) -> Unit = {}
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentPivotDate by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var isMonthView by remember { mutableStateOf(false) }

    val displayList = remember { mutableStateListOf<BookingDisplayData>() }
    val sessionsManager = remember { SessionsClass() }
    val dateID = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val context = LocalContext.current

    var isAddingSession by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<SessionsClass?>(null) }
    var sessionToBook by remember { mutableStateOf<RoomData?>(null) }
    var bookingToEdit by remember { mutableStateOf<BookingDisplayData?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    // Optional filters for Faculty
    var filterRoomName by remember { mutableStateOf("") }
    var filterStartTime by remember { mutableStateOf("") }
    var filterEndTime by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }

    val filteredDisplayList = remember(displayList.size, refreshTrigger, filterRoomName, filterStartTime, filterEndTime, isMyBookingsMode) {
        if (isMyBookingsMode) {
            displayList.toList()
        } else {
            displayList.filter { item ->
                val room = item.session.rooms.values.firstOrNull() ?: return@filter false
                
                val matchesRoom = filterRoomName.isBlank() || room.roomName.contains(filterRoomName, ignoreCase = true)
                
                val matchesStart = if (filterStartTime.isBlank()) true else {
                    val roomStart = SessionsClass.parseTime(room.startTime)
                    val filterStart = SessionsClass.parseTime(filterStartTime)
                    !roomStart.isBefore(filterStart)
                }
                
                val matchesEnd = if (filterEndTime.isBlank()) true else {
                    val roomEnd = SessionsClass.parseTime(room.endTime)
                    val filterEnd = SessionsClass.parseTime(filterEndTime)
                    !roomEnd.isAfter(filterEnd)
                }
                
                matchesRoom && matchesStart && matchesEnd
            }
        }
    }
    
    var showDeleteRoomDialog by remember { mutableStateOf<String?>(null) }
    
    val calendarDayStats = remember { mutableStateMapOf<LocalDate, DayStats>() }
    val userBookings = remember { mutableStateListOf<String>() }

    val handleBack = {
        when {
            isAddingSession -> isAddingSession = false
            sessionToEdit != null -> sessionToEdit = null
            sessionToBook != null -> sessionToBook = null
            bookingToEdit != null -> bookingToEdit = null
            else -> onBack()
        }
    }

    BackHandler(enabled = isAddingSession || sessionToEdit != null || sessionToBook != null || bookingToEdit != null) {
        handleBack()
    }

    // Load user bookings once
    LaunchedEffect(user, refreshTrigger) {
        val onBookingsLoaded: (List<String>) -> Unit = { bookings ->
            userBookings.clear()
            userBookings.addAll(bookings)
        }
        if (user is FacultySubclass) {
            user.getMyBookings(onBookingsLoaded)
        } else if (user is StudentSubclass) {
            user.getMyBookings(onBookingsLoaded)
        }
    }

    // Calculate calendar stats
    LaunchedEffect(currentPivotDate, isMonthView, refreshTrigger, userBookings.size) {
        val startDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.firstDayOfMonth()) else currentPivotDate
        val endDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.lastDayOfMonth()) else currentPivotDate.plusDays(6)
        
        sessionsManager.getSessionsInRange(
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ) { sessions ->
            calendarDayStats.clear()
            
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val sessionForDate = sessions.find { it.dateID == curr.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                calendarDayStats[curr] = (sessionForDate ?: SessionsClass()).calculateDayStats(
                    date = curr,
                    userUID = user.thisUID,
                    userRole = user.thisRole,
                    userBookings = userBookings
                )
                curr = curr.plusDays(1)
            }
        }
    }

    // Load sessions for the selected date
    LaunchedEffect(selectedDate, refreshTrigger) {
        displayList.clear()
        if (isMyBookingsMode) {
            val getBookings: ((List<String>) -> Unit) -> Unit = { callback ->
                if (user is StudentSubclass) user.getMyBookings(callback)
                else if (user is FacultySubclass) user.getMyBookings(callback)
                else callback(emptyList()) 
            }

            getBookings { bookingIDs ->
                sessionsManager.getSession(dateID) { success ->
                    if (success) {
                        bookingIDs.forEach { bId ->
                            val booking = BookingsClass()
                            booking.getBooking(bId) { bSuccess ->
                                if (bSuccess && booking.sessionDateID == dateID) {
                                    val otherUser = UserMembersClass()
                                    val otherUID = if (user.thisRole == 3) booking.facultyUID else booking.studentUID
                                    val otherLabel = if (user.thisRole == 3) "Faculty" else "Student"
                                    
                                    otherUser.getUserData(otherUID) { uSuccess ->
                                        if (uSuccess) {
                                            val room = sessionsManager.rooms[booking.roomName]
                                            if (room != null) {
                                                displayList.add(BookingDisplayData(
                                                    session = SessionsClass(dateID = dateID, rooms = mutableMapOf(booking.roomName to room)),
                                                    examName = booking.examName,
                                                    otherUserName = otherUser.thisUserName,
                                                    otherUserLabel = otherLabel,
                                                    bookingData = booking
                                                ))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            sessionsManager.getSession(dateID) { success ->
                if (success) {
                    sessionsManager.rooms.forEach { (roomName, roomData) ->
                        displayList.add(BookingDisplayData(
                            session = SessionsClass(dateID = dateID, rooms = mutableMapOf(roomName to roomData))
                        ))
                    }
                }
            }
        }
    }

    if (showDeleteRoomDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteRoomDialog = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete the session room '$showDeleteRoomDialog'? This will remove the room from the schedule for this date.") },
            confirmButton = {
                HoldToConfirmButton(
                    text = "Delete",
                    onConfirm = {
                        val roomName = showDeleteRoomDialog!!
                        showDeleteRoomDialog = null
                        sessionsManager.deleteRoomFromSession(dateID, roomName) { success ->
                            if (success) {
                                displayList.removeAll { it.session.rooms.containsKey(roomName) }
                                refreshTrigger++
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRoomDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(modifier = Modifier
                    .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .statusBarsPadding()) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isMyBookingsMode) "My Bookings" else "Weekly Sessions",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    "Exam Center Directory",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = handleBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = heroContent)
                            }
                        },
                        actions = {
                            if (isMyBookingsMode && displayList.isNotEmpty()) {
                                IconButton(onClick = {
                                    displayList.forEach { item ->
                                        item.bookingData?.let { exportBookingToCalendar(context, it) }
                                    }
                                }) {
                                    Icon(Icons.Default.Event, contentDescription = "Export All to Calendar", tint = heroContent)
                                }
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Menu", tint = heroContent)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        floatingActionButton = {
            val isPastDate = selectedDate.isBefore(LocalDate.now())
            if (user.thisRole == 0 && !isAddingSession && sessionToEdit == null && sessionToBook == null && !isMyBookingsMode && !isPastDate) {
                FloatingActionButton(onClick = { isAddingSession = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Session")
                }
            }
        }
    ) { innerPadding ->
        val heroContent = DuckGradients.heroContentColor()
        Box(modifier = Modifier.fillMaxSize()) {
            // FIXED BACKGROUND GRADIENT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = DuckGradients.heroGradient(),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Form overlays as items
                if (isAddingSession && user.thisRole == 0) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Add New Session for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                                style = MaterialTheme.typography.titleMedium,
                                color = heroContent,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            RoomSetupScreen(
                                initialSession = SessionsClass(dateID = dateID),
                                existingRooms = sessionsManager.rooms,
                                onAddSession = { newSessionClass ->
                                    val roomData = newSessionClass.rooms.values.first()
                                    sessionsManager.addRoomToSession(
                                        dateID = dateID,
                                        roomName = roomData.roomName,
                                        startTime = roomData.startTime,
                                        endTime = roomData.endTime,
                                        totalCapacity = roomData.totalCap
                                    ) { result ->
                                        if (result == true) refreshTrigger++
                                    }
                                    isAddingSession = false
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { isAddingSession = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Cancel") }
                        }
                    }
                } else if (sessionToEdit != null && user.thisRole == 0) {
                    item {
                        val oldRoomName = sessionToEdit?.rooms?.keys?.firstOrNull() ?: ""
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Edit Session for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                                style = MaterialTheme.typography.titleMedium,
                                color = DuckGradients.heroContentColor(),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            RoomSetupScreen(
                                initialSession = sessionToEdit,
                                existingRooms = sessionsManager.rooms,
                                onAddSession = { updatedSessionClass ->
                                    val updatedRoom = updatedSessionClass.rooms.values.first()
                                    sessionsManager.updateRoomInSession(
                                        dateID = dateID,
                                        oldRoomName = oldRoomName,
                                        newRoomName = updatedRoom.roomName,
                                        newStartTime = updatedRoom.startTime,
                                        newEndTime = updatedRoom.endTime,
                                        newTotalCapacity = updatedRoom.totalCap
                                    ) { success ->
                                        if (success) refreshTrigger++
                                    }
                                    sessionToEdit = null
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { sessionToEdit = null },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Cancel") }
                        }
                    }
                } else if (sessionToBook != null) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            BookingForm(
                                user = user,
                                room = sessionToBook!!,
                                dateID = dateID,
                                onCancel = { sessionToBook = null },
                                onSuccess = {
                                    sessionToBook = null
                                    refreshTrigger++
                                }
                            )
                        }
                    }
                } else if (bookingToEdit != null) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            BookingForm(
                                user = user,
                                room = bookingToEdit!!.session.rooms.values.first(),
                                dateID = dateID,
                                existingBooking = bookingToEdit!!.bookingData,
                                onCancel = { bookingToEdit = null },
                                onSuccess = {
                                    bookingToEdit = null
                                    refreshTrigger++
                                }
                            )
                        }
                    }
                } else {
                    // CALENDAR AND FILTERS SECTION
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isMonthView) "Month View" else "Week View",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DuckGradients.heroContentColor().copy(alpha = 0.8f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Show Month", style = MaterialTheme.typography.bodySmall, color = DuckGradients.heroContentColor().copy(alpha = 0.8f))
                                    Spacer(Modifier.width(8.dp))
                                    val heroContent = DuckGradients.heroContentColor()
                                    Switch(
                                        checked = isMonthView,
                                        onCheckedChange = { 
                                            isMonthView = it
                                            currentPivotDate = if (it) selectedDate.with(TemporalAdjusters.firstDayOfMonth()) 
                                                               else selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = heroContent,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            uncheckedThumbColor = heroContent.copy(alpha = 0.6f),
                                            uncheckedTrackColor = heroContent.copy(alpha = 0.2f)
                                        )
                                    )
                                }
                            }

                            CalendarPicker(
                                currentPivotDate = currentPivotDate,
                                selectedDates = setOf(selectedDate),
                                onDateSelected = { selectedDate = it },
                                onPrev = { 
                                    currentPivotDate = if (isMonthView) currentPivotDate.minusMonths(1) else currentPivotDate.minusWeeks(1) 
                                },
                                onNext = { 
                                    currentPivotDate = if (isMonthView) currentPivotDate.plusMonths(1) else currentPivotDate.plusWeeks(1) 
                                },
                                isMonthView = isMonthView,
                                dayStats = calendarDayStats
                            )
                        }
                    }

                    // SEARCH FILTERS HEADER
                    if ((user.thisRole == 1 || user.thisRole == 0) && !isMyBookingsMode) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Search Filters",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { showFilters = !showFilters }) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = if (showFilters) "Hide Filters" else "Show Filters",
                                            tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                if (showFilters) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedTextField(
                                                value = filterRoomName,
                                                onValueChange = { filterRoomName = it },
                                                label = { Text("Room Name") },
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                TimeSlotPicker(
                                                    label = "Starts After",
                                                    selectedTime = filterStartTime,
                                                    onTimeSelected = { filterStartTime = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TimeSlotPicker(
                                                    label = "Ends Before",
                                                    selectedTime = filterEndTime,
                                                    onTimeSelected = { filterEndTime = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (filterRoomName.isNotBlank() || filterStartTime.isNotBlank() || filterEndTime.isNotBlank()) {
                                                TextButton(
                                                    onClick = { filterRoomName = ""; filterStartTime = ""; filterEndTime = "" },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) { Text("Clear All Filters", color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isMyBookingsMode) {
                         item {
                            Text(
                                text = "Selected Bookings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                         }
                    } else {
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    // LIST CONTENT
                    if (displayList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (isMyBookingsMode) "No bookings found for this date." 
                                    else "No sessions available for this date.", 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (filteredDisplayList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                                Text("No sessions match your filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(filteredDisplayList) { item ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                                val room = item.session.rooms.values.first()
                                val isPastDate = selectedDate.isBefore(LocalDate.now())
                                
                                DetailedSessionItem(
                                    session = item.session,
                                    userRole = if (isMyBookingsMode) 3 else user.thisRole,
                                    examName = item.examName,
                                    otherUserName = item.otherUserName,
                                    otherUserLabel = item.otherUserLabel,
                                    showCapacity = !isMyBookingsMode,
                                    bookingData = item.bookingData,
                                    onDelete = { showDeleteRoomDialog = room.roomName },
                                    onEdit = {
                                        if (isMyBookingsMode && (user.thisRole == 1 || user.thisRole == 0)) bookingToEdit = item
                                        else sessionToEdit = item.session
                                    },
                                    onBook = { sessionToBook = room },
                                    onBookingDeleted = { refreshTrigger++ },
                                    onNavigateToLiveBooking = onNavigateToLiveBooking,
                                    isPastDate = isPastDate,
                                    isAssignedToMe = room.assignedProctors.contains(user.thisUID)
                                 )
                            }
                        }
                    }
                }
            }
        }
    }
}
