package com.example.walkietalkieapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import com.example.walkietalkieapp.auth.Room
import com.example.walkietalkieapp.auth.RoomMember
import com.example.walkietalkieapp.auth.RoomMemberRequest
import com.example.walkietalkieapp.auth.RoomResult
import com.example.walkietalkieapp.auth.SupabaseRoomManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoomsDashboardScreen(
    currentUserId: String,
    currentUsername: String,
    onLogout: () -> Unit,
    onJoinRoom: (String, String) -> Unit // Passes roomCode, roomName
) {
    val coroutineScope = rememberCoroutineScope()
    var myRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var pendingRequests by remember { mutableStateOf<List<RoomMemberRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    // Dialog & Action States for Long Press
    var selectedRoomForAction by remember { mutableStateOf<Room?>(null) }
    var showOwnerActionDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showDestroyDialog by remember { mutableStateOf(false) }
    var showMemberLeaveDialog by remember { mutableStateOf(false) }
    var candidatesForNewOwner by remember { mutableStateOf<List<RoomMember>>(emptyList()) }
    var selectedNewOwner by remember { mutableStateOf<RoomMember?>(null) }
    var isLoadingMembers by remember { mutableStateOf(false) }
    var isActionInProgress by remember { mutableStateOf(false) }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            val roomsResult = SupabaseRoomManager.getMyRooms(currentUserId)
            if (roomsResult is RoomResult.Success) {
                myRooms = roomsResult.data
            }

            val pendingResult = SupabaseRoomManager.getPendingRequests(currentUserId)
            if (pendingResult is RoomResult.Success) {
                pendingRequests = pendingResult.data
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
        com.example.walkietalkieapp.auth.SupabaseRealtimeManager.addListener("dashboard") {
            refreshData()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.example.walkietalkieapp.auth.SupabaseRealtimeManager.removeListener("dashboard")
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0B),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF14161B))
                        .border(1.dp, Color(0xFF262B35), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = currentUsername, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { refreshData() },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1E2126)).border(1.dp, Color(0xFF2E333D), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1E2126)).border(1.dp, Color(0xFF2E333D), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Out", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = Color(0xFF1E2126),
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Join Room")
                }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = Color(0xFF00FF66),
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Room")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp)) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF00FF66), modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                if (pendingRequests.isNotEmpty()) {
                    Text("Pending Join Requests", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn {
                        items(pendingRequests) { req ->
                            PendingRequestItem(
                                req = req,
                                onApprove = {
                                    coroutineScope.launch {
                                        SupabaseRoomManager.approveRequest(req.roomId, req.userId)
                                        refreshData()
                                    }
                                },
                                onDecline = {
                                    coroutineScope.launch {
                                        SupabaseRoomManager.declineRequest(req.roomId, req.userId)
                                        refreshData()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text("My Squads", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (myRooms.isEmpty()) {
                    Text("You aren`t in any squads yet. Create or join one!", color = Color.Gray, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(myRooms) { room ->
                            val isOwner = room.ownerId == currentUserId
                            val roomPendingCount = pendingRequests.count { it.roomId == room.id || it.roomCode == room.code }
                            RoomItem(
                                room = room, 
                                isOwner = isOwner,
                                pendingCount = roomPendingCount,
                                onClick = { onJoinRoom(room.code, room.name) },
                                onLongClick = {
                                    selectedRoomForAction = room
                                    if (isOwner) {
                                        showOwnerActionDialog = true
                                    } else {
                                        showMemberLeaveDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialog 1: Owner Options (Leave & Assign Owner vs Destroy Squad) ──
    if (showOwnerActionDialog && selectedRoomForAction != null) {
        val targetRoom = selectedRoomForAction!!
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showOwnerActionDialog = false },
            containerColor = Color(0xFF1E2126),
            title = {
                Column {
                    Text("Squad Actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(targetRoom.name, color = Color(0xFF00FF66), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    // Option 1: Leave & Assign New Owner
                    Surface(
                        onClick = {
                            showOwnerActionDialog = false
                            showTransferDialog = true
                            isLoadingMembers = true
                            selectedNewOwner = null
                            coroutineScope.launch {
                                val membersRes = SupabaseRoomManager.getApprovedMembers(targetRoom.id)
                                if (membersRes is RoomResult.Success) {
                                    candidatesForNewOwner = membersRes.data.filter { it.userId != currentUserId }
                                }
                                isLoadingMembers = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262B33),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2196F3))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Leave & Assign Owner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Transfer ownership to another member", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    // Option 2: Destroy Squad
                    Surface(
                        onClick = {
                            showOwnerActionDialog = false
                            showDestroyDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262B33),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(Color(0xFFFF5252).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Destroy Squad", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Permanently delete and remove members", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOwnerActionDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // ── Dialog 2: Transfer Ownership & Leave Dialog ──
    if (showTransferDialog && selectedRoomForAction != null) {
        val targetRoom = selectedRoomForAction!!
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showTransferDialog = false },
            containerColor = Color(0xFF1E2126),
            title = {
                Text("Transfer Ownership & Leave", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Select an approved member to become the new owner of \"${targetRoom.name}\":",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoadingMembers) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00FF66))
                        }
                    } else if (candidatesForNewOwner.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF262B33), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Other Members", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "There are no other members in this squad. You can destroy the squad or wait for members to join.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(candidatesForNewOwner) { member ->
                                val isSelected = selectedNewOwner?.userId == member.userId
                                Surface(
                                    onClick = { selectedNewOwner = member },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFF262B33),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF66)) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF00FF66) else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(member.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Approved Member", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (candidatesForNewOwner.isNotEmpty()) {
                    Button(
                        onClick = {
                            val newOwner = selectedNewOwner ?: return@Button
                            isActionInProgress = true
                            coroutineScope.launch {
                                SupabaseRoomManager.transferOwnershipAndLeave(
                                    roomId = targetRoom.id,
                                    currentOwnerId = currentUserId,
                                    newOwnerId = newOwner.userId
                                )
                                showTransferDialog = false
                                isActionInProgress = false
                                refreshData()
                            }
                        },
                        enabled = selectedNewOwner != null && !isActionInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isActionInProgress) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Transfer & Leave", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            showTransferDialog = false
                            showDestroyDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Destroy Squad Instead", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTransferDialog = false },
                    enabled = !isActionInProgress
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // ── Dialog 3: Destroy Squad Confirmation Dialog ──
    if (showDestroyDialog && selectedRoomForAction != null) {
        val targetRoom = selectedRoomForAction!!
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showDestroyDialog = false },
            containerColor = Color(0xFF1E2126),
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Destroy Squad?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${targetRoom.name}\" (${targetRoom.code})?\n\nThis will remove all members and delete this squad for everyone. This cannot be undone.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isActionInProgress = true
                        coroutineScope.launch {
                            SupabaseRoomManager.destroyRoom(targetRoom.id)
                            showDestroyDialog = false
                            isActionInProgress = false
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isActionInProgress
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Destroy Squad", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDestroyDialog = false },
                    enabled = !isActionInProgress
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // ── Dialog 4: Regular Member Exit Squad Confirmation Dialog ──
    if (showMemberLeaveDialog && selectedRoomForAction != null) {
        val targetRoom = selectedRoomForAction!!
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showMemberLeaveDialog = false },
            containerColor = Color(0xFF1E2126),
            icon = {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Exit Squad?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Are you sure you want to exit \"${targetRoom.name}\" (${targetRoom.code})?\n\nYou will need an invite or owner approval to rejoin.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isActionInProgress = true
                        coroutineScope.launch {
                            SupabaseRoomManager.leaveRoom(targetRoom.id, currentUserId)
                            showMemberLeaveDialog = false
                            isActionInProgress = false
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isActionInProgress
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Exit Squad", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMemberLeaveDialog = false },
                    enabled = !isActionInProgress
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            onDismiss = { showCreateDialog = false },
            onSuccess = { roomCode, roomName ->
                showCreateDialog = false
                onJoinRoom(roomCode, roomName)
            }
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            onDismiss = { showJoinDialog = false },
            onSuccess = { 
                showJoinDialog = false
                refreshData()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomItem(
    room: Room, 
    isOwner: Boolean = false, 
    pendingCount: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2126))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(Color(0xFF2C2F33), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (isOwner) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFFFA000).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "👑 OWNER",
                            color = Color(0xFFFFA000),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Code: ${room.code}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("• Hold to exit", color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
        if (pendingCount > 0) {
            Surface(
                color = Color(0xFF00FF66).copy(alpha = 0.2f),
                shape = CircleShape
            ) {
                Text(
                    text = "$pendingCount",
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PendingRequestItem(req: RoomMemberRequest, onApprove: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2126))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(req.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("wants to join ${req.roomName ?: req.roomId}", color = Color.Gray, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onApprove, modifier = Modifier.size(32.dp).background(Color(0xFF00FF66).copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDecline, modifier = Modifier.size(32.dp).background(Color(0xFFFF5252).copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CreateRoomDialog(
    currentUserId: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSuccess: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2126),
        title = { Text("Create Squad", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Squad Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF66)
                    ),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        isSubmitting = true
                        scope.launch {
                            val res = SupabaseRoomManager.createRoom(name, currentUserId, currentUsername)
                            if (res is RoomResult.Success) {
                                onSuccess(res.data.code, res.data.name)
                            } else if (res is RoomResult.Error) {
                                error = res.message
                                isSubmitting = false
                            }
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text("Create", color = Color(0xFF00FF66))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun JoinRoomDialog(
    currentUserId: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2126),
        title = { Text("Join Squad", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Squad Code (e.g. WT-A1B2)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF66)
                    ),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (successMsg != null) {
                    Text(successMsg!!, color = Color(0xFF00FF66), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (successMsg != null) {
                        onSuccess()
                    } else if (code.isNotBlank()) {
                        isSubmitting = true
                        scope.launch {
                            val res = SupabaseRoomManager.requestJoin(code, currentUserId, currentUsername)
                            if (res is RoomResult.Success) {
                                successMsg = "Join request sent! Waiting for owner to approve."
                                error = null
                            } else if (res is RoomResult.Error) {
                                error = res.message
                                successMsg = null
                            }
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(if (successMsg != null) "Done" else "Request Join", color = Color(0xFF00FF66))
            }
        },
        dismissButton = {
            if (successMsg == null) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
            }
        }
    )
}
