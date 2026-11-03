package com.example.duckduckdefer.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.duckduckdefer.BiometricAuthManager
import com.example.duckduckdefer.CredentialsManager
import com.example.duckduckdefer.R
import com.example.duckduckdefer.components.CircleVideoPlayer
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.*
import kotlinx.coroutines.delay

@Composable
@Suppress("UNUSED_PARAMETER")
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoginSuccess: (UserMembersClass) -> Unit,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    biometricAuthManager: BiometricAuthManager? = null,
    credentialsManager: CredentialsManager? = null,
    activity: FragmentActivity? = null
) {
    var emailLocal by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf("@mymacewan.ca") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var agreeToTerms by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }
    var resetDialogMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val emailTrimmed = (emailLocal.trim() + selectedDomain)
    val canLogin = emailLocal.isNotBlank() && password.isNotBlank() && agreeToTerms && !isLoading

    // Entrance Animation State
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    val handleLogin = { emailToUse: String, passwordToUse: String ->
        isLoading = true
        errorMessage = null
        DatabaseL.login(emailToUse, passwordToUse) { result ->
            when (result) {
                is DatabaseL.AuthResult.Success -> {
                    credentialsManager?.saveCredentials(emailToUse, passwordToUse)
                    val user = UserMembersClass.create(result.role)
                    user.getUserData(result.uid) { success ->
                        isLoading = false
                        if (success) onLoginSuccess(user)
                        else errorMessage = "Error loading profile."
                    }
                }
                DatabaseL.AuthResult.DeactivatedAccount -> { isLoading = false; errorMessage = "Account deactivated." }
                DatabaseL.AuthResult.RejectedAccount -> { isLoading = false; errorMessage = "Registration rejected." }
                DatabaseL.AuthResult.PendingApproval -> { isLoading = false; errorMessage = "Pending approval." }
                DatabaseL.AuthResult.InvalidCreds -> { isLoading = false; errorMessage = "Invalid email or password." }
                DatabaseL.AuthResult.NetworkError -> { isLoading = false; errorMessage = "Network error. Check connection." }
                else -> { isLoading = false; errorMessage = "An unknown error occurred." }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (credentialsManager?.hasCredentials() == true && biometricAuthManager?.isBiometricAvailable() == true && activity != null) {
            biometricAuthManager.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    val (savedEmail, savedPassword) = credentialsManager.getCredentials()
                    if (savedEmail != null && savedPassword != null) {
                        handleLogin(savedEmail, savedPassword)
                    }
                },
                onError = { _, _ -> },
                onFailed = { }
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Password Reset") },
            text = { Text(resetDialogMessage) },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DuckGradients.primaryGradient())
    ) {
        // Slanted Background Decoration (Now separate from content)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .graphicsLayer(rotationZ = 3f, translationY = 60f)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 100.dp))
        )

        // Terms and Conditions Row (Straight and above the Android bottom bar)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // Ensures it's above the home/back buttons
                .padding(bottom = 24.dp), // Positions it nicely within the visible black area
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Checkbox(
                checked = agreeToTerms,
                onCheckedChange = { agreeToTerms = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
            Text(
                text = "I agree with Terms & Conditions!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(80.dp))

            // Header Section with Animation
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -40 }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome!",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Text(
                            text = "Sign in to Continue",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        )
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val logoScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Image(
                        painter = painterResource(id = R.drawable.duck_logo2),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .scale(logoScale)
                            .offset(y = (-15).dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Movie/Video placed in the middle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircleVideoPlayer(
                    videoResId = R.raw.hi,
                    size = 110
                )
            }

            Spacer(Modifier.height(40.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Input Fields Staggered Entrance
            StaggeredEntrance(visible = visible, delay = 100) {
                EmailWithDomainField(
                    localPart = emailLocal,
                    onLocalPartChange = { emailLocal = it.replace("@", "") },
                    selectedDomain = selectedDomain,
                    onDomainToggle = {
                        selectedDomain = if (selectedDomain == "@mymacewan.ca") "@macewan.ca" else "@mymacewan.ca"
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            StaggeredEntrance(visible = visible, delay = 200) {
                CustomLoginField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onPasswordToggle = { passwordVisible = !passwordVisible }
                )
            }

            Spacer(Modifier.height(12.dp))

            StaggeredEntrance(visible = visible, delay = 300) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (biometricAuthManager?.isBiometricAvailable() == true && credentialsManager?.hasCredentials() == true) {
                        IconButton(onClick = {
                            if (activity != null) {
                                biometricAuthManager.showBiometricPrompt(
                                    activity = activity,
                                    onSuccess = {
                                        val (savedEmail, savedPassword) = credentialsManager.getCredentials()
                                        if (savedEmail != null && savedPassword != null) {
                                            handleLogin(savedEmail, savedPassword)
                                        }
                                    },
                                    onError = { _, err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() },
                                    onFailed = { Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }) {
                            Icon(
                                Icons.Default.Fingerprint, 
                                contentDescription = "Biometric Login", 
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Text(
                        text = "Forgot password?",
                        modifier = Modifier
                            .clickable {
                                if (emailLocal.isBlank()) {
                                    errorMessage = "Please enter your email address first."
                                } else {
                                    DatabaseL.resetPassword(email = emailTrimmed) { success: Boolean ->
                                        if (success) {
                                            resetDialogMessage = "A password reset email has been sent. Check your inbox."
                                            showResetDialog = true
                                        } else {
                                            errorMessage = "Failed to send reset email."
                                        }
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            StaggeredEntrance(visible = visible, delay = 400) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        // Pulsing effect for login button
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (canLogin) 1.15f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale)
                                .clickable(enabled = canLogin) {
                                    handleLogin(emailTrimmed, password)
                                },
                            shape = CircleShape,
                            color = if (canLogin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            shadowElevation = 8.dp
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Login",
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            StaggeredEntrance(visible = visible, delay = 500) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Don't have an account? ", color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        text = "Register",
                        modifier = Modifier.clickable { onGoToRegister() },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
            
            Spacer(Modifier.height(160.dp))
        }
    }
}

@Composable
fun StaggeredEntrance(
    visible: Boolean,
    delay: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, delayMillis = delay)) + slideInVertically(tween(600, delayMillis = delay)) { 20 }
    ) {
        content()
    }
}

@Composable
fun CustomLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    isError: Boolean = false,
    supportingText: String? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        trailingIcon = if (isPassword) {
            {
                val visibilityIcon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                IconButton(onClick = onPasswordToggle) {
                    Icon(imageVector = visibilityIcon, contentDescription = null)
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true
    )
}

@Composable
fun EmailWithDomainField(
    localPart: String,
    onLocalPartChange: (String) -> Unit,
    selectedDomain: String,
    onDomainToggle: () -> Unit
) {
    TextField(
        value = localPart,
        onValueChange = onLocalPartChange,
        placeholder = { Text("Username") },
        leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null) },
        trailingIcon = {
            Surface(
                onClick = onDomainToggle,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDomain,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch domain",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true
    )
}

