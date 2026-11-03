package com.example.duckduckdefer.screens.SettingsScreens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.ProfileAvatar
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.ProfileRepository
import com.example.duckduckdefer.useraccountclasses.UserMembersClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    currentUser: UserMembersClass,
    onBack: () -> Unit,
    onProfileUpdated: (UserMembersClass) -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    val context = LocalContext.current
    val profileRepository = remember { ProfileRepository() }

    var profilePictureUrl by remember { mutableStateOf(currentUser.profilePictureUrl) }
    var biography by remember { mutableStateOf(currentUser.biography) }
    var contactEmails by remember { mutableStateOf(currentUser.contactEmails.toMutableList()) }
    var phoneNumbers by remember { mutableStateOf(currentUser.phoneNumbers.toMutableList()) }

    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    var isUploadingImage by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val roleName = when (currentUser.thisRole) {
        0 -> "Administrator"
        1 -> "Faculty Member"
        2 -> "Proctor"
        3 -> "Student"
        else -> "User"
    }

    // Show error snackbar
    LaunchedEffect(uploadError) {
        uploadError?.let {
            snackbarHostState.showSnackbar(it)
            uploadError = null
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingImage = true
            profileRepository.uploadProfilePicture(context, currentUser.thisUID, it) { newUrl ->
                isUploadingImage = false
                if (newUrl != null) {
                    profilePictureUrl = newUrl
                } else {
                    uploadError = "Failed to upload profile picture. Check Firebase Storage rules and try again."
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(
                    modifier = Modifier
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Edit Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Update your public details",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = heroContent
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    isSaving = true
                                    profileRepository.updateProfile(
                                        userId = currentUser.thisUID,
                                        biography = biography,
                                        contactEmails = contactEmails,
                                        phoneNumbers = phoneNumbers,
                                        profilePictureUrl = profilePictureUrl
                                    ) { success ->
                                        isSaving = false
                                        if (success) {
                                            showSaveSuccess = true
                                            val updatedUser = UserMembersClass.create(currentUser.thisRole).apply {
                                                thisUID = currentUser.thisUID
                                                thisEmail = currentUser.thisEmail
                                                thisUserName = currentUser.thisUserName
                                                thisRole = currentUser.thisRole
                                                thisApprovalStatus = currentUser.thisApprovalStatus
                                                this.profilePictureUrl = profilePictureUrl
                                                this.biography = biography
                                                this.contactEmails = contactEmails
                                                this.phoneNumbers = phoneNumbers
                                                fcmToken = currentUser.fcmToken
                                                pushNotificationsEnabled = currentUser.pushNotificationsEnabled
                                                emailNotificationsEnabled = currentUser.emailNotificationsEnabled
                                                themePreference = currentUser.themePreference
                                                fontSizePreference = currentUser.fontSizePreference
                                                contrastPreference = currentUser.contrastPreference
                                                level = currentUser.level
                                                experience = currentUser.experience
                                                points = currentUser.points
                                                badges = currentUser.badges
                                                currentTitle = currentUser.currentTitle
                                                unlockedTitles = currentUser.unlockedTitles
                                                unlockedThemes = currentUser.unlockedThemes
                                            }
                                            onProfileUpdated(updatedUser)
                                        }
                                    }
                                },
                                enabled = !isSaving && !isUploadingImage
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = heroContent
                                    )
                                } else {
                                    Text("Save", color = heroContent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open Menu",
                                    tint = heroContent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .imePadding()
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header (Hero area with avatar)
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = DuckGradients.heroGradient(),
                            shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                        )
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(136.dp)
                                    .border(4.dp, heroContent.copy(alpha = 0.3f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                ProfileAvatar(
                                    profilePictureUrl = profilePictureUrl,
                                    name = currentUser.thisUserName,
                                    size = 120.dp,
                                    onClick = { imagePickerLauncher.launch("image/*") }
                                )
                                
                                if (isUploadingImage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(120.dp),
                                        strokeWidth = 4.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Camera icon overlay
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(40.dp)
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .shadow(6.dp, CircleShape)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(2.dp, Color.White)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change photo",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tap photo to change",
                            style = MaterialTheme.typography.labelMedium,
                            color = heroContentSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .offset(y = (-24).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- Account Info Card (Read-only) ---
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Account Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            ReadOnlyField(Icons.Default.Person, "Full Name", currentUser.thisUserName)
                            Spacer(Modifier.height(12.dp))
                            ReadOnlyField(Icons.Default.Badge, "Role", roleName)
                            Spacer(Modifier.height(12.dp))
                            ReadOnlyField(Icons.Default.Email, "Primary Email", currentUser.thisEmail)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Biography Section ---
                    EditProfileSection(title = "Biography", icon = Icons.Default.EditNote) {
                        OutlinedTextField(
                            value = biography,
                            onValueChange = { if (it.length <= 500) biography = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text("Tell the community about yourself...") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            supportingText = {
                                Text(
                                    "${biography.length}/500 characters",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Contact Details Section ---
                    EditProfileSection(title = "Additional Contact Details", icon = Icons.Default.ContactSupport) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Email Management
                            DynamicListEditor(
                                items = contactEmails,
                                label = "Contact Email",
                                placeholder = "example@email.com",
                                icon = Icons.Default.AlternateEmail,
                                onAdd = { if (it.contains("@")) contactEmails = contactEmails.toMutableList().apply { add(it.trim()) } },
                                onRemove = { index -> contactEmails = contactEmails.toMutableList().apply { removeAt(index) } }
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            
                            // Phone Management
                            DynamicListEditor(
                                items = phoneNumbers,
                                label = "Phone Number",
                                placeholder = "+1 (555) 000-0000",
                                icon = Icons.Default.Phone,
                                onAdd = { phoneNumbers = phoneNumbers.toMutableList().apply { add(it.trim()) } },
                                onRemove = { index -> phoneNumbers = phoneNumbers.toMutableList().apply { removeAt(index) } }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
    }
}

@Composable
fun ReadOnlyField(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EditProfileSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
fun DynamicListEditor(
    items: List<String>,
    label: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    
    Column {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )
            IconButton(
                onClick = { if (textValue.isNotBlank()) { onAdd(textValue); textValue = "" } },
                enabled = textValue.isNotBlank()
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = if (textValue.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}
