package com.example.duckduckdefer.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.R
import com.example.duckduckdefer.components.CircleVideoPlayer
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.DatabaseL
import kotlinx.coroutines.delay

private fun isMacewanEmail(emailRaw: String): Boolean {
    val email = emailRaw.trim().lowercase()
    return email.endsWith("@mymacewan.ca") || email.endsWith("@macewan.ca")
}

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: () -> Unit
) {
    var uiState by remember { mutableStateOf(RegisterUiState()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Validation
    val emailTrimmed = uiState.email.trim()
    val emailError = emailTrimmed.isNotBlank() && !isMacewanEmail(emailTrimmed)
    val isFacultyEmail = emailTrimmed.lowercase().endsWith("@macewan.ca")
    
    val passwordsMatch = uiState.password == uiState.confirmPassword && uiState.password.isNotBlank()
    
    val hasMinLength = uiState.password.length >= 8
    val hasCapital = uiState.password.any { it.isUpperCase() }
    val hasLowercase = uiState.password.any { it.isLowerCase() }
    val hasNumber = uiState.password.any { it.isDigit() }
    val hasSymbol = uiState.password.any { !it.isLetterOrDigit() }
    val allPasswordRequirementsMet = hasMinLength && hasCapital && hasLowercase && hasNumber && hasSymbol

    if (uiState.showApprovalDialog) {
        ApprovalDialog(onDismiss = {
            uiState = uiState.copy(showApprovalDialog = false)
            onRegistrationSuccess()
        })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DuckGradients.primaryGradient())
    ) {
        // Decorative Slanted Background (Bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .graphicsLayer(rotationZ = 3f, translationY = 60f)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 100.dp))
        )

        // Terms and Conditions Row - Moved outside to prevent slanting and respect bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Checkbox(
                checked = uiState.agreeToTerms,
                onCheckedChange = { uiState = uiState.copy(agreeToTerms = it) },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(60.dp))

            // Back Button and Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToLogin,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create Account",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                
                val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
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

            Spacer(Modifier.height(20.dp))

            // Movie/Video and subtext placed in the middle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircleVideoPlayer(
                    videoResId = R.raw.hi,
                    size = 100
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Join DuckDuckDefer",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DuckGradients.heroContentColor().copy(alpha = 0.15f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Grouped Name Fields
                    StaggeredEntrance(visible = visible, delay = 100) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                CustomLoginField(
                                    value = uiState.firstName,
                                    onValueChange = { uiState = uiState.copy(firstName = it) },
                                    label = "First Name",
                                    icon = Icons.Default.Person
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                CustomLoginField(
                                    value = uiState.lastName,
                                    onValueChange = { uiState = uiState.copy(lastName = it) },
                                    label = "Last Name",
                                    icon = Icons.Default.Person
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))

                    StaggeredEntrance(visible = visible, delay = 150) {
                        CustomLoginField(
                            value = uiState.email,
                            onValueChange = { 
                                val newRole = if (it.trim().lowercase().endsWith("@mymacewan.ca")) 3 else uiState.role
                                uiState = uiState.copy(email = it, role = newRole) 
                            },
                            label = "MacEwan Email",
                            icon = Icons.Default.Email,
                            isError = emailError,
                            supportingText = if (emailError) "Use @mymacewan.ca or @macewan.ca" else null
                        )
                    }
                    
                    // Role Selector for @macewan.ca emails
                    if (isFacultyEmail && !emailError) {
                        Spacer(Modifier.height(12.dp))
                        StaggeredEntrance(visible = visible, delay = 175) {
                            RoleSelector(
                                selectedRole = uiState.role,
                                onRoleChange = { uiState = uiState.copy(role = it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    StaggeredEntrance(visible = visible, delay = 200) {
                        CustomLoginField(
                            value = uiState.password,
                            onValueChange = { uiState = uiState.copy(password = it) },
                            label = "Password",
                            icon = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordToggle = { passwordVisible = !passwordVisible }
                        )
                    }
                    
                    // Password Requirements
                    if (uiState.password.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        PasswordRequirementRow("At least 8 characters", hasMinLength)
                        PasswordRequirementRow("At least one capital letter", hasCapital)
                        PasswordRequirementRow("At least one lowercase letter", hasLowercase)
                        PasswordRequirementRow("At least one number", hasNumber)
                        PasswordRequirementRow("At least one special symbol", hasSymbol)
                    }

                    Spacer(Modifier.height(12.dp))

                    StaggeredEntrance(visible = visible, delay = 250) {
                        CustomLoginField(
                            value = uiState.confirmPassword,
                            onValueChange = { uiState = uiState.copy(confirmPassword = it) },
                            label = "Confirm Password",
                            icon = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = confirmPasswordVisible,
                            onPasswordToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                            isError = uiState.confirmPassword.isNotEmpty() && !passwordsMatch,
                            supportingText = if (uiState.confirmPassword.isNotEmpty() && !passwordsMatch) "Passwords do not match" else null
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Register Button
            val canRegister = uiState.firstName.isNotBlank() && 
                             uiState.lastName.isNotBlank() && 
                             !emailError && 
                             passwordsMatch && 
                             allPasswordRequirementsMet &&
                             uiState.agreeToTerms &&
                             !uiState.isLoading

            StaggeredEntrance(visible = visible, delay = 350) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_button")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (canRegister) 1.1f else 1f,
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
                                .clickable(enabled = canRegister) {
                                    uiState = uiState.copy(isLoading = true, errorMessage = null)
                                    DatabaseL.register(
                                        email = emailTrimmed,
                                        password = uiState.password,
                                        firstName = uiState.firstName.trim(),
                                        lastName = uiState.lastName.trim(),
                                        role = uiState.role
                                    ) { success: Boolean, msg: String? ->
                                        if (success) {
                                            uiState = uiState.copy(isLoading = false, showApprovalDialog = true)
                                        } else {
                                            uiState = uiState.copy(isLoading = false, errorMessage = msg)
                                        }
                                    }
                                },
                            shape = CircleShape,
                            color = if (canRegister) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            shadowElevation = 8.dp
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Register",
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(160.dp))
        }
    }
}

@Composable
fun PasswordRequirementRow(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isMet) 1f else 0.7f)
        )
    }
}

@Composable
private fun RoleSelector(selectedRole: Int, onRoleChange: (Int) -> Unit) {
    Column {
        Text(
            text = "Register as:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == 1, // Faculty
                    onClick = { onRoleChange(1) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                )
                Text(
                    "Faculty", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == 0, // Admin
                    onClick = { onRoleChange(0) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                )
                Text(
                    "Admin", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ApprovalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Don't dismiss on click outside */ },
        title = { Text("Registration Successful") },
        text = {
            Column {
                Text("Your account has been created successfully!")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Note: Your account is currently pending admin approval. You will be able to log in once an administrator approves your request.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Understood")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val agreeToTerms: Boolean = true,
    val role: Int = 3, // Default to Student
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showApprovalDialog: Boolean = false
)
