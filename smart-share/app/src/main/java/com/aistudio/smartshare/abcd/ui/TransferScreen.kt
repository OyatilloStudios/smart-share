package com.aistudio.smartshare.abcd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.smartshare.abcd.MainViewModel
import com.aistudio.smartshare.abcd.data.TransferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val status by viewModel.transferManager.status.collectAsStateWithLifecycle()
    val progress by viewModel.transferManager.progress.collectAsStateWithLifecycle()
    val speedMB by viewModel.transferManager.speedMB.collectAsStateWithLifecycle()
    val etaSeconds by viewModel.transferManager.etaSeconds.collectAsStateWithLifecycle()
    val activeFile by viewModel.transferManager.activeFile.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.transferManager.incomingRequest.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transferring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (incomingRequest != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Incoming Request", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "File: ${incomingRequest!!.filename}\nSize: ${incomingRequest!!.size / 1024 / 1024} MB",
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = { viewModel.acceptIncoming(false) }) {
                                Text("Reject")
                            }
                            Button(onClick = { viewModel.acceptIncoming(true) }) {
                                Text("Accept")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "File: $activeFile",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Speed: ${String.format("%.2f", speedMB)} MB/s")
                Text("ETA: $etaSeconds seconds")

                Spacer(modifier = Modifier.height(32.dp))

                if (status == TransferStatus.TRANSFERRING || status == TransferStatus.CONNECTING) {
                    Button(
                        onClick = { viewModel.cancelTransfer() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Transfer")
                    }
                } else {
                    Button(onClick = {
                        viewModel.resetTransfer()
                        onBack()
                    }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
