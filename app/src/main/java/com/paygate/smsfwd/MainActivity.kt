package com.paygate.smsfwd

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paygate.smsfwd.ui.screens.ActivityLogScreen
import com.paygate.smsfwd.ui.screens.HomeScreen
import com.paygate.smsfwd.ui.screens.PairingScreen
import com.paygate.smsfwd.ui.screens.SettingsScreen
import com.paygate.smsfwd.ui.theme.PayGateTheme
import com.paygate.smsfwd.ui.theme.StatusPending
import com.paygate.smsfwd.ui.theme.StatusSuccess
import com.paygate.smsfwd.ui.theme.StatusWarning
import com.paygate.smsfwd.ui.viewmodel.MainViewModel
import java.util.Locale

enum class ScreenTab {
    HOME,
    LOG,
    PAIR,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val language by viewModel.languageFlow.collectAsStateWithLifecycle()
            LaunchedEffect(language) {
                applyLocale(language)
            }

            PayGateTheme {
                PayGateApp(viewModel = viewModel)
            }
        }
    }

    private fun applyLocale(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHealthStatus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayGateApp(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    val isPaired by viewModel.isPairedFlow.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val language by viewModel.languageFlow.collectAsStateWithLifecycle()

    // Permission Launchers
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshHealthStatus()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshHealthStatus()
    }

    // Predictive back handler when on sub-screens
    if (currentTab != ScreenTab.HOME) {
        BackHandler {
            currentTab = ScreenTab.HOME
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.paygate_logo_1791826977584),
                            contentDescription = "PayGate Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dotColor = when {
                                    !isPaired -> StatusWarning
                                    pendingCount > 0 -> StatusPending
                                    else -> StatusSuccess
                                }
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(dotColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isPaired) "Live Forwarder" else "Not Paired",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.toggleLanguage() },
                        modifier = Modifier.testTag("btn_top_lang")
                    ) {
                        Text(
                            text = if (language == "bn") "EN" else "বাং",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == ScreenTab.HOME,
                    onClick = { currentTab = ScreenTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    modifier = Modifier.testTag("nav_item_home")
                )
                NavigationBarItem(
                    selected = currentTab == ScreenTab.LOG,
                    onClick = { currentTab = ScreenTab.LOG },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_log)) },
                    modifier = Modifier.testTag("nav_item_log")
                )
                NavigationBarItem(
                    selected = currentTab == ScreenTab.PAIR,
                    onClick = { currentTab = ScreenTab.PAIR },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_pair)) },
                    modifier = Modifier.testTag("nav_item_pair")
                )
                NavigationBarItem(
                    selected = currentTab == ScreenTab.SETTINGS,
                    onClick = { currentTab = ScreenTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            ScreenTab.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onRequestSmsPermission = {
                        smsPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    },
                    onRequestNotifPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onNavigateToPairing = { currentTab = ScreenTab.PAIR },
                    onNavigateToLog = { currentTab = ScreenTab.LOG },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.LOG -> {
                ActivityLogScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.PAIR -> {
                PairingScreen(
                    viewModel = viewModel,
                    onPairingSuccess = { currentTab = ScreenTab.HOME },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            ScreenTab.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
