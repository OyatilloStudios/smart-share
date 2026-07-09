package com.aistudio.smartshare.abcd.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.smartshare.abcd.MainViewModel
import com.aistudio.smartshare.abcd.utils.QrGenerator

// Google Code Scanner kutubxonalari importlari
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit
) {
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val targetCode by viewModel.targetCode.collectAsStateWithLifecycle()
    val deviceCode = viewModel.transferManager.getDeviceCode()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addFiles(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartShare", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedFiles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.startSending() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("Send", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Identity Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your Device Code", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deviceCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val qrBitmap = remember(deviceCode) { QrGenerator.generateQrCode(deviceCode, 400) }
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Device QR Code",
                            modifier = Modifier
                                .size(150.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    val localIps = remember { viewModel.getLocalIpsList() }
                    if (localIps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("My IP Addresses:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        localIps.forEach { ip ->
                            Text(ip, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val context = androidx.compose.ui.platform.LocalContext.current

            // Send Section (Target Code and Password)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = targetCode,
                    onValueChange = { viewModel.setTargetCode(it) },
                    label = { Text("Target Code / IP", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            try {
                                val scanner = GmsBarcodeScanning.getClient(context)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode: Barcode ->
                                        val rawValue = barcode.rawValue ?: ""
                                        try {
                                            val json = org.json.JSONObject(rawValue)
                                            val code = json.optString("code")
                                            if (code.isNotEmpty()) {
                                                viewModel.setTargetCode(code)
                                            }
                                        } catch (e: Exception) {
                                            viewModel.setTargetCode(rawValue)
                                        }
                                    }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Scan QR Code"
                            )
                        }
                    }
                )

                var passwordInput by remember { mutableStateOf(viewModel.getEncryptionPassword()) }
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { 
                        passwordInput = it
                        viewModel.setEncryptionPassword(it)
                    },
                    label = { Text("Password (optional)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add files")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Files")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Files List
            if (selectedFiles.isNotEmpty()) {
                Text(
                    "Selected Files (${selectedFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(selectedFiles) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${file.size / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeFile(file) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
