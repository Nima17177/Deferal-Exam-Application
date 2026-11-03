package com.example.duckduckdefer.components

import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.widget.NextBookingWidget
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BookingForm(
    user: UserMembersClass,
    room: RoomData,
    dateID: String,
    existingBooking: BookingsClass? = null,
    onCancel: () -> Unit,
    onSuccess: () -> Unit
) {
    var examName by remember { mutableStateOf(existingBooking?.examName ?: "") }
    var specialInstructions by remember { mutableStateOf(existingBooking?.specialInstructions ?: "") }
    
    var studentQuery by remember { mutableStateOf("") }
    var suggestedStudents by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var studentExpanded by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<UserMembersClass?>(null) }
    
    var facultyQuery by remember { mutableStateOf("") }
    var suggestedFaculty by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var facultyExpanded by remember { mutableStateOf(false) }
    var selectedFaculty by remember { mutableStateOf<UserMembersClass?>(null) }

    var startTime by remember { mutableStateOf(existingBooking?.startTime ?: room.startTime) }
    var endTime by remember { mutableStateOf(existingBooking?.endTime ?: room.endTime) }
    
    var isSearchingStudents by remember { mutableStateOf(false) }
    var isSearchingFaculty by remember { mutableStateOf(false) }
    var isBooking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    var studentSearchJob by remember { mutableStateOf("") }
    var facultySearchJob by remember { mutableStateOf("") }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf(existingBooking?.examDocumentName) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isEditing = existingBooking != null
    val headerTitle = if (isEditing) "Edit Booking" else "New Booking"
    val headerSubtitle = if (isEditing) "Modify booking details" else "Schedule an exam session"

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
        uri?.let {
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    selectedFileName = cursor.getString(nameIndex)
                }
            }
        }
    }

    LaunchedEffect(existingBooking) {
        if (existingBooking != null) {
            val student = UserMembersClass()
            student.getUserData(existingBooking.studentUID) { success ->
                if (success) {
                    selectedStudent = student
                    studentQuery = student.thisEmail
                }
            }
            if (user.thisRole == 0) {
                val faculty = UserMembersClass()
                faculty.getUserData(existingBooking.facultyUID) { success ->
                    if (success) {
                        selectedFaculty = faculty
                        facultyQuery = faculty.thisEmail
                    }
                }
            }
        }
    }

    // --- Premium Success Dialog ---
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    "Booking Confirmed!",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    "The booking for '$examName' has been created successfully. Would you like to book this same exam for another student?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedStudent = null
                        studentQuery = ""
                        showSuccessDialog = false
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Repeat for Another", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onSuccess()
                    }
                ) { Text("Finish", fontWeight = FontWeight.Medium) }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════
        // HERO HEADER with gradient
        // ═══════════════════════════════════════
        val heroContent = DuckGradients.heroContentColor()
        val heroContentSecondary = DuckGradients.heroContentColorSecondary()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        DuckGradients.heroGradient(),
                        RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = heroContent.copy(alpha = 0.15f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.EditCalendar else Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = heroContent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = heroContent,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = heroContentSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ═══════════════════════════════════════
            // ROOM INFO CARD - Premium styled
            // ═══════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.MeetingRoom,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                room.roomName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "${SessionsClass.formatTime(room.startTime)} – ${SessionsClass.formatTime(room.endTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AvailabilityIntervals(room = room)
                }
            }

            // ═══════════════════════════════════════
            // EXAM DETAILS SECTION
            // ═══════════════════════════════════════
            BookingFormSectionLabel("Exam Details", Icons.Default.Description)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text("Exam Name") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = specialInstructions,
                        onValueChange = { specialInstructions = it },
                        label = { Text("Proctor Notes / Special Instructions") },
                        placeholder = { Text("e.g., Allow calculator, 1 page of notes…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // ═══════════════════════════════════════
            // PEOPLE SECTION
            // ═══════════════════════════════════════
            BookingFormSectionLabel("People", Icons.Default.People)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SearchDropdown(
                        label = "Student (Email or Name)",
                        value = studentQuery,
                        suggestions = suggestedStudents,
                        expanded = studentExpanded,
                        isSearching = isSearchingStudents,
                        onValueChange = { query ->
                            studentQuery = query
                            selectedStudent = null
                            studentSearchJob = query
                            if (query.length >= 3) {
                                isSearchingStudents = true
                                studentExpanded = true
                                DatabaseL.searchUsersByRole(query, 3) { students: List<UserMembersClass> ->
                                    if (studentSearchJob == query) {
                                        isSearchingStudents = false
                                        suggestedStudents = students
                                        studentExpanded = true
                                    }
                                }
                            } else {
                                studentExpanded = false
                                suggestedStudents = emptyList()
                            }
                        },
                        onSelect = {
                            selectedStudent = it
                            studentQuery = it.thisEmail
                            studentExpanded = false
                        },
                        onDismiss = { studentExpanded = false }
                    )

                    // Selected student chip
                    if (selectedStudent != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedStudent!!.thisUserName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(selectedStudent!!.thisEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { selectedStudent = null; studentQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (user.thisRole == 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SearchDropdown(
                            label = "Faculty (Email or Name)",
                            value = facultyQuery,
                            suggestions = suggestedFaculty,
                            expanded = facultyExpanded,
                            isSearching = isSearchingFaculty,
                            onValueChange = { query ->
                                facultyQuery = query
                                selectedFaculty = null
                                facultySearchJob = query
                                if (query.length >= 3) {
                                    isSearchingFaculty = true
                                    facultyExpanded = true
                                    DatabaseL.searchUsersByRole(query, 1) { faculty: List<UserMembersClass> ->
                                        if (facultySearchJob == query) {
                                            isSearchingFaculty = false
                                            suggestedFaculty = faculty
                                            facultyExpanded = true
                                        }
                                    }
                                } else {
                                    facultyExpanded = false
                                    suggestedFaculty = emptyList()
                                }
                            },
                            onSelect = {
                                selectedFaculty = it
                                facultyQuery = it.thisEmail
                                facultyExpanded = false
                            },
                            onDismiss = { facultyExpanded = false }
                        )

                        // Selected faculty chip
                        if (selectedFaculty != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(selectedFaculty!!.thisUserName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(selectedFaculty!!.thisEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = { selectedFaculty = null; facultyQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // DOCUMENT SECTION (non-student)
            // ═══════════════════════════════════════
            if (user.thisRole != 3) {
                BookingFormSectionLabel("Exam Document", Icons.Default.AttachFile)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selectedFileName != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        selectedFileName!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { selectedFileUri = null; selectedFileName = null },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("application/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedFileName != null) "Replace Document" else "Upload PDF / Word",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // TIME SELECTION SECTION
            // ═══════════════════════════════════════
            BookingFormSectionLabel("Time Slot", Icons.Default.Schedule)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TimeSlotPicker(
                            label = "Start",
                            selectedTime = startTime,
                            onTimeSelected = {
                                startTime = it
                                if (endTime < it) endTime = it
                            },
                            minTime = room.startTime,
                            maxTime = room.endTime,
                            modifier = Modifier.weight(1f)
                        )
                        TimeSlotPicker(
                            label = "End",
                            selectedTime = endTime,
                            onTimeSelected = { endTime = it },
                            minTime = startTime,
                            maxTime = room.endTime,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // ERROR MESSAGE
            // ═══════════════════════════════════════
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // ACTION BUTTONS - Premium styled
            // ═══════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }

                val confirmEnabled = examName.isNotBlank() && studentQuery.isNotBlank() &&
                        (user.thisRole != 0 || facultyQuery.isNotBlank() || user.thisRole == 1) && !isBooking

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isBooking = true
                            errorMessage = null

                            try {
                                if (selectedStudent == null) {
                                    throw IllegalArgumentException("Please select a valid student from the dropdown suggestions.")
                                }
                                if (user.thisRole == 0 && selectedFaculty == null) {
                                    throw IllegalArgumentException("Please select a valid faculty member from the dropdown suggestions.")
                                }

                                val studentUid = selectedStudent!!.thisUID
                                val facultyUid = if (user.thisRole == 0) selectedFaculty!!.thisUID else user.thisUID

                                var docUrl: String? = null
                                var docName: String? = null
                                if (selectedFileUri != null) {
                                    try {
                                        val uploadResult = uploadExamDocument(selectedFileUri!!, selectedFileName, context)
                                        docUrl = uploadResult?.first
                                        docName = uploadResult?.second
                                    } catch (e: Exception) {
                                        throw Exception("Document upload failed: ${e.message}")
                                    }
                                } else {
                                    docUrl = existingBooking?.examDocumentUrl
                                    docName = existingBooking?.examDocumentName
                                }

                                saveBookingWithDocs(
                                    examName = examName,
                                    specialInstructions = specialInstructions,
                                    room = room,
                                    dateID = dateID,
                                    sUid = studentUid,
                                    fUid = facultyUid,
                                    startTime = startTime,
                                    endTime = endTime,
                                    docUrl = docUrl,
                                    docName = docName,
                                    existingBooking = existingBooking,
                                    onSuccess = {
                                        isBooking = false
                                        NextBookingWidget.requestWidgetUpdate(context)
                                        if (existingBooking == null && user.thisRole == 1) {
                                            showSuccessDialog = true
                                        } else {
                                            onSuccess()
                                        }
                                    },
                                    onError = { error ->
                                        isBooking = false
                                        errorMessage = error
                                    }
                                )
                            } catch (e: Exception) {
                                isBooking = false
                                errorMessage = e.message ?: "An unexpected error occurred"
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isBooking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            if (isEditing) "Save Changes" else "Confirm Booking",
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
// Premium Section Label - consistent with app design
// ═══════════════════════════════════════════════════
@Composable
private fun BookingFormSectionLabel(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SearchDropdown(
    label: String,
    value: String,
    suggestions: List<UserMembersClass>,
    expanded: Boolean,
    isSearching: Boolean,
    onValueChange: (String) -> Unit,
    onSelect: (UserMembersClass) -> Unit,
    onDismiss: () -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (expanded) {
                onDismiss()
            } else if (value.length >= 3) {
                onValueChange(value)
            }
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
            trailingIcon = {
                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            if (isSearching) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { },
                    enabled = false
                )
            } else if (suggestions.isEmpty() && value.length >= 3) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { },
                    enabled = false
                )
            } else {
                suggestions.forEach { user ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(user.thisUserName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(user.thisEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = { onSelect(user) }
                    )
                }
            }
        }
    }
}

/**
 * Suspend function to upload exam document to Firebase Storage
 * @return Pair of (downloadUrl, fileName) or null if upload fails
 */
private suspend fun uploadExamDocument(
    fileUri: Uri,
    fileName: String?,
    context: android.content.Context
): Pair<String, String>? = suspendCancellableCoroutine { continuation ->
    try {
        val contentType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(contentType)
            .build()
        
        val storageRef = FirebaseStorage.getInstance("gs://duckduckdefer.firebasestorage.app")
            .reference
            .child("exam_documents/${UUID.randomUUID()}_$fileName")
        
        storageRef.putFile(fileUri, metadata)
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        continuation.resume(Pair(uri.toString(), fileName ?: "exam_document"))
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(Exception("Failed to get download URL: ${e.message}"))
                    }
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(Exception("File upload failed: ${e.message}"))
            }
    } catch (e: Exception) {
        continuation.resumeWithException(e)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun saveBookingWithDocs(
    examName: String,
    specialInstructions: String,
    room: RoomData,
    dateID: String,
    sUid: String,
    fUid: String,
    startTime: String,
    endTime: String,
    docUrl: String?,
    docName: String?,
    existingBooking: BookingsClass? = null,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val booking = existingBooking ?: BookingsClass()
    
    booking.checkStudentConflict(sUid, dateID, startTime, endTime, existingBooking?.bookingID) { conflict ->
        if (conflict) {
            onError("Student is already booked for another session at this time.")
            return@checkStudentConflict
        }

        val onBookingSaved: (Boolean) -> Unit = { success ->
            if (success) {
                onSuccess()
            } else {
                if (existingBooking == null) {
                    onError("Failed to create booking. Room might be full or session doesn't exist.")
                } else {
                    onError("Failed to save booking document")
                }
            }
        }

        if (existingBooking != null) {
            booking.examName = examName
            booking.specialInstructions = specialInstructions
            booking.studentUID = sUid
            booking.facultyUID = fUid
            booking.startTime = startTime
            booking.endTime = endTime
            booking.examDocumentUrl = docUrl
            booking.examDocumentName = docName
            booking.updateBooking(onBookingSaved)
        } else {
            booking.createBooking(
                studentUID = sUid,
                facultyUID = fUid,
                examName = examName,
                roomName = room.roomName,
                startTime = startTime,
                endTime = endTime,
                sessionDateID = dateID,
                examDocumentUrl = docUrl,
                examDocumentName = docName,
                specialInstructions = specialInstructions,
                onComplete = onBookingSaved
            )
        }
    }
}
