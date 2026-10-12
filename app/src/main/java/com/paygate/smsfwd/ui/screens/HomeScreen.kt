package com.paygate.smsfwd.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paygate.smsfwd.R
import com.paygate.smsfwd.data.local.entity.QueuedSms
import com.paygate.smsfwd.ui.components.ChipStatus
import com.paygate.smsfwd.ui.components.GateButton
import com.paygate.smsfwd.ui.components.HealthRow
import com.paygate.smsfwd.ui.components.MaskedValue
import com.paygate.smsfwd.ui.components.ReferenceChip
import com.paygate.smsfwd.ui.components.StatCard
import com.paygate.smsfwd.ui.components.StatusChip
import com.paygate.smsfwd.ui.theme.GateDangerLight
import com.paygate.smsfwd.ui.theme.GateSuccessLight
import com.paygate.smsfwd.ui.theme.GateTheme
import com.paygate.smsfwd.ui.theme.GateWarningLight
import com.paygate.smsfwd.ui.theme.ProviderBkash
import com.paygate.smsfwd.ui.theme.ProviderNagad
import com.paygate.smsfwd.ui.theme.ProviderRocket
import com.paygate.smsfwd.ui.viewmodel.HealthStatus
import com.paygate.smsfwd.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestSmsPermission: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPaired by viewModel.isPairedFlow.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val staleCount by viewModel.staleCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()
    val lastDelivered by viewModel.lastDeliveredSms.collectAsStateWithLifecycle()
    val health by viewModel.healthStatus.collectAsStateWithLifecycle()
    val wallets by viewModel.walletsFlow.collectAsStateWithLifecycle()
    val isKiosk by viewModel.isKioskFlow.collectAsStateWithLifecycle()
    val actionFeedback by viewModel.actionFeedback.collectAsStateWithLifecycle()

    var showSimulatorSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SIM Wallet Registry Status Row (FR-15, FR-22)
        item {
            SimWalletsStatusRow(
                wallets = wallets,
                isPaired = isPaired,
                onConfigureClick = onNavigateToPairing
            )
        }

        // 2. Kiosk or Attention Banner (if paused, stuck queue, or unmapped)
        if (viewModel.prefs.isAuthRevoked) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GateDangerLight.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GateDangerLight, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = GateDangerLight)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delivery Paused (HTTP 401/403)", fontWeight = FontWeight.Bold, color = GateDangerLight)
                            Text("Token revoked or merchant hold. Tap to re-pair device.", fontSize = 12.sp)
                        }
                        GateButton(
                            text = "Re-pair",
                            onClick = onNavigateToPairing,
                            testTag = "btn_banner_repair"
                        )
                    }
                }
            }
        }

        // Action Feedback Toast
        if (actionFeedback != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionFeedback!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearActionFeedback() }) {
                            Text("✕", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Stat Cards Grid (Queue depth, Stale count, Delivered)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Outbox Queue",
                    value = "$pendingCount",
                    subtitle = if (pendingCount == 0) "All synced" else "Pending send",
                    statusColor = if (pendingCount > 0) GateWarningLight else GateSuccessLight,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Stale (>24h)",
                    value = "$staleCount",
                    subtitle = if (staleCount > 0) "Needs review" else "Zero stale",
                    statusColor = if (staleCount > 0) GateDangerLight else GateSuccessLight,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Failed",
                    value = "$failedCount",
                    subtitle = if (failedCount > 0) "Tap to retry" else "0 dropped",
                    statusColor = if (failedCount > 0) GateDangerLight else GateSuccessLight,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Last Forwarded Payment Card (with ReferenceChip & Provider Pill)
        item {
            LastPaymentHeroCard(
                lastDelivered = lastDelivered,
                onViewLogClick = onNavigateToLog
            )
        }

        // 5. Setup & Health Checklist with live Fix buttons (FR-18)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .testTag("health_checklist_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Setup & Health Checklist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StatusChip(
                            label = if (health.hasSmsPermission && health.isBatteryExempt) "HEALTHY" else "ATTENTION",
                            status = if (health.hasSmsPermission && health.isBatteryExempt) ChipStatus.SUCCESS else ChipStatus.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    HealthRow(
                        icon = Icons.Default.PhoneAndroid,
                        label = "SMS Receive Permission",
                        isHealthy = health.hasSmsPermission,
                        onFixClick = onRequestSmsPermission,
                        statusHint = "Monitors bKash, Nagad & Rocket PDUs"
                    )

                    HealthRow(
                        icon = Icons.Default.Notifications,
                        label = "Notifications & Foreground Alert",
                        isHealthy = health.hasNotificationPermission,
                        onFixClick = onRequestNotifPermission,
                        statusHint = "Android 13+ background execution"
                    )

                    HealthRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery Optimization Exemption",
                        isHealthy = health.isBatteryExempt,
                        onFixClick = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        statusHint = "Exempt from aggressive OEM battery killers"
                    )

                    HealthRow(
                        icon = Icons.Default.SimCard,
                        label = "Wallet-to-SIM Mapping",
                        isHealthy = wallets.isNotEmpty(),
                        onFixClick = onNavigateToPairing,
                        statusHint = "${wallets.size} SIM wallets bound to device"
                    )
                }
            }
        }

        // 6. Action Controls: Sync Now, Heartbeat, Test Simulator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fleet Operations & Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Instant queue drain, manual heartbeat ping, and simulated test receipts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GateButton(
                            text = "Sync Now",
                            onClick = { viewModel.triggerSyncNow() },
                            leadingIcon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                            testTag = "btn_sync_now"
                        )

                        OutlinedButton(
                            onClick = { viewModel.triggerHeartbeatNow() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_heartbeat")
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Heartbeat")
                        }

                        Button(
                            onClick = { showSimulatorSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_test_simulator")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulator")
                        }
                    }
                }
            }
        }
    }

    // Comprehensive PRD v3.0 Test SMS Simulator Bottom Sheet
    if (showSimulatorSheet) {
        TestSmsSimulatorSheetV3(
            onDismiss = { showSimulatorSheet = false },
            onInjectSms = { sender, body, simSlot ->
                viewModel.simulateIncomingSms(sender, body, simSlot)
                showSimulatorSheet = false
            }
        )
    }
}

