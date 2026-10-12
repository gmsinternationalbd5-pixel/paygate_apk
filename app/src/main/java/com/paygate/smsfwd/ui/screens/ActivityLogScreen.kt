package com.paygate.smsfwd.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paygate.smsfwd.data.local.entity.QueuedSms
import com.paygate.smsfwd.ui.components.ChipStatus
import com.paygate.smsfwd.ui.components.PinLockDialog
import com.paygate.smsfwd.ui.components.ReferenceChip
import com.paygate.smsfwd.ui.components.StatusChip
import com.paygate.smsfwd.ui.theme.GateDangerLight
import com.paygate.smsfwd.ui.theme.GateSuccessLight
import com.paygate.smsfwd.ui.theme.GateTheme
import com.paygate.smsfwd.ui.theme.GateWarningLight
import com.paygate.smsfwd.ui.theme.ProviderBkash
import com.paygate.smsfwd.ui.theme.ProviderNagad
import com.paygate.smsfwd.ui.theme.ProviderRocket
import com.paygate.smsfwd.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }
    var detailItem by remember { mutableStateOf<QueuedSms?>(null) }

    // FR-23: 10-second reveal state
    var isRevealed by remember { mutableStateOf(false) }
    var revealCountdown by remember { mutableIntStateOf(0) }
    var showPinDialogForReveal by remember { mutableStateOf(false) }

    // Timer effect for 10-second auto remask
    LaunchedEffect(isRevealed, revealCountdown) {
        if (isRevealed && revealCountdown > 0) {
            delay(1000L)
            revealCountdown -= 1
            if (revealCountdown <= 0) {
                isRevealed = false
            }
        }
    }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "DELIVERED" -> logs.filter { it.status == QueuedSms.STATUS_DELIVERED }
            "PENDING" -> logs.filter { it.status == QueuedSms.STATUS_PENDING }
            "STALE" -> logs.filter { it.status == QueuedSms.STATUS_STALE || it.isStale }
            "FAILED" -> logs.filter { it.status == QueuedSms.STATUS_FAILED || it.status == QueuedSms.STATUS_FAILED_PERMANENT || it.status == QueuedSms.STATUS_AUTH_PAUSED }
            else -> logs
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top filter bar & 10s Reveal toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${logs.size})") }
                )
                FilterChip(
                    selected = selectedFilter == "DELIVERED",
                    onClick = { selectedFilter = "DELIVERED" },
                    label = { Text("Delivered") }
                )
                FilterChip(
                    selected = selectedFilter == "FAILED",
                    onClick = { selectedFilter = "FAILED" },
                    label = { Text("Failed") }
                )
            }

            TextButton(
                onClick = {
                    if (isRevealed) {
                        isRevealed = false
                        revealCountdown = 0
                    } else {
                        if (viewModel.prefs.isPinSet) {
                            showPinDialogForReveal = true
                        } else {
                            isRevealed = true
                            revealCountdown = 10
                        }
                    }
                },
                modifier = Modifier.testTag("btn_reveal_toggle")
            ) {
                Icon(
                    imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isRevealed) "Hide (${revealCountdown}s)" else "Reveal (10s)")
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages match filter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLogs, key = { it.clientMsgId }) { sms ->
                    ActivityLogItemV3(
                        sms = sms,
                        isRevealed = isRevealed,
                        onClick = { detailItem = sms },
                        onRetry = { viewModel.retryFailedItem(sms.id) }
                    )
                }
            }
        }
    }

    if (showPinDialogForReveal) {
        PinLockDialog(
            title = "Reveal Raw TrxID",
            subtitle = "Enter 4-digit PIN to unmask TrxID for 10 seconds",
            onDismiss = { showPinDialogForReveal = false },
            onSuccess = {
                showPinDialogForReveal = false
                isRevealed = true
                revealCountdown = 10
            }
        )
    }

    if (detailItem != null) {
        LogDetailBottomSheetV3(
            sms = detailItem!!,
            onDismiss = { detailItem = null },
            onRetry = {
                viewModel.retryFailedItem(detailItem!!.id)
                detailItem = null
            }
        )
    }
}

@Composable
fun ActivityLogItemV3(
    sms: QueuedSms,
    isRevealed: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    val pillColor = when (sms.provider) {
        "bKash" -> ProviderBkash
        "Nagad" -> ProviderNagad
        "Rocket" -> ProviderRocket
        else -> MaterialTheme.colorScheme.primary
    }

    val (statusLabel, chipStatus) = when (sms.status) {
        QueuedSms.STATUS_DELIVERED -> "Delivered" to ChipStatus.SUCCESS
        QueuedSms.STATUS_PENDING -> "Pending (${sms.attemptCount})" to ChipStatus.WARNING
        QueuedSms.STATUS_STALE -> "Stale >24h" to ChipStatus.DANGER
        QueuedSms.STATUS_AUTH_PAUSED -> "Auth Paused" to ChipStatus.DANGER
        QueuedSms.STATUS_FAILED_PERMANENT -> "Needs Support" to ChipStatus.DANGER
        QueuedSms.STATUS_IGNORED -> "Ignored" to ChipStatus.NEUTRAL
        else -> "Failed" to ChipStatus.DANGER
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = GateTheme.colors.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GateTheme.colors.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("log_item_${sms.clientMsgId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GateTheme.colors.surface2)
                    .border(1.dp, GateTheme.colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.paygate.smsfwd.ui.components.ProviderBrandDot(
                        provider = sms.provider,
                        size = 8.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sms.provider ?: "Wallet",
                        color = GateTheme.colors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sms.amount ?: "Payment",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = com.paygate.smsfwd.ui.theme.GateMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = GateTheme.colors.ink
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ReferenceChip(reference = sms.refHint)
                }

                val displayTrxId = if (isRevealed) {
                    sms.trxId ?: "N/A"
                } else {
                    sms.trxId?.takeLast(4)?.let { "••••$it" } ?: "••••"
                }

                Text(
                    text = "TrxID: $displayTrxId • SIM ${sms.simSlot} • Seq ${sms.deviceSeq}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusChip(label = statusLabel, status = chipStatus)
                Spacer(modifier = Modifier.height(4.dp))
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sms.receivedAt))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailBottomSheetV3(
    sms: QueuedSms,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction Provenance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(sms.body)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy SMS")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Provider", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sms.provider ?: "N/A", fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Amount", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sms.amount ?: "N/A", fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reference Hint (ref_hint)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReferenceChip(reference = sms.refHint)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Raw TrxID", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sms.trxId ?: "N/A", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Device Sequence (device_seq)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${sms.deviceSeq}", fontFamily = FontFamily.Monospace)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SIM Slot & Source", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Slot ${sms.simSlot} (${sms.source})")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status & Attempts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${sms.status} (attempt ${sms.attemptCount})")
            }

            if (sms.errorMessage != null) {
                Text("Error: ${sms.errorMessage}", color = GateDangerLight, fontSize = 12.sp)
            }

            HorizontalDivider()
            Text("Idempotency Hash (client_msg_id):", style = MaterialTheme.typography.labelMedium)
            Text(sms.clientMsgId, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)

            HorizontalDivider()
            Text("Raw SMS Content:", style = MaterialTheme.typography.labelMedium)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(sms.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }

            if (sms.status != QueuedSms.STATUS_DELIVERED) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Delivery Now")
                }
            }
        }
    }
}
