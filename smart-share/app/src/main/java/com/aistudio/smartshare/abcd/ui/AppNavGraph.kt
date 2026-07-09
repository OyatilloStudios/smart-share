package com.aistudio.smartshare.abcd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistudio.smartshare.abcd.MainViewModel
import com.aistudio.smartshare.abcd.data.TransferStatus
import com.aistudio.smartshare.abcd.utils.QrGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    // Listen to transfer status to navigate automatically
    val transferStatus by viewModel.transferManager.status.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.transferManager.incomingRequest.collectAsStateWithLifecycle()

    LaunchedEffect(transferStatus) {
        if (transferStatus == TransferStatus.CONNECTING || transferStatus == TransferStatus.TRANSFERRING) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != "transfer") {
                navController.navigate("transfer")
            }
        }
    }

    LaunchedEffect(incomingRequest) {
        if (incomingRequest != null) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != "transfer") {
                navController.navigate("transfer")
            }
        }
    }

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToHistory = { navController.navigate("history") }
            )
        }
        composable("transfer") {
            TransferScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
