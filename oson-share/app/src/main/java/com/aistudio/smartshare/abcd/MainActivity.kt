package com.aistudio.smartshare.abcd

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.systemBars
                ) { padding ->
                    AdaptiveLayout(
                        modifier = Modifier.padding(padding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AdaptiveLayout(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LeftPanel(viewModel)
            }
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
                    .padding(24.dp)
            ) {
                RightPanel(viewModel)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { LeftPanel(viewModel) }
            item { RightPanel(viewModel) }
        }
    }
}

@Composable
fun LeftPanel(viewModel: MainViewModel) {
    val showHistory by viewModel.showHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "SmartShare",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Lokal & Masofaviy P2P tarmoq",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(30.dp))
        MyQRCodeCard(viewModel)
        Spacer(modifier = Modifier.height(30.dp))
        PairingSection(viewModel)
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { viewModel.toggleHistory() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = if (showHistory) Icons.Default.List else Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showHistory) "Workspacega qaytish" else "Kelgan fayllar tarixi",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun MyQRCodeCard(viewModel: MainViewModel) {
    val code = viewModel.deviceCode
    val name = viewModel.deviceName

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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, Color(0xFF38383A), RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            qrBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
            } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Ulanish uchun 5 xonali kod:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = code,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
    }
}

fun generateQrCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
fun PairingSection(viewModel: MainViewModel) {
    var codeInput by remember { mutableStateOf("") }
    val transferStatus by viewModel.transferStatus.collectAsState()

    Column {
        Text("Qurilmaga ulanish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            OutlinedTextField(
                value = codeInput,
                onValueChange = { if (it.length <= 5) codeInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("code_input"),
                placeholder = { Text("5 xonali kod", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(
                onClick = { viewModel.startTransfer(codeInput) },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("connect_button"),
                enabled = transferStatus == TransferStatus.IDLE || transferStatus == TransferStatus.COMPLETED || transferStatus == TransferStatus.FAILED || transferStatus == TransferStatus.CANCELLED
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Connect", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RightPanel(viewModel: MainViewModel) {
    val showHistory by viewModel.showHistory.collectAsState()
    if (showHistory) {
        HistoryView(viewModel)
    } else {
        TransferWorkspace(viewModel)
    }
}

@Composable
fun TransferWorkspace(viewModel: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        var filePath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Fayl yo'lini kiriting", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    placeholder = { Text("Masalan: document.pdf") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (filePath.isNotBlank()) viewModel.addSelectedFile(filePath)
                    showDialog = false
                }) {
                    Text("Qo'shish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Bekor qilish") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Fayllarni yuborish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))
                Text("Fayllarni bu yerga sudrab tashlang", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = { showDialog = true }) {
                    Text("Yoki kompyuterdan tanlang", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        
        val status by viewModel.transferStatus.collectAsState()
        if (status == TransferStatus.IDLE || status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.CANCELLED) {
            SelectedFilesQueue(viewModel)
        } else {
            ActiveTransferCard(viewModel)
        }
    }
}

@Composable
fun SelectedFilesQueue(viewModel: MainViewModel) {
    val files by viewModel.selectedFiles.collectAsState()
    
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hozircha tanlangan fayllar yo'q.", color = Color.White.copy(alpha = 0.2f), fontSize = 13.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tanlangan fayllar (${files.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = { viewModel.clearSelectedFiles() }) {
                Text("Hammasini o'chirish", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files) { file ->
                val filename = file.substringAfterLast("/")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeIcon(filename, viewModel.getCategoryFolder(filename))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(filename, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { viewModel.removeSelectedFile(file) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        val status by viewModel.transferStatus.collectAsState()
        Button(
            onClick = { /* Connect must be triggered from PairingSection in this design */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (status == TransferStatus.COMPLETED) "YANA YUBORISH UCHUN KOD KIRITING" else "KOD KIRITIB YUBORISH",
                fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTypeIcon(activeFile, viewModel.getCategoryFolder(activeFile))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeFile.ifEmpty { "Bog'lanmoqda..." },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = when (status) {
                    TransferStatus.CONNECTING -> "Qurilma bilan ulanmoqda..."
                    TransferStatus.TRANSFERRING -> "Uzatilmoqda..."
                    TransferStatus.COMPLETED -> "Muvaffaqiyatli yakunlandi!"
                    TransferStatus.FAILED -> "Ulanish muvaffaqiyatsiz tugadi!"
                    TransferStatus.CANCELLED -> "Bekor qilindi!"
                    else -> ""
                }
                val statusColor = when (status) {
                    TransferStatus.TRANSFERRING -> MaterialTheme.colorScheme.primary
                    TransferStatus.COMPLETED -> SuccessGreen
                    TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                    else -> Color.Gray
                }
                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = { viewModel.cancelTransfer() },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row {
                Text(String.format("%.2f MB/s", speed), color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(10.dp))
                if (eta in 1..9998) {
                    Text("${eta}s qoldi", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FileTypeIcon(filename: String, folder: String) {
    val icon = when (folder) {
        "Images" -> Icons.Default.AccountBox
        "Videos" -> Icons.Default.PlayArrow
        "Documents" -> Icons.Default.Info
        "Music" -> Icons.Default.PlayArrow
        else -> Icons.Default.Build
    }
    val color = when (folder) {
        "Images" -> MaterialTheme.colorScheme.error
        "Videos" -> InfoBlue
        "Documents" -> SuccessGreen
        "Music" -> MaterialTheme.colorScheme.primary
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun HistoryView(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kelgan fayllar tarixi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { viewModel.clearHistory() }) {
                Text("Tarixni tozalash", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tarix bo'sh", color = Color.White.copy(alpha = 0.2f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileTypeIcon(item.filename, item.category)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.filename, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%.2f MB", item.size / (1024f * 1024f)), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
