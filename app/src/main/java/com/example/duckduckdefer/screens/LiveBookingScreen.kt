package com.example.duckduckdefer.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.duckduckdefer.MainActivity
import com.example.duckduckdefer.R
import com.example.duckduckdefer.components.*
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.utils.CampusLocations
import com.example.duckduckdefer.utils.NfcAttendanceManager
import com.example.duckduckdefer.viewmodels.LiveBookingViewModel
import com.example.duckduckdefer.viewmodels.MessagingViewModel
import com.google.android.gms.wallet.*
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LiveBookingScreen(
    bookingID: String,
    currentUser: UserMembersClass,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {}
) {
    val viewModel: LiveBookingViewModel = viewModel()
    val messagingViewModel: MessagingViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var nfcScanMode by remember { mutableStateOf(NfcScanMode.READ) }
    var selectedFullImageUrl by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    // NFC state from MainActivity
    val isNfcAvailable by MainActivity.nfcAvailable.collectAsState()
    val isNfcEnabled by MainActivity.nfcEnabled.collectAsState()

    // Control NFC foreground dispatch (proctor side) and HCE emulation (student side)
    val activity = context as? MainActivity
    LaunchedEffect(showNfcDialog, nfcScanMode) {
        if (showNfcDialog) {
            if (nfcScanMode == NfcScanMode.READ) {
                // Proctor: enable foreground dispatch to read NFC
                activity?.setNfcForegroundActive(true)
            } else {
                // Student: start HCE emulation so phone acts as an NFC tag
                // Use context-aware version so payload survives process recreation
                NfcAttendanceManager.startEmitting(bookingID, currentUser.thisUID, context)
            }
        } else {
            // Dialog closed → stop everything
            activity?.setNfcForegroundActive(false)
            NfcAttendanceManager.stopEmitting(context)
        }
    }

    // Clean up HCE when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            NfcAttendanceManager.stopEmitting(context)
            activity?.setNfcForegroundActive(false)
        }
    }

    // Auto-dismiss the student NFC dialog when attendance is confirmed by the proctor.
    // The booking is a live Firestore listener, so status updates in real time.
    LaunchedEffect(viewModel.booking?.status, showNfcDialog) {
        if (showNfcDialog && nfcScanMode == NfcScanMode.WRITE && (viewModel.booking?.status ?: 0) >= 3) {
            NfcAttendanceManager.stopEmitting(context)
            showNfcDialog = false
            snackbarHostState.showSnackbar("✅ Attendance confirmed! You're checked in.")
        }
    }

    // Listen for NFC intents when the proctor's NFC dialog is showing.
    // Uses resetReplayCache() so stale intents from a previous session don't fire instantly.
    LaunchedEffect(showNfcDialog) {
        if (!showNfcDialog) return@LaunchedEffect
        // Clear any stale replayed intent from a previous NFC session
        MainActivity.nfcIntentFlow.resetReplayCache()
        MainActivity.nfcIntentFlow.collect { nfcIntent ->
            if (!showNfcDialog) return@collect
            if (nfcScanMode == NfcScanMode.READ) {
                // Proctor reading NFC from student's phone (HCE) or physical tag
                // IsoDep I/O must run off the main thread
                val payload = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    NfcAttendanceManager.readTagFromIntent(nfcIntent)
                }
                if (payload != null) {
                    val parsed = NfcAttendanceManager.parseAttendancePayload(payload)
                    if (parsed != null) {
                        val (_, scannedBookingID, scannedStudentUID) = parsed
                        if (scannedBookingID == bookingID && scannedStudentUID == viewModel.booking?.studentUID) {
                            showNfcDialog = false
                            viewModel.updateStatus(3, currentUser.thisUserName) { success ->
                                scope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("✅ Attendance confirmed via NFC!")
                                    } else {
                                        snackbarHostState.showSnackbar("⚠️ NFC read succeeded but status update failed. Try the manual check-in button.")
                                    }
                                }
                            }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Invalid NFC tag for this session.") }
                        }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Unrecognized NFC tag.") }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("No attendance data found. Try again.") }
                }
            }
            // Note: WRITE mode no longer needs intent handling — HCE handles it automatically
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is required to scan QR codes") }
        }
    }

    LaunchedEffect(bookingID) {
        viewModel.startListening(bookingID)
    }

    val booking = viewModel.booking
    val studentObj = viewModel.studentObj
    val facultyObj = viewModel.facultyObj
    val proctorsWithTimes = viewModel.proctorsWithTimes
    val isLoading = viewModel.isLoading

    if (showEditDialog && booking != null) {
        Dialog(onDismissRequest = { showEditDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        val dummyRoom = RoomData(roomName = booking.roomName, startTime = booking.startTime, endTime = booking.endTime)
                        BookingForm(
                            user = currentUser,
                            room = dummyRoom,
                            dateID = booking.sessionDateID,
                            existingBooking = booking,
                            onCancel = { showEditDialog = false },
                            onSuccess = { 
                                showEditDialog = false
                                viewModel.startListening(bookingID) // Refresh
                            }
                        )
                    }
                    // Floating close button over the hero header
                    IconButton(
                        onClick = { showEditDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DuckGradients.heroContentColor())
                    }
                }
            }
        }
    }

    if (showPaymentDialog && booking != null) {
        PremiumPaymentDialog(
            fee = 25.0,
            onDismiss = { showPaymentDialog = false },
            onPaymentComplete = {
                viewModel.updateStatus(2, currentUser.thisUserName) { success ->
                    if (success) {
                        showPaymentDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Payment successful! Deferral fee paid.") }
                    }
                }
            },
            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Session Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (booking != null && (booking.status == 3 || booking.status == 4)) {
                            LiveIndicator()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (booking != null) {
                        if (currentUser.thisRole == 0 || (currentUser.thisRole == 1 && booking.facultyUID == currentUser.thisUID)) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Booking")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLoading && booking == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else if (booking == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Booking not found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    BookingInfoCard(
                        booking = booking,
                        student = studentObj,
                        faculty = facultyObj,
                        proctorsWithTimes = proctorsWithTimes,
                        currentUser = currentUser,
                        messagingViewModel = messagingViewModel,
                        onNavigateToChat = onNavigateToChat
                    )
                }

                item {
                    Column {
                        Text(
                            "PROGRESS TRACKER", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            BookingProgressBar(currentStatus = booking.status)
                        }
                    }
                }

                if (booking.specialInstructions.isNotBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Notes, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Proctor Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(booking.specialInstructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    AttendanceSection(
                        booking = booking,
                        currentUser = currentUser,
                        isNfcAvailable = isNfcAvailable,
                        isNfcEnabled = isNfcEnabled,
                        onNfcTap = { mode ->
                            nfcScanMode = mode
                            showNfcDialog = true
                        },
                        onScanClick = {
                            val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                showScanner = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onPayClick = { showPaymentDialog = true }
                    )
                }

                item {
                    ActionButtons(booking, currentUser, viewModel)
                }

                if (currentUser.thisRole != 3) {
                    item {
                        ExamDocumentCard(booking, context)
                    }
                }

                item {
                    IncidentHeader(currentUser) { showIncidentDialog = true }
                }

                val reportsToShow = if (currentUser.thisRole == 3) {
                    booking.incidentReports.filter { it.visibleToStudent }
                } else {
                    booking.incidentReports
                }

                if (reportsToShow.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "No incident reports recorded.", 
                                style = MaterialTheme.typography.bodyMedium, 
                                color = Color.Gray,
                                modifier = Modifier.padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(reportsToShow.sortedByDescending { it.timestamp }) { report ->
                        IncidentReportItem(report, onImageClick = { selectedFullImageUrl = it })
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ACTIVITY HISTORY", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (booking.activityLog.isEmpty()) {
                    item {
                        Text("No activity recorded yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    items(booking.activityLog.sortedByDescending { it.timestamp }) { log ->
                        ActivityLogItem(log)
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showScanner) {
        QRScannerDialog(
            onDismiss = { showScanner = false },
            onCodeScanned = { scannedValue ->
                showScanner = false
                val parts = scannedValue.split(":")
                if (parts.size == 3 && parts[0] == "attendance") {
                    val scannedBookingID = parts[1]
                    val scannedStudentUID = parts[2]
                    
                    if (scannedBookingID == bookingID && scannedStudentUID == booking?.studentUID) {
                        viewModel.updateStatus(3, currentUser.thisUserName) { success ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Attendance confirmed!") }
                            }
                        }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Invalid QR code for this session.") }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Unrecognized QR code.") }
                }
            }
        )
    }

    if (showNfcDialog && booking != null) {
        NfcAttendanceDialog(
            mode = nfcScanMode,
            bookingID = bookingID,
            studentUID = if (nfcScanMode == NfcScanMode.WRITE) currentUser.thisUID else booking!!.studentUID,
            isNfcAvailable = isNfcAvailable,
            isNfcEnabled = isNfcEnabled,
            onNfcSuccess = {
                showNfcDialog = false
            },
            onQrScanned = { scannedValue ->
                showNfcDialog = false
                val parts = scannedValue.split(":")
                if (parts.size == 3 && parts[0] == "attendance") {
                    val scannedBookingID = parts[1]
                    val scannedStudentUID = parts[2]
                    if (scannedBookingID == bookingID && scannedStudentUID == booking?.studentUID) {
                        viewModel.updateStatus(3, currentUser.thisUserName) { success ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("✅ Attendance confirmed via QR!") }
                            }
                        }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Invalid QR code for this session.") }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Unrecognized QR code.") }
                }
            },
            onDismiss = { showNfcDialog = false }
        )
    }

    if (showIncidentDialog) {
        IncidentReportDialog(
            currentUser = currentUser,
            onDismiss = { showIncidentDialog = false },
            onConfirm = { message, imageUrl, visibleToStudent ->
                val newReport = IncidentReport(
                    reporterUID = currentUser.thisUID,
                    reporterName = currentUser.thisUserName,
                    message = message,
                    imageUrl = imageUrl,
                    visibleToStudent = visibleToStudent
                )
                viewModel.addIncidentReport(newReport) { success ->
                    if (success) showIncidentDialog = false
                    else scope.launch { snackbarHostState.showSnackbar("Failed to save report.") }
                }
            },
            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }

    if (selectedFullImageUrl != null) {
        FullScreenImageDialog(
            imageUrl = selectedFullImageUrl!!,
            onDismiss = { selectedFullImageUrl = null }
        )
    }
}

/**
 * Google Pay Integration Logic
 */
object GooglePayUtil {
    fun createPaymentsClient(context: android.content.Context): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()
        return Wallet.getPaymentsClient(context, walletOptions)
    }

    private val baseRequest = JSONObject().apply {
        put("apiVersion", 2)
        put("apiVersionMinor", 0)
    }

    private fun getBaseCardPaymentMethod(): JSONObject {
        return JSONObject().apply {
            put("type", "CARD")
            put("parameters", JSONObject().apply {
                put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                put("allowedCardNetworks", JSONArray(listOf("AMEX", "DISCOVER", "MASTERCARD", "VISA")))
            })
        }
    }

    private fun getCardPaymentMethod(): JSONObject {
        val cardPaymentMethod = getBaseCardPaymentMethod()
        cardPaymentMethod.put("tokenizationSpecification", JSONObject().apply {
            put("type", "PAYMENT_GATEWAY")
            put("parameters", JSONObject().apply {
                put("gateway", "example")
                put("gatewayMerchantId", "exampleGatewayMerchantId")
            })
        })
        return cardPaymentMethod
    }

    fun getIsReadyToPayRequest(): JSONObject {
        return baseRequest.apply {
            put("allowedPaymentMethods", JSONArray().put(getBaseCardPaymentMethod()))
        }
    }

    fun getPaymentDataRequest(price: String): JSONObject {
        return baseRequest.apply {
            put("allowedPaymentMethods", JSONArray().put(getCardPaymentMethod()))
            put("transactionInfo", JSONObject().apply {
                put("totalPrice", price)
                put("totalPriceStatus", "FINAL")
                put("countryCode", "US")
                put("currencyCode", "USD")
            })
            put("merchantInfo", JSONObject().apply {
                put("merchantName", "DuckDuckDefer Exam Services")
            })
        }
    }
}

@Composable
fun PremiumPaymentDialog(fee: Double, onDismiss: () -> Unit, onPaymentComplete: () -> Unit, onShowMessage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val paymentsClient = remember { GooglePayUtil.createPaymentsClient(context) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGooglePayAvailable by remember { mutableStateOf(false) }

    // Collect result from MainActivity flow
    LaunchedEffect(Unit) {
        MainActivity.googlePayResultFlow.collect { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                onPaymentComplete()
            } else {
                isProcessing = false
                if (result.resultCode == AutoResolveHelper.RESULT_ERROR) {
                    val status = AutoResolveHelper.getStatusFromIntent(result.data)
                    onShowMessage("Google Pay Error: ${status?.statusMessage}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val request = IsReadyToPayRequest.fromJson(GooglePayUtil.getIsReadyToPayRequest().toString())
        paymentsClient.isReadyToPay(request).addOnCompleteListener { task ->
            isGooglePayAvailable = task.isSuccessful && task.result == true
        }
    }

    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(24.dp, shape = RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Professional Header with Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    "Secure Checkout",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(Modifier.height(8.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Service Fee", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("Deferral Booking", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "$${String.format(Locale.US, "%.2f", fee)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "CARD INFORMATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { if (it.length <= 16) cardNumber = it },
                        label = { Text("Card Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { if (it.length <= 5) expiry = it },
                            label = { Text("MM/YY") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                        OutlinedTextField(
                            value = cvv,
                            onValueChange = { if (it.length <= 3) cvv = it },
                            label = { Text("CVV") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Payment Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Authentic Google Pay Button
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable(enabled = !isProcessing && isGooglePayAvailable) {
                                    isProcessing = true
                                    val request = PaymentDataRequest.fromJson(GooglePayUtil.getPaymentDataRequest(String.format(Locale.US, "%.2f", fee)).toString())
                                    val task = paymentsClient.loadPaymentData(request)
                                    AutoResolveHelper.resolveTask(task, context as ComponentActivity, 999)
                                },
                            color = if (isGooglePayAvailable) Color.Black else Color(0xFF5F6368),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_google_logo),
                                        contentDescription = "Google",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Pay",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.25.sp
                                    )
                                }
                            }
                        }

                        // Premium Pay Now Button with Gradient
                        val payBrush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF6200EE)))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable(enabled = !isProcessing && cardNumber.length == 16 && expiry.length == 5 && cvv.length == 3) {
                                    isProcessing = true
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onPaymentComplete() }, 2500)
                                },
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.background(payBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
                                } else {
                                    Text("PAY NOW", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss, 
                    modifier = Modifier.padding(top = 20.dp), 
                    enabled = !isProcessing
                ) {
                    Text("Cancel Transaction", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full Screen Incident Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun AttendanceSection(
    booking: BookingsClass,
    currentUser: UserMembersClass,
    isNfcAvailable: Boolean = false,
    isNfcEnabled: Boolean = false,
    onNfcTap: (NfcScanMode) -> Unit = {},
    onScanClick: () -> Unit,
    onPayClick: () -> Unit
) {
    if (booking.status == 1) { // Booked, need payment
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Payment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                if (currentUser.thisRole == 3) { // Student
                    Text(
                        "Confirmation Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Please pay the deferral fee to finalize your booking.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onPayClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Pay Deferral Fee (\$25.00)", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Text(
                        "Awaiting Payment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "The student has not yet paid the deferral fee.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else if (booking.status == 2) { // Paid, need check-in
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentUser.thisRole == 3) { // Student
                    Text(
                        "Check-in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Use NFC or show QR code to the proctor upon arrival.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    // Primary: NFC broadcast button (student phone acts as NFC tag)
                    Button(
                        onClick = { onNfcTap(NfcScanMode.WRITE) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Nfc, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Broadcast NFC / Show QR", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    // QR Code always visible as backup
                    Text(
                        "OR show this QR code directly:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(8.dp).shadow(8.dp, RoundedCornerShape(20.dp))
                    ) {
                        QRCodeGenerator(
                            content = "attendance:${booking.bookingID}:${currentUser.thisUID}",
                            modifier = Modifier.size(200.dp).padding(16.dp)
                        )
                    }
                } else if (currentUser.thisRole in listOf(0, 1, 2)) { // Faculty, Proctor or Admin
                    Text(
                        "Check-in Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Scan the student's NFC tag or QR code to confirm attendance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(16.dp))

                    // Primary: NFC Scan button
                    Button(
                        onClick = { onNfcTap(NfcScanMode.READ) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Nfc, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Scan NFC / QR Code", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Secondary: Direct QR scanner
                    OutlinedButton(
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("QR Scanner Only", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else if (booking.status >= 3) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFE8F5E9),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Attendance Verified", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { 
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.Red)
        )
        Spacer(Modifier.width(6.dp))
        Text("LIVE", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
fun ExamDocumentCard(booking: BookingsClass, context: android.content.Context) {
    if (booking.examDocumentUrl != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            shape = RoundedCornerShape(20.dp),
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(booking.examDocumentUrl))
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Exam Resource", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        booking.examDocumentName ?: "Reference Document", 
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun IncidentHeader(currentUser: UserMembersClass, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "INCIDENT REPORTS", 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        if (currentUser.thisRole <= 2) { // Admin, Faculty, Proctor
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Report Issue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BookingInfoCard(
    booking: BookingsClass,
    student: UserMembersClass?,
    faculty: UserMembersClass?,
    proctorsWithTimes: List<Pair<UserMembersClass, String>> = emptyList(),
    currentUser: UserMembersClass,
    messagingViewModel: MessagingViewModel,
    onNavigateToChat: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = booking.examName, 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Participants Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Student Row
                ParticipantRow(
                    label = "Student",
                    name = student?.thisUserName ?: "Loading...",
                    icon = Icons.Default.AccountCircle,
                    user = student,
                    currentUser = currentUser,
                    messagingViewModel = messagingViewModel,
                    onNavigateToChat = onNavigateToChat
                )
                
                // Faculty Row
                ParticipantRow(
                    label = "Instructor",
                    name = faculty?.thisUserName ?: "Loading...",
                    icon = Icons.Default.School,
                    user = faculty,
                    currentUser = currentUser,
                    messagingViewModel = messagingViewModel,
                    onNavigateToChat = onNavigateToChat
                )

                // Proctor Section
                if (proctorsWithTimes.isNotEmpty()) {
                    proctorsWithTimes.forEach { (proctor, time) ->
                        ParticipantRow(
                            label = "Proctor",
                            name = proctor.thisUserName,
                            icon = Icons.Default.AssignmentInd,
                            user = proctor,
                            currentUser = currentUser,
                            messagingViewModel = messagingViewModel,
                            onNavigateToChat = onNavigateToChat,
                            subtext = if (time.isNotEmpty()) "Assigned: $time" else null
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                InfoChip(icon = Icons.Default.LocationOn, text = "Room ${booking.roomName}", modifier = Modifier.weight(1f))
                InfoChip(icon = Icons.Default.Schedule, text = "${booking.startTime} - ${booking.endTime}", modifier = Modifier.weight(1.2f))
            }

            // ── Get Directions section ───────────────────────────────
            val dirContext = LocalContext.current
            val roomLocation = remember(booking.roomName) {
                CampusLocations.parseRoom(booking.roomName)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            // e.g. "Building 7 – Robbins Health Learning Centre · Floor 2 · Room 25"
            Text(
                text = roomLocation.displayLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = roomLocation.building.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = { CampusLocations.openDirections(dirContext, roomLocation.building) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Get Directions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ParticipantRow(
    label: String,
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    user: UserMembersClass?,
    currentUser: UserMembersClass,
    messagingViewModel: MessagingViewModel,
    onNavigateToChat: (String) -> Unit,
    subtext: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
            if (subtext != null) {
                Text(subtext, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        if (user != null && user.thisUID != currentUser.thisUID) {
            Surface(
                onClick = { /* MessageUserButton logic is handled inside its component */ },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                MessageUserButton(
                    currentUser = currentUser,
                    targetUser = user,
                    viewModel = messagingViewModel,
                    onConversationStarted = onNavigateToChat,
                    buttonStyle = MessageButtonStyle.ICON_ONLY
                )
            }
        }
    }
}

@Composable
fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActionButtons(booking: BookingsClass, currentUser: UserMembersClass, viewModel: LiveBookingViewModel) {
    val role = currentUser.thisRole
    val status = booking.status

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (role <= 2) { // Faculty, Proctor or Admin
            when (status) {
                1 -> ActionButton(text = "Mark as Paid", icon = Icons.Default.Check, onClick = { viewModel.updateStatus(2, currentUser.thisUserName) {} })
                2 -> ActionButton(text = "Check In Student", icon = Icons.AutoMirrored.Filled.Login, onClick = { viewModel.updateStatus(3, currentUser.thisUserName) {} }, color = MaterialTheme.colorScheme.secondary)
                3 -> ActionButton(text = "Finalize Exam", icon = Icons.Default.TaskAlt, onClick = { viewModel.updateStatus(4, currentUser.thisUserName) {} })
                4 -> ActionButton(text = "Drop Off Materials", icon = Icons.Default.Outbox, onClick = { viewModel.updateStatus(5, currentUser.thisUserName) {} })
            }
        }

        if ((role == 1 || role == 0) && status == 5) {
            ActionButton(text = "Confirm Receipt & Close", icon = Icons.Default.Verified, onClick = { viewModel.updateStatus(6, currentUser.thisUserName) {} })
        }

        // Revert Button
        if (role <= 2 && status > 1 && status < 6) {
            OutlinedButton(
                onClick = { viewModel.revertStatus(currentUser.thisUserName) {} },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Revert Status", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.primary) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        elevation = ButtonDefaults.elevatedButtonElevation(2.dp)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
fun ActivityLogItem(log: ActivityLog) {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = sdf.format(Date(log.timestamp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.action, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("by ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(timeStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun IncidentReportItem(report: IncidentReport, onImageClick: (String) -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(report.timestamp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ReportProblem, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(report.reporterName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
                }
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            Text(report.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (report.imageUrl != null) {
                Spacer(Modifier.height(16.dp))
                AsyncImage(
                    model = report.imageUrl,
                    contentDescription = "Incident Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(report.imageUrl) }
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            if (report.visibleToStudent) {
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "VISIBLE TO STUDENT", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun IncidentReportDialog(
    currentUser: UserMembersClass,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Boolean) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    var message by remember { mutableStateOf("") }
    var visibleToStudent by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempCameraUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onShowMessage("Permission granted. Tap Camera again.")
        } else {
            onShowMessage("Camera permission is required.")
        }
    }

    fun createImageUri(): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Incident", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Describe the situation") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(16.dp)
                )
                
                Spacer(Modifier.height(20.dp))
                
                Text("ATTACHMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(8.dp))

                if (selectedImageUri != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImageUri = null }, 
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { 
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    createImageUri()?.let { uri ->
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } ?: onShowMessage("Error creating file.")
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }, 
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, 
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                }

                Surface(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { visibleToStudent = !visibleToStudent }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Checkbox(checked = visibleToStudent, onCheckedChange = { visibleToStudent = it })
                        Text("Visible to Student", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                
                AnimatedVisibility(visible = isUploading) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                        Text("Uploading evidence...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isUploading && message.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    if (selectedImageUri != null) {
                        isUploading = true
                        val storage = FirebaseStorage.getInstance("gs://duckduckdefer.firebasestorage.app")
                        val storageRef = storage.reference.child("incident_images/${UUID.randomUUID()}")
                        
                        storageRef.putFile(selectedImageUri!!)
                            .addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                    onConfirm(message, uri.toString(), visibleToStudent)
                                    isUploading = false
                                }.addOnFailureListener {
                                    isUploading = false
                                    onShowMessage("Failed to get image link")
                                }
                            }
                            .addOnFailureListener { e ->
                                isUploading = false
                                onShowMessage("Upload failed")
                            }
                    } else {
                        onConfirm(message, null, visibleToStudent)
                    }
                }
            ) {
                Text(if (isUploading) "Processing..." else "Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
