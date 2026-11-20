package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.Battery2Data
import com.example.model.ConnectionState
import com.example.model.DiscoveredDevice
import com.example.model.Esp32Data
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Esp32BleManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onLog: (source: String, msg: String, isError: Boolean) -> Unit,
) {
  companion object {
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX_WRITE_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_TX_NOTIFY_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }

  private val btAdapter: BluetoothAdapter?
    get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

  // Slot 1: ESP32 #1 (CAN Sniffer, Contact, Matrix, Music)
  private val _esp1State = MutableStateFlow(ConnectionState.DISCONNECTED)
  val esp1State: StateFlow<ConnectionState> = _esp1State.asStateFlow()
  private val _esp1Name = MutableStateFlow("")
  val esp1Name: StateFlow<String> = _esp1Name.asStateFlow()
  private val _esp1Data = MutableStateFlow(Esp32Data())
  val esp1Data: StateFlow<Esp32Data> = _esp1Data.asStateFlow()

  // Slot 2: ESP32 #2 (BMS ORI Battery 2)
  private val _esp2State = MutableStateFlow(ConnectionState.DISCONNECTED)
  val esp2State: StateFlow<ConnectionState> = _esp2State.asStateFlow()
  private val _esp2Name = MutableStateFlow("")
  val esp2Name: StateFlow<String> = _esp2Name.asStateFlow()
  private val _battery2Data = MutableStateFlow(Battery2Data())
  val battery2Data: StateFlow<Battery2Data> = _battery2Data.asStateFlow()

  private val _scannedDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val scannedDevices: StateFlow<List<DiscoveredDevice>> = _scannedDevices.asStateFlow()

  private var gatt1: BluetoothGatt? = null
  private var write1: BluetoothGattCharacteristic? = null
  private val rxBuf1 = StringBuilder()

  private var gatt2: BluetoothGatt? = null
  private var write2: BluetoothGattCharacteristic? = null
  private val rxBuf2 = StringBuilder()

  private var scanCb: ScanCallback? = null
  @Volatile var invertB2Current: Boolean = false

  private fun hasPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
          PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  @SuppressLint("MissingPermission")
  fun scanDevices() {
    val adapter = btAdapter
    if (adapter == null || !adapter.isEnabled || !hasPermission()) {
      onLog("ESP32", "Bluetooth belum aktif atau izin belum diberikan.", true)
      return
    }
    stopScan()
    val found = mutableListOf<DiscoveredDevice>()
    _scannedDevices.value = emptyList()
    val cb =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val dev = result.device ?: return
          val addr = dev.address ?: return
          val name =
            try {
              dev.name ?: result.scanRecord?.deviceName ?: "ESP32 Device"
            } catch (_: SecurityException) {
              "ESP32 Device"
            }
          if (found.none { it.address == addr }) {
            found.add(DiscoveredDevice(name = name, address = addr, isBle = true, rssi = result.rssi))
            _scannedDevices.value = found.sortedByDescending { it.rssi }
          }
        }
      }
    scanCb = cb
    try {
      adapter.bluetoothLeScanner?.startScan(cb)
      scope.launch {
        delay(8000)
        stopScan()
      }
    } catch (e: Exception) {
      onLog("ESP32", "Gagal scan BLE: ${e.message}", true)
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    val cb = scanCb ?: return
    scanCb = null
    try {
      btAdapter?.bluetoothLeScanner?.stopScan(cb)
    } catch (_: Exception) {}
  }

  @SuppressLint("MissingPermission")
  fun connectSlot(slot: Int, address: String) {
    if (!hasPermission()) return
    val dev =
      try {
        btAdapter?.getRemoteDevice(address)
      } catch (_: Exception) {
        null
      } ?: return

    disconnectSlot(slot)
    stopScan()

    val devName =
      try {
        dev.name ?: address
      } catch (_: SecurityException) {
        address
      }

    if (slot == 1) {
      _esp1State.value = ConnectionState.CONNECTING
      _esp1Name.value = devName
    } else {
      _esp2State.value = ConnectionState.CONNECTING
      _esp2Name.value = devName
    }
    onLog("ESP32#$slot", "Menghubungkan ke $devName…", false)

    val gatt =
      dev.connectGatt(
        context,
        false,
        object : BluetoothGattCallback() {
          override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
              g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
              onLog("ESP32#$slot", "ESP32 #$slot terputus.", true)
              cleanupSlot(slot)
            }
          }

          override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(NUS_SERVICE_UUID)
            if (svc == null) {
              onLog("ESP32#$slot", "Nordic UART Service tidak ditemukan.", true)
              cleanupSlot(slot)
              return
            }
            val tx = svc.getCharacteristic(NUS_TX_NOTIFY_UUID)
            val rx = svc.getCharacteristic(NUS_RX_WRITE_UUID)
            if (slot == 1) write1 = rx else write2 = rx

            if (tx != null) {
              g.setCharacteristicNotification(tx, true)
              val cccd = tx.getDescriptor(CCCD_UUID)
              if (cccd != null) {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
              }
            }
            if (slot == 1) {
              _esp1State.value = ConnectionState.CONNECTED
            } else {
              _esp2State.value = ConnectionState.CONNECTED
            }
            onLog("ESP32#$slot", "Terhubung ke ESP32 #$slot ($devName)", false)
          }

          @Deprecated("Deprecated in API 33")
          override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
          ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleBytes(slot, String(bytes, Charsets.UTF_8))
          }

          override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
          ) {
            handleBytes(slot, String(value, Charsets.UTF_8))
          }
        },
        BluetoothDevice.TRANSPORT_LE,
      )

    if (slot == 1) gatt1 = gatt else gatt2 = gatt
  }

  private fun handleBytes(slot: Int, chunk: String) {
    val buf = if (slot == 1) rxBuf1 else rxBuf2
    buf.append(chunk)
    while (true) {
      val idx = buf.indexOf("\n")
      if (idx < 0) break
      val line = buf.substring(0, idx).trim()
      buf.delete(0, idx + 1)
      if (line.isNotEmpty()) {
        if (slot == 1) parseEsp1Line(line) else parseEsp2Line(line)
      }
    }
  }

  private fun parseEsp1Line(line: String) {
    onLog("ESP1_RX", line, false)
    val eq = line.indexOf('=')
    if (eq <= 0) return
    val key = line.substring(0, eq).trim().uppercase()
    val valStr = line.substring(eq + 1).trim()
    val num = valStr.toFloatOrNull()
    val now = System.currentTimeMillis()
    val cur = _esp1Data.value
    _esp1Data.value =
      when (key) {
        "SPD" -> cur.copy(speedKmh = num, lastUpdatedMs = now)
        "TRT" -> cur.copy(throttlePct = num?.coerceIn(0f, 100f), lastUpdatedMs = now)
        "CRZ" -> cur.copy(cruiseActive = valStr == "1" || valStr.equals("ON", true), lastUpdatedMs = now)
        "REM" -> cur.copy(reverseActive = valStr == "1" || valStr.equals("ON", true), lastUpdatedMs = now)
        "STD" -> cur.copy(sideStandDown = valStr == "1" || valStr.equals("ON", true), lastUpdatedMs = now)
        "MODE" -> cur.copy(modeIndex = num?.toInt(), lastUpdatedMs = now)
        "PARK" -> cur.copy(isPark = valStr == "1", lastUpdatedMs = now)
        "CONTACT" -> cur.copy(contactOn = valStr == "1" || valStr.equals("ON", true), lastUpdatedMs = now)
        "MATRIX" -> cur.copy(matrixOn = valStr == "1" || valStr.equals("ON", true), lastUpdatedMs = now)
        "MUSIC_TRACK" -> cur.copy(musicTrack = num?.toInt() ?: cur.musicTrack, lastUpdatedMs = now)
        "MUSIC_STATE" -> cur.copy(musicState = valStr.uppercase(), lastUpdatedMs = now)
        "MUSIC_VOL" -> cur.copy(musicVol = num?.toInt() ?: cur.musicVol, lastUpdatedMs = now)
        else -> cur
      }
  }

  private fun parseEsp2Line(line: String) {
    onLog("ESP2_RX", line, false)
    val eq = line.indexOf('=')
    if (eq <= 0) return
    val key = line.substring(0, eq).trim().uppercase()
    val valStr = line.substring(eq + 1).trim()
    val num = valStr.toFloatOrNull()
    val now = System.currentTimeMillis()
    val cur = _battery2Data.value

    val updated =
      when (key) {
        "VBAT", "PACK_V", "V2" -> cur.copy(vbat = num, lastUpdatedMs = now)
        "IBAT", "PACK_I", "I2" -> {
          val adj = num?.let { if (invertB2Current) -it else it }
          cur.copy(ibat = adj, lastUpdatedMs = now)
        }
        "PWR", "P2" -> cur.copy(powerW = num, lastUpdatedMs = now)
        "SOC", "SOC2" -> cur.copy(socPct = num?.coerceIn(0f, 100f), lastUpdatedMs = now)
        "SOH", "SOH2" -> cur.copy(sohPct = num?.coerceIn(0f, 100f), lastUpdatedMs = now)
        "TBAT", "TEMP", "T2" -> cur.copy(tempC = num, lastUpdatedMs = now)
        "DELTA", "DCELL" -> cur.copy(deltaCellV = num, lastUpdatedMs = now)
        "CELLS" -> {
          val parsedCells = valStr.split(',').mapNotNull { it.trim().toFloatOrNull() }
          val minC = parsedCells.minOrNull()
          val maxC = parsedCells.maxOrNull()
          val delta = if (minC != null && maxC != null) maxC - minC else cur.deltaCellV
          cur.copy(
            cellCount = parsedCells.size,
            cellVoltages = parsedCells,
            deltaCellV = delta,
            lastUpdatedMs = now,
          )
        }
        "CYCLES", "CBAT" -> cur.copy(cycles = num?.toInt(), lastUpdatedMs = now)
        "REMAIN_AH" -> cur.copy(remainAh = num, lastUpdatedMs = now)
        "CANMODE" -> cur.copy(canMode = valStr.uppercase(), lastUpdatedMs = now)
        "SIMMODE" -> cur.copy(simMode = valStr, lastUpdatedMs = now)
        else -> cur
      }
    _battery2Data.value = updated
  }

  @SuppressLint("MissingPermission")
  fun sendCommand(slot: Int, cmd: String) {
    val g = if (slot == 1) gatt1 else gatt2
    val ch = if (slot == 1) write1 else write2
    if (g == null || ch == null) {
      onLog("ESP32#$slot", "Perangkat belum terhubung.", true)
      return
    }
    val payload = if (cmd.endsWith("\n")) cmd else "$cmd\n"
    try {
      @Suppress("DEPRECATION")
      ch.value = payload.toByteArray(Charsets.UTF_8)
      @Suppress("DEPRECATION")
      g.writeCharacteristic(ch)
      onLog("ESP32#$slot", "TX: ${cmd.trim()}", false)
    } catch (e: Exception) {
      onLog("ESP32#$slot", "Gagal kirim: ${e.message}", true)
    }
  }

  fun disconnectSlot(slot: Int) {
    cleanupSlot(slot)
  }

  @SuppressLint("MissingPermission")
  private fun cleanupSlot(slot: Int) {
    if (slot == 1) {
      try {
        gatt1?.disconnect()
        gatt1?.close()
      } catch (_: Exception) {}
      gatt1 = null
      write1 = null
      rxBuf1.clear()
      _esp1State.value = ConnectionState.DISCONNECTED
    } else {
      try {
        gatt2?.disconnect()
        gatt2?.close()
      } catch (_: Exception) {}
      gatt2 = null
      write2 = null
      rxBuf2.clear()
      _esp2State.value = ConnectionState.DISCONNECTED
    }
  }
}
