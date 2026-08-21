package com.example.walkietalkieapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
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
import com.example.walkietalkieapp.auth.Room
import com.example.walkietalkieapp.auth.RoomMemberRequest
import com.example.walkietalkieapp.auth.RoomResult
import com.example.walkietalkieapp.auth.SupabaseRoomManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                            RoomItem(room = room, onClick = { onJoinRoom(room.code, room.name) })
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            onDismiss = { showCreateDialog = false },
            onSuccess = { roomCode ->
                showCreateDialog = false
                refreshData()
                val roomName = myRooms.find { it.code == roomCode }?.name ?: roomCode
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
            }
        )
    }
}

@Composable
fun RoomItem(room: Room, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2126))
            .clickable(onClick = onClick)
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
            Text(room.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Code: ${room.code}", color = Color.Gray, fontSize = 12.sp)
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
    onSuccess: (String) -> Unit
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
                                onSuccess(res.data.code)
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
