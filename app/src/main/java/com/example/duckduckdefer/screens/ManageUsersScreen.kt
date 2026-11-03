package com.example.duckduckdefer.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.HoldToConfirmButton
import com.example.duckduckdefer.components.UserListItem
import com.example.duckduckdefer.components.shimmerEffect
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.AdminSubclass
import com.example.duckduckdefer.useraccountclasses.UserMembersClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    user: AdminSubclass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    var searchResults by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    // Filtering State
    var selectedRoleFilter by remember { mutableStateOf<Int?>(null) } 

    var pendingUsers by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var allFilteredUsers by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var showDeactivateDialog by remember { mutableStateOf(false) }
    var userToDeactivate by remember { mutableStateOf<UserMembersClass?>(null) }
    var showApproveDialog by remember { mutableStateOf(false) }
    var userToApprove by remember { mutableStateOf<UserMembersClass?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var userToReject by remember { mutableStateOf<UserMembersClass?>(null) }
    var showPromoteDialog by remember { mutableStateOf(false) }
    var userToPromote by remember { mutableStateOf<UserMembersClass?>(null) }

    // Fetch Initial Data
    LaunchedEffect(refreshTrigger, selectedRoleFilter) {
        isLoadingUsers = true
        
        // Load pending users first
        getPendingUsers(user) { pending ->
            pendingUsers = pending
            
            // If no search query, load users by filter
            if (searchQuery.isEmpty()) {
                if (selectedRoleFilter != null) {
                    user.getUsersByRole(selectedRoleFilter!!) { results ->
                        allFilteredUsers = results.filter { it.thisUID != user.thisUID }
                        isLoadingUsers = false
                    }
                } else {
                    allFilteredUsers = emptyList()
                    isLoadingUsers = false
                }
            } else {
                isLoadingUsers = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Integrated Header with Gradient
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Column(
                    modifier = Modifier
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "User Directory",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    "Admin Control Center",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = heroContent)
                            }
                        },
                        actions = {
                            IconButton(onClick = { refreshTrigger++ }) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = heroContent)
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, "Menu", tint = heroContent)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    // Search and Filter section perfectly aligned inside the gradient header
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Premium Styled Search Box
                        val heroContent = DuckGradients.heroContentColor()
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (it.length >= 3) {
                                    isSearching = true
                                    user.searchUsers(it) { results ->
                                        searchResults = results.filter { it.thisUID != user.thisUID }
                                        isSearching = false
                                    }
                                } else { 
                                    searchResults = emptyList()
                                    isSearching = false
                                }
                            },
                            label = { Text("Search by name or email...") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = heroContent.copy(alpha = 0.7f)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                        Icon(Icons.Default.Close, null, tint = heroContent.copy(alpha = 0.7f))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = heroContent,
                                unfocusedTextColor = heroContent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = heroContent.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = heroContent.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = heroContent.copy(alpha = 0.12f),
                                unfocusedContainerColor = heroContent.copy(alpha = 0.08f),
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        // Filter Chips with clear contrast
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val roles = listOf("All" to null, "Admin" to 0, "Faculty" to 1, "Student" to 3)
                            roles.forEach { (name, id) ->
                                val isSelected = selectedRoleFilter == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedRoleFilter = id },
                                    label = { 
                                        Text(
                                            name, 
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) heroContent else heroContent.copy(alpha = 0.7f)
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = heroContent) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        containerColor = heroContent.copy(alpha = 0.08f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = heroContent.copy(alpha = 0.15f),
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Main Content Area
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            // 1. Loading State
            if (isSearching || (searchQuery.isEmpty() && isLoadingUsers)) {
                item {
                    Text(
                        "Updating directory...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(4) {
                    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).shimmerEffect())
                }
            } 
            // 2. Main List View (No Search Active)
            else if (searchQuery.isEmpty()) {
                val currentPending = pendingUsers.filter { selectedRoleFilter == null || it.thisRole == selectedRoleFilter }
                val currentUsers = allFilteredUsers.filter { u -> currentPending.none { it.thisUID == u.thisUID } }

                // Show Pending first regardless of anything
                if (currentPending.isNotEmpty()) {
                    item { ManageUsersSectionHeader("Pending Requests", currentPending.size) }
                    itemsIndexed(currentPending) { index, u ->
                        AnimatedUserItem(index) {
                            UserListItem(
                                user = u,
                                onApprove = { userToApprove = u; showApproveDialog = true },
                                onDecline = { userToReject = u; showRejectDialog = true }
                            )
                        }
                    }
                }

                // Show Filtered users next
                if (currentUsers.isNotEmpty()) {
                    item { ManageUsersSectionHeader("Members", currentUsers.size) }
                    itemsIndexed(currentUsers) { index, u ->
                        AnimatedUserItem(index + currentPending.size) {
                            UserListItem(
                                user = u,
                                onDeactivate = { userToDeactivate = u; showDeactivateDialog = true },
                                onPromote = { userToPromote = u; showPromoteDialog = true }
                            )
                        }
                    }
                } else if (currentPending.isEmpty()) {
                    if (selectedRoleFilter == null) {
                        item { ManageUsersEmptyState(Icons.AutoMirrored.Filled.FactCheck, "All new registrations have been handled.") }
                    } else {
                        item { ManageUsersEmptyState(Icons.Default.FilterListOff, "No members found for this category.") }
                    }
                }
            } 
            // 3. Search Results
            else {
                val displayResults = if (selectedRoleFilter == null) searchResults
                                     else searchResults.filter { it.thisRole == selectedRoleFilter }
                
                if (displayResults.isNotEmpty()) {
                    item { ManageUsersSectionHeader("Global Directory Results", displayResults.size) }
                    itemsIndexed(displayResults) { index, u ->
                        AnimatedUserItem(index) {
                            UserListItem(
                                user = u,
                                onDeactivate = { userToDeactivate = u; showDeactivateDialog = true },
                                onPromote = { userToPromote = u; showPromoteDialog = true }
                            )
                        }
                    }
                } else {
                    item { ManageUsersEmptyState(Icons.AutoMirrored.Filled.ManageSearch, "No members found matching \"$searchQuery\"") }
                }
            }
        }
    }

    // --- Confirmation Dialogs ---
    if (showDeactivateDialog && userToDeactivate != null) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false; userToDeactivate = null },
            title = { Text("Deactivate Account") },
            text = { Text("Are you sure you want to disable access for ${userToDeactivate?.thisUserName}?") },
            confirmButton = {
                HoldToConfirmButton(
                    text = "Deactivate",
                    onConfirm = {
                        user.deactivateAccount(userToDeactivate!!) { if (it) refreshTrigger++ }
                        showDeactivateDialog = false
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showDeactivateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showApproveDialog && userToApprove != null) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false; userToApprove = null },
            title = { Text("Approve Registration") },
            text = { Text("Allow ${userToApprove?.thisUserName} to access the platform?") },
            confirmButton = {
                Button(onClick = {
                    user.approveAccount(userToApprove!!) { if (it) refreshTrigger++ }
                    showApproveDialog = false
                }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { showApproveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRejectDialog && userToReject != null) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false; userToReject = null },
            title = { Text("Reject Registration") },
            text = { Text("Permanently decline registration for ${userToReject?.thisUserName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        user.rejectAccount(userToReject!!) { if (it) refreshTrigger++ }
                        showRejectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPromoteDialog && userToPromote != null) {
        AlertDialog(
            onDismissRequest = { showPromoteDialog = false; userToPromote = null },
            title = { Text("Promote to Admin") },
            text = { Text("Are you sure you want to promote ${userToPromote?.thisUserName} to an Administrator? This will grant them full system access.") },
            confirmButton = {
                HoldToConfirmButton(
                    text = "Promote",
                    onConfirm = {
                        user.changeUserRole(userToPromote!!, 0) { if (it) refreshTrigger++ }
                        showPromoteDialog = false
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showPromoteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ManageUsersSectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    ) {
        Text(
            title, 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Badge(containerColor = MaterialTheme.colorScheme.primary) { 
            Text(count.toString(), color = DuckGradients.heroContentColor(), fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun ManageUsersEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }
    
    val alpha by animateFloatAsState(if (startAnimation) 1f else 0f, tween(1000), label = "alpha")
    val scale by animateFloatAsState(if (startAnimation) 1f else 0.8f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

@Composable
fun AnimatedUserItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, delayMillis = index * 40)) +
                slideInVertically(tween(600, delayMillis = index * 40)) { it / 4 }
    ) {
        content()
    }
}

private fun getPendingUsers(user: AdminSubclass, onComplete: (List<UserMembersClass>) -> Unit) {
    user.getPendingAccounts { uids ->
        if (uids.isEmpty()) { 
            onComplete(emptyList())
            return@getPendingAccounts 
        }
        
        val list = mutableListOf<UserMembersClass>()
        var remainingCount = uids.size
        
        // Load all pending users in parallel
        uids.forEach { uid ->
            val userObj = UserMembersClass()
            userObj.getUserData(uid) { success ->
                if (success) {
                    synchronized(list) {
                        list.add(userObj)
                    }
                }
                
                remainingCount--
                // Once all users have been loaded, return the complete list
                if (remainingCount == 0) {
                    onComplete(list)
                }
            }
        }
    }
}
