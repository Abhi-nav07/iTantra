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
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.TransportCoordinator
import com.itantra.core.transport.peer.BluetoothPeerTransport
import com.itantra.core.transport.peer.WifiPeerTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val hasPermissions: Boolean = false,
    val transportMode: TransportMode = TransportMode.BLUETOOTH
)

enum class TransportMode {
    BLUETOOTH, WIFI
}

class ConnectViewModel(
    private val transportCoordinator: TransportCoordinator,
    private val bluetoothTransport: BluetoothPeerTransport,
    private val wifiTransport: WifiPeerTransport,
    private val bluetoothAdapter: BluetoothAdapter?
) : ViewModel() {

    private val hasPermissionsState = MutableStateFlow(false)
    private val transportModeState = MutableStateFlow(TransportMode.BLUETOOTH)

    val uiState: StateFlow<ConnectUiState> = combine(
        transportCoordinator.observeConnectionState(),
        hasPermissionsState,
        transportModeState
    ) { state, hasPerms, mode ->
        ConnectUiState(
            connectionState = state,
            pairedDevices = getPairedDevices(hasPerms),
            hasPermissions = hasPerms,
            transportMode = mode
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), ConnectUiState())

    fun updatePermissions(hasPerms: Boolean) {
        hasPermissionsState.value = hasPerms
    }

    fun setTransportMode(mode: TransportMode) {
        transportModeState.value = mode
        disconnect() // disconnect current when switching modes
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices(hasPerms: Boolean): List<BluetoothDevice> {
        if (!hasPerms || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return bluetoothAdapter.bondedDevices.toList()
    }

    fun startBluetoothServer() {
        viewModelScope.launch {
            transportCoordinator.switchTransport(bluetoothTransport)
            bluetoothTransport.startServer()
        }
    }

    fun connectToBluetoothDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            transportCoordinator.switchTransport(bluetoothTransport)
            bluetoothTransport.connectToDevice(device)
        }
    }
    
    fun startWifiServer(port: Int = WifiPeerTransport.DEFAULT_PORT) {
        viewModelScope.launch {
            transportCoordinator.switchTransport(wifiTransport)
            wifiTransport.startServer(port)
        }
    }

    fun connectToWifiDevice(host: String, port: Int = WifiPeerTransport.DEFAULT_PORT) {
        viewModelScope.launch {
            transportCoordinator.switchTransport(wifiTransport)
            wifiTransport.connectToAddress(host, port)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            transportCoordinator.disconnect()
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
        // Top Bar
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

        // Transport Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.Center
        ) {
            val modes = listOf("Bluetooth" to TransportMode.BLUETOOTH, "Wi-Fi" to TransportMode.WIFI)
            modes.forEach { (label, mode) ->
                FilterChip(
                    selected = state.transportMode == mode,
                    onClick = { viewModel.setTransportMode(mode) },
                    label = { Text(label) },
                    modifier = Modifier.padding(horizontal = Spacing.xs)
                )
            }
        }
        
        Spacer(Modifier.height(Spacing.md))

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            // Connection Status
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

            if (state.transportMode == TransportMode.BLUETOOTH && !state.hasPermissions) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = {
                            if (state.transportMode == TransportMode.BLUETOOTH) {
                                viewModel.startBluetoothServer()
                            } else {
                                viewModel.startWifiServer()
                            }
                        },
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
                
                if (state.transportMode == TransportMode.BLUETOOTH) {
                    Text(
                        "PAIRED DEVICES",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    if (state.pairedDevices.isEmpty()) {
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
                                    onClick = { viewModel.connectToBluetoothDevice(device) },
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "CONNECT TO WI-FI PEER",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    
                    var hostIp by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = hostIp,
                        onValueChange = { hostIp = it },
                        label = { Text("Peer IP Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(Modifier.height(Spacing.md))
                    
                    Button(
                        onClick = {
                            if (hostIp.isNotBlank()) {
                                viewModel.connectToWifiDevice(hostIp)
                            }
                        },
                        enabled = hostIp.isNotBlank() && (state.connectionState == ConnectionState.DISCONNECTED || state.connectionState == ConnectionState.ERROR),
                        modifier = Modifier.fillMaxWidth(),
                        shape = ITantraShapes.button,
                    ) {
                        Text("CONNECT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