@Composable
fun SimWalletsStatusRow(
    wallets: List<com.paygate.smsfwd.data.remote.model.WalletSlotInfo>,
    isPaired: Boolean,
    onConfigureClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isPaired) GateSuccessLight else GateWarningLight, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPaired) "Fleet Device Online" else "Device Unpaired",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusChip(
                    label = if (isPaired) "CONNECTED" else "UNPAIRED",
                    status = if (isPaired) ChipStatus.SUCCESS else ChipStatus.WARNING
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Text("Registered SIM Wallets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            if (wallets.isEmpty()) {
                Text(
                    text = "No wallets mapped. Pair device with PayGate dashboard.",
                    fontSize = 12.sp,
                    color = GateWarningLight
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wallets.forEach { w ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GateTheme.colors.surface2)
                                .border(1.dp, GateTheme.colors.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.paygate.smsfwd.ui.components.ProviderBrandDot(
                                    provider = w.provider,
                                    size = 10.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${w.provider.uppercase()} (SIM ${w.slot})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = GateTheme.colors.ink
                                    )
                                    Text(
                                        text = w.numberMasked,
                                        fontFamily = com.paygate.smsfwd.ui.theme.GateMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = GateTheme.colors.ink2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LastPaymentHeroCard(
    lastDelivered: QueuedSms?,
    onViewLogClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GateTheme.colors.border, RoundedCornerShape(16.dp))
            .testTag("last_forwarded_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last Forwarded Payment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onViewLogClick) {
                    Text("View 200 Logs", fontSize = 12.sp, color = GateTheme.colors.accent)
                }
            }

            if (lastDelivered == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No wallet payment SMS forwarded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GateTheme.colors.ink3
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GateTheme.colors.surface2)
                            .border(1.dp, GateTheme.colors.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.paygate.smsfwd.ui.components.ProviderBrandDot(
                                provider = lastDelivered.provider,
                                size = 8.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lastDelivered.provider ?: "Wallet",
                                color = GateTheme.colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = lastDelivered.amount ?: "Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = com.paygate.smsfwd.ui.theme.GateMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = GateTheme.colors.ink
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ReferenceChip(reference = lastDelivered.refHint)
                        }

                        Text(
                            text = "TrxID: ${lastDelivered.trxId?.takeLast(4)?.let { "••••$it" } ?: "••••"} • SIM ${lastDelivered.simSlot}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastDelivered.receivedAt))
                        Text(text = timeStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        if (lastDelivered.deliveryLatencyMs != null) {
                            Text(
                                text = "${lastDelivered.deliveryLatencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = GateSuccessLight
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSmsSimulatorSheetV3(
    onDismiss: () -> Unit,
    onInjectSms: (sender: String, body: String, simSlot: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var senderInput by remember { mutableStateOf("bKash") }
    var bodyInput by remember {
        mutableStateOf("You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF934P923 at 12/10/2026 15:24")
    }
    var simSlotInput by remember { mutableStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SMS Test & Attack Fixture Simulator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Test every PRD v3.0 fixture: Reference hints, Cash In, Nagad, Rocket, OTP rejection & 11-digit forge rejection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("PRD v3.0 Test Fixtures:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = bodyInput.contains("Ref REF934P923"),
                    onClick = {
                        senderInput = "bKash"
                        bodyInput = "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF934P923 at 12/10/2026 15:24"
                    },
                    label = { Text("bKash + Ref") }
                )
                FilterChip(
                    selected = bodyInput.contains("ref934p923"),
                    onClick = {
                        senderInput = "bKash"
                        bodyInput = "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and reference ref934p923 at 12/10/2026 15:24"
                    },
                    label = { Text("Lower Ref") }
                )
                FilterChip(
                    selected = bodyInput.contains("REF-934P923"),
                    onClick = {
                        senderInput = "bKash"
                        bodyInput = "You have received Tk 1,200.00 from 01712750278. Fee Tk 0.00. Balance Tk 1,200.00. TrxID DJC9SZQV0J and Ref REF-934P923 at 12/10/2026 15:24"
                    },
                    label = { Text("Hyphen Ref") }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = bodyInput.contains("Cash In"),
                    onClick = {
                        senderInput = "bKash"
                        bodyInput = "Cash In Tk 2,500.00 from 01755650768 successful. Fee Tk 0.00. Balance Tk 2,505.39. TrxID DIS133PE5L at 28/09/2026 21:59."
                    },
                    label = { Text("bKash Cash In") }
                )
                FilterChip(
                    selected = senderInput == "NAGAD",
                    onClick = {
                        senderInput = "NAGAD"
                        bodyInput = "You have received Tk 850.00 from 01811223344. Balance Tk 2,400.00. TrxID 72AB3890 at 12/10/2026"
                    },
                    label = { Text("Nagad") }
                )
                FilterChip(
                    selected = senderInput == "16216",
                    onClick = {
                        senderInput = "16216"
                        bodyInput = "Tk 3,100.00 received from 01999887766. TxnId 98214. Rocket Balance Tk 5,000.00"
                    },
                    label = { Text("Rocket (16216)") }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = bodyInput.contains("OTP is 482913"),
                    onClick = {
                        senderInput = "bKash"
                        bodyInput = "Your OTP is 482913. Ref 7781"
                    },
                    label = { Text("⚠️ OTP Exclusion") }
                )
                FilterChip(
                    selected = senderInput == "01712345678",
                    onClick = {
                        senderInput = "01712345678"
                        bodyInput = "You have received Tk 500.00. TrxID FORGE999 bKash"
                    },
                    label = { Text("⚠️ 11-digit Spoof") }
                )
            }

            OutlinedTextField(
                value = senderInput,
                onValueChange = { senderInput = it },
                label = { Text("SMS Sender") },
                modifier = Modifier.fillMaxWidth().testTag("input_sim_sender")
            )

            OutlinedTextField(
                value = bodyInput,
                onValueChange = { bodyInput = it },
                label = { Text("SMS Body") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("input_sim_body")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("SIM Slot:")
                FilterChip(selected = simSlotInput == 1, onClick = { simSlotInput = 1 }, label = { Text("SIM 1") })
                FilterChip(selected = simSlotInput == 2, onClick = { simSlotInput = 2 }, label = { Text("SIM 2") })
            }

            Button(
                onClick = { onInjectSms(senderInput, bodyInput, simSlotInput) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("btn_inject_sms")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inject SMS into Pipeline")
            }
        }
    }
}
