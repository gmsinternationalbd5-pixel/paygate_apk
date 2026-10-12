package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.GateButton
import com.example.ui.components.StatusChip
import com.example.ui.components.ChipStatus
import com.example.ui.theme.GateDangerLight
import com.example.ui.theme.GateSuccessLight
import com.example.ui.theme.ProviderBkash
import com.example.ui.theme.ProviderNagad
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PairingUiState
import java.util.Locale

@Composable
fun PairingScreen(
    viewModel: MainViewModel,
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val isPaired by viewModel.isPairedFlow.collectAsStateWithLifecycle()
    val wallets by viewModel.walletsFlow.collectAsStateWithLifecycle()

    var pairingCodeInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf("https://api.paygate.com") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )

        Text(
            text = "Pair Device with PayGate",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Scan the QR code issued from the Admin console (single-use, expires in 10 minutes) or enter code manually.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // QR Viewfinder simulation box
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Align QR Code inside box",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status Feedback Banner
        when (val state = pairingState) {
            is PairingUiState.InProgress -> {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text("Exchanging code for KeyStore token & wallet mapping...", fontSize = 13.sp)
            }
            is PairingUiState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GateSuccessLight.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GateSuccessLight, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GateSuccessLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(state.message, color = GateSuccessLight, fontWeight = FontWeight.Bold)
                        }

                        if (wallets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Assigned Wallet SIMs:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            wallets.forEach { w ->
                                Text("• Slot ${w.slot}: ${w.provider.uppercase()} (${w.numberMasked})", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
            is PairingUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GateDangerLight.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GateDangerLight, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = GateDangerLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.message, color = GateDangerLight, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }
            else -> {}
        }

        // Manual Entry Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Manual Pairing Option",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = pairingCodeInput,
                    onValueChange = { pairingCodeInput = it.uppercase() },
                    label = { Text("10-min Single-Use Code") },
                    placeholder = { Text("ABCD-1234") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_pairing_code")
                )

                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("PayGate Webhook API Base") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_base_url")
                )

                GateButton(
                    text = "Connect Phone",
                    onClick = { viewModel.pairDevice(pairingCodeInput, baseUrlInput) },
                    enabled = pairingCodeInput.isNotBlank() && pairingState !is PairingUiState.InProgress,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_connect_device"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Instant Fleet Quick-Connect (for testing & emulator)
                OutlinedButton(
                    onClick = { viewModel.quickPairLocalDev() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_quick_dev_pair")
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulated Fleet Quick-Connect (instant 200 OK)")
                }
            }
        }
    }
}
