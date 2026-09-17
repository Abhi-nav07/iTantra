package com.itantra.feature.transceiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.ui.components.StatusChip
import com.itantra.app.ui.theme.*
import com.itantra.core.transport.BluetoothTransportEngine
import com.itantra.core.transport.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val hasPermissions: Boolean = false
)

class ConnectViewModel(
    private val transportEngine: BluetoothTransportEngine,
    private val bluetoothAdapter: BluetoothAdapter?
) : ViewModel() {

    private val hasPermissionsState = MutableStateFlow(false)

    val uiState: StateFlow<ConnectUiState> = combine(
        transportEngine.observeConnectionState(),
        hasPermissionsState
    ) { state, hasPerms ->
        ConnectUiState(
            connectionState = state,
            pairedDevices = getPairedDevices(hasPerms),
            hasPermissions = hasPerms
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), ConnectUiState())

    fun updatePermissions(hasPerms: Boolean) {
        hasPermissionsState.value = hasPerms
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices(hasPerms: Boolean): List<BluetoothDevice> {
        if (!hasPerms || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return bluetoothAdapter.bondedDevices.toList()
    }

    fun startServer() {
        viewModelScope.launch {
            transportEngine.startServer()
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            transportEngine.connectToDevice(device)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            transportEngine.disconnect()
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val granted = perms.values.all { it }
        viewModel.updatePermissions(granted)
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.updatePermissions(allGranted)
        if (!allGranted) {
            launcher.launch(requiredPermissions)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top Bar ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                )
            }
            Text("CONNECT PEER", style = MaterialTheme.typography.headlineMedium)
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            // ── Connection Status ───────────────────────────────
            val (statusLabel, statusColor) = when (state.connectionState) {
                ConnectionState.CONNECTED -> "CONNECTED" to SignalGreen
                ConnectionState.CONNECTING -> "CONNECTING…" to WarningAmber
                ConnectionState.LISTENING -> "LISTENING…" to WarningAmber
                ConnectionState.ERROR -> "CONNECTION ERROR" to CriticalRed
                else -> "DISCONNECTED" to TextSecondary
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(label = statusLabel, color = statusColor)
            }

            Spacer(Modifier.height(Spacing.xl))

            if (!state.hasPermissions) {
                // ── Permission Required ─────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Bluetooth Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningAmber,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Grant Bluetooth permissions to discover and connect to peers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Button(onClick = { launcher.launch(requiredPermissions) }) {
                        Text("Grant Permissions")
                    }
                }
            } else {
                // ── Connection Actions ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = viewModel::startServer,
                        enabled = state.connectionState == ConnectionState.DISCONNECTED || state.connectionState == ConnectionState.ERROR,
                        modifier = Modifier.weight(1f),
                        shape = ITantraShapes.button,
                    ) {
                        Text("LISTEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = viewModel::disconnect,
                        enabled = state.connectionState != ConnectionState.DISCONNECTED,
                        modifier = Modifier.weight(1f),
                        shape = ITantraShapes.button,
                    ) {
                        Text("DISCONNECT", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // ── Listening Indicator ─────────────────────────
                if (state.connectionState == ConnectionState.LISTENING) {
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = WarningAmber,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Waiting for peer to connect…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarningAmber,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xxl))
                Text(
                    "PAIRED DEVICES",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Spacing.sm))

                if (state.pairedDevices.isEmpty()) {
                    // Empty state
                    Text(
                        "No paired devices found.\nPair a device in Android Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.pairedDevices) { device ->
                            DeviceItem(
                                device = device,
                                isConnecting = state.connectionState == ConnectionState.CONNECTING,
                                onClick = { viewModel.connectToDevice(device) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceItem(
    device: BluetoothDevice,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDarkElevated, ITantraShapes.card)
            .clickable(enabled = !isConnecting, onClick = onClick)
            .padding(Spacing.lg)
            .semantics { contentDescription = "Connect to ${device.name ?: "unknown device"}" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                device.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            "CONNECT",
            style = MaterialTheme.typography.labelSmall,
            color = SignalGreen,
            fontWeight = FontWeight.Bold,
        )
    }
}
