package com.aistudio.smartshare.abcd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.smartshare.abcd.data.TransferHistoryItem
import com.aistudio.smartshare.abcd.data.TransferStatus
import com.aistudio.smartshare.abcd.ui.MainViewModel
import com.aistudio.smartshare.abcd.ui.theme.InfoBlue
import com.aistudio.smartshare.abcd.ui.theme.PrimaryPurple
import com.aistudio.smartshare.abcd.ui.theme.SmartShareTheme
import com.aistudio.smartshare.abcd.ui.theme.SuccessGreen
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartShareTheme {
                val viewModel: MainViewModel = viewModel()
                val context = LocalContext.current

                // Request dangerous storage permissions on launch for older devices
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val permissions = arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                        val needed = permissions.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isNotEmpty()) {
                            permissionLauncher.launch(needed.toTypedArray())
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.systemBars
                ) { padding ->
                    MainLayout(
                        modifier = Modifier.padding(padding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MainLayout(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val status by viewModel.transferStatus.collectAsState()

    // Dialog for incoming file request
    incomingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { request.onResponse(false) },
            title = { Text("Keluvchi fayl so'rovi", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Qurilma (${request.peerIp}) sizga fayl yubormoqchi:\n", color = Color.White)
                    Text("Nomi: ${request.filename}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Hajmi: ${String.format("%.2f MB", request.size / (1024f * 1024f))}", color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { request.onResponse(true) }) {
                    Text("QABUL QILISH", color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { request.onResponse(false) }) {
                    Text("RAD ETISH", color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // App header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "OsonShare Mobile",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Xavfsiz P2P fayl almashish",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // Active transfer progress bar if transferring or connecting
        if (status != TransferStatus.IDLE && status != TransferStatus.COMPLETED && status != TransferStatus.FAILED && status != TransferStatus.CANCELLED) {
            ActiveTransferCard(viewModel)
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("YUBORISH", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Send, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("QABUL QILISH", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("TARIX", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.List, contentDescription = null) }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            when (selectedTab) {
                0 -> SendTab(viewModel)
                1 -> ReceiveTab(viewModel)
                2 -> HistoryTab(viewModel)
            }
        }
    }
}

@Composable
fun SendTab(viewModel: MainViewModel) {
    val files by viewModel.selectedFiles.collectAsState()
    val status by viewModel.transferStatus.collectAsState()
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.forEach { uri ->
            viewModel.addSelectedFile(uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selection card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, Color(0xFF38383A), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FAYLLARNI TANLASH", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected files list
        if (files.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tanlangan fayllar (${files.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                TextButton(onClick = { viewModel.clearSelectedFiles() }) {
                    Text("O'chirish", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileTypeIcon(file.name, viewModel.getCategoryFolder(file.name))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(String.format("%.2f MB", file.size / (1024f * 1024f)), color = Color.Gray, fontSize = 10.sp)
                        }
                        IconButton(onClick = { viewModel.removeSelectedFile(file) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Hozircha tanlangan fayllar yo'q.", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Inputs for connection code & custom password
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Ulanish kodi / IP", fontSize = 11.sp) },
                placeholder = { Text("Masalan: 54321") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { 
                    passwordInput = it
                    viewModel.setEncryptionPassword(it)
                },
                label = { Text("Maxfiy parol (ixtiyoriy)", fontSize = 11.sp) },
                placeholder = { Text("Maxfiy shifrlash") },
                singleLine = true,
                modifier = Modifier.weight(1.2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Send Button
        Button(
            onClick = { 
                viewModel.startTransfer(codeInput)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = files.isNotEmpty() && codeInput.length >= 5 && (status == TransferStatus.IDLE || status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.CANCELLED)
        ) {
            Text("YUBORISH", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun ReceiveTab(viewModel: MainViewModel) {
    val code = viewModel.deviceCode
    val name = viewModel.deviceName
    val context = LocalContext.current
    var passwordInput by remember { mutableStateOf(viewModel.getEncryptionPassword()) }

    // Build QR-Code payload
    val qrData = remember(code, name) {
        JSONObject().apply {
            put("code", code)
            put("name", name)
        }.toString()
    }

    val qrBitmap = remember(qrData) {
        generateQrCode(qrData)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38383A))
        ) {
            Column(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let {
                        Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Ulanish uchun 5 xonali kod:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = code,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }

        // Custom password settings inside Receive Tab for decryption
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { 
                passwordInput = it
                viewModel.setEncryptionPassword(it)
            },
            label = { Text("Qabul qilish shifrlash paroli (Agar maxfiy bo'lsa)", fontSize = 11.sp) },
            placeholder = { Text("Yuboruvchi o'rnatgan parolni yozing") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Qabul qilishga tayyor • Oflayn rejim faol", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryTab(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kelgan fayllar tarixi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Tarixni tozalash", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tarix bo'sh", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileTypeIcon(item.filename, item.category)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.filename, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${item.category} • ${String.format("%.2f MB", item.size / (1024f * 1024f))}",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveTransferCard(viewModel: MainViewModel) {
    val activeFile by viewModel.activeFile.collectAsState()
    val status by viewModel.transferStatus.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val speed by viewModel.speedMB.collectAsState()
    val eta by viewModel.etaSeconds.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(activeFile, viewModel.getCategoryFolder(activeFile))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeFile.ifEmpty { "Bog'lanmoqda..." },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusText = when (status) {
                        TransferStatus.CONNECTING -> "Bog'lanmoqda..."
                        TransferStatus.TRANSFERRING -> "Uzatilmoqda..."
                        TransferStatus.COMPLETED -> "Muvaffaqiyatli yakunlandi!"
                        TransferStatus.FAILED -> "Muammo yuz berdi!"
                        TransferStatus.CANCELLED -> "Bekor qilindi!"
                        else -> ""
                    }
                    val statusColor = when (status) {
                        TransferStatus.TRANSFERRING -> MaterialTheme.colorScheme.primary
                        TransferStatus.COMPLETED -> SuccessGreen
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> Color.Gray
                    }
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { viewModel.cancelTransfer() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.background
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row {
                    Text(String.format("%.2f MB/s", speed), color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    if (eta in 1..9998) {
                        Text("${eta}s qoldi", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
