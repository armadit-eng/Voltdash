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
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.ConnectionState
import com.example.model.DiscoveredDevice
import com.example.model.MotorData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MotorBluetoothManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onLog: (source: String, msg: String, isError: Boolean) -> Unit,
  private val onValidOdoBackup: (Float) -> Unit,
) {
  companion object {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val MOTOR_BLE_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val MOTOR_BLE_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val MOTOR_BLE_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
  }

  private val btAdapter: BluetoothAdapter?
    get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _connectedName = MutableStateFlow("")
  val connectedName: StateFlow<String> = _connectedName.asStateFlow()

  private val _motorData = MutableStateFlow(MotorData())
  val motorData: StateFlow<MotorData> = _motorData.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _mapsFramesSent = MutableStateFlow(0)
  val mapsFramesSent: StateFlow<Int> = _mapsFramesSent.asStateFlow()

  private val _mapsLastWriteMs = MutableStateFlow(0L)
  val mapsLastWriteMs: StateFlow<Long> = _mapsLastWriteMs.asStateFlow()

  private val _mapsLastChars = MutableStateFlow(0)
  val mapsLastChars: StateFlow<Int> = _mapsLastChars.asStateFlow()

  @Volatile private var mapsSending = false
  val isMapsSending: Boolean
    get() = mapsSending

  private var sppSocket: BluetoothSocket? = null
  private var sppOutput: OutputStream? = null
  private var readJob: Job? = null
  private var pollJob: Job? = null
  private var bleGatt: BluetoothGatt? = null
  private var bleWriteChar: BluetoothGattCharacteristic? = null
  private var bleScanCallback: ScanCallback? = null
  private var isBleTransport = false

  private val txQueue = ConcurrentLinkedQueue<String>()
  private var txJob: Job? = null
  private val rxBuffer = StringBuilder()
  private var pollCycle = 0

  fun hasBluetoothPermission(): Boolean {
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
  fun loadPairedAndScanBle() {
    val adapter = btAdapter
    if (adapter == null || !adapter.isEnabled) {
      onLog("MOTOR", "Bluetooth tidak aktif atau tidak tersedia.", true)
      return
    }
    if (!hasBluetoothPermission()) {
      onLog("MOTOR", "Izin Bluetooth belum diberikan.", true)
      return
    }

    val paired =
      try {
        adapter.bondedDevices?.map { dev ->
          DiscoveredDevice(
            name = dev.name ?: "Perangkat Tanpa Nama",
            address = dev.address,
            isBle = dev.type == BluetoothDevice.DEVICE_TYPE_LE,
            isPaired = true,
          )
        } ?: emptyList()
      } catch (_: SecurityException) {
        emptyList()
      }

    _discoveredDevices.value = paired

    val scanner = adapter.bluetoothLeScanner ?: return
    stopBleScan()
    val found = paired.toMutableList()
    val cb =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val dev = result.device ?: return
          val addr = dev.address ?: return
          val name =
            try {
              dev.name ?: result.scanRecord?.deviceName ?: "BLE Device"
            } catch (_: SecurityException) {
              "BLE Device"
            }
          if (found.none { it.address == addr }) {
            found.add(
              DiscoveredDevice(
                name = name,
                address = addr,
                isBle = true,
                rssi = result.rssi,
                isPaired = false,
              )
            )
            _discoveredDevices.value = found.toList()
          }
        }
      }
    bleScanCallback = cb
    try {
      scanner.startScan(cb)
      scope.launch {
        delay(8000)
        stopBleScan()
      }
    } catch (e: Exception) {
      onLog("MOTOR", "Gagal scan BLE: ${e.message}", true)
    }
  }

  @SuppressLint("MissingPermission")
  fun stopBleScan() {
    val cb = bleScanCallback ?: return
    bleScanCallback = null
    try {
      btAdapter?.bluetoothLeScanner?.stopScan(cb)
    } catch (_: Exception) {}
  }

  @SuppressLint("MissingPermission")
  fun connect(address: String, preferBle: Boolean = false) {
    if (!hasBluetoothPermission()) {
      onLog("MOTOR", "Izin Bluetooth diperlukan untuk koneksi.", true)
      return
    }
    val adapter = btAdapter ?: return
    val device =
      try {
        adapter.getRemoteDevice(address)
      } catch (e: Exception) {
        onLog("MOTOR", "Alamat perangkat tidak valid: $address", true)
        return
      }

    disconnect()
    stopBleScan()
    _connectionState.value = ConnectionState.CONNECTING
    val devName =
      try {
        device.name ?: address
      } catch (_: SecurityException) {
        address
      }
    _connectedName.value = devName
    onLog("MOTOR", "Menghubungkan ke $devName ($address)…", false)

    if (preferBle || device.type == BluetoothDevice.DEVICE_TYPE_LE) {
      connectBle(device)
    } else {
      connectSpp(device)
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectSpp(device: BluetoothDevice) {
    isBleTransport = false
    scope.launch(Dispatchers.IO) {
      try {
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        btAdapter?.cancelDiscovery()
        socket.connect()
        sppSocket = socket
        sppOutput = socket.outputStream
        _connectionState.value = ConnectionState.CONNECTED
        onLog("MOTOR", "Terhubung Classic SPP ke ${device.name ?: device.address}", false)
        startSppReader(socket)
        startTxAndPolling()
      } catch (e: Exception) {
        onLog("MOTOR", "Gagal SPP: ${e.message}. Mencoba BLE fallback…", true)
        connectBle(device)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectBle(device: BluetoothDevice) {
    isBleTransport = true
    bleGatt =
      device.connectGatt(
        context,
        false,
        object : BluetoothGattCallback() {
          override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
              onLog("MOTOR", "BLE terhubung, mencari layanan AT…", false)
              gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
              onLog("MOTOR", "Koneksi BLE Motor terputus.", true)
              cleanupConnection()
            }
          }

          override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service =
              gatt.getService(MOTOR_BLE_SERVICE)
                ?: gatt.services.firstOrNull {
                  it.characteristics.any { ch ->
                    (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                  }
                }
            if (service == null) {
              onLog("MOTOR", "Service serial BLE tidak ditemukan.", true)
              cleanupConnection()
              return
            }
            val notifyChar =
              service.getCharacteristic(MOTOR_BLE_CHAR)
                ?: service.characteristics.firstOrNull {
                  (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                }
            bleWriteChar =
              service.getCharacteristic(MOTOR_BLE_WRITE)
                ?: notifyChar
                ?: service.characteristics.firstOrNull {
                  (it.properties and
                    (BluetoothGattCharacteristic.PROPERTY_WRITE or
                      BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                }
            if (notifyChar != null) {
              gatt.setCharacteristicNotification(notifyChar, true)
              val cccd = notifyChar.getDescriptor(CCCD_UUID)
              if (cccd != null) {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
              }
            }
            _connectionState.value = ConnectionState.CONNECTED
            onLog("MOTOR", "Terhubung BLE ke ${device.name ?: device.address}", false)
            startTxAndPolling()
          }

          @Deprecated("Deprecated in API 33")
          override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
          ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingChunk(String(bytes, Charsets.UTF_8))
          }

          override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
          ) {
            handleIncomingChunk(String(value, Charsets.UTF_8))
          }
        },
        BluetoothDevice.TRANSPORT_LE,
      )
  }

  private fun startSppReader(socket: BluetoothSocket) {
    readJob?.cancel()
    readJob =
      scope.launch(Dispatchers.IO) {
        try {
          val reader = BufferedReader(InputStreamReader(socket.inputStream))
          while (isActive) {
            val line = reader.readLine() ?: break
            parseAtLine(line)
          }
        } catch (e: Exception) {
          if (isActive) onLog("MOTOR", "Koneksi SPP terputus: ${e.message}", true)
        } finally {
          cleanupConnection()
        }
      }
  }

  private fun handleIncomingChunk(chunk: String) {
    rxBuffer.append(chunk)
    while (true) {
      val idx = rxBuffer.indexOf("\n")
      if (idx < 0) break
      val line = rxBuffer.substring(0, idx).trim()
      rxBuffer.delete(0, idx + 1)
      if (line.isNotEmpty()) parseAtLine(line)
    }
  }

  private fun startTxAndPolling() {
    txJob?.cancel()
    pollJob?.cancel()
    txJob =
      scope.launch(Dispatchers.IO) {
        while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
          val cmd = txQueue.poll()
          if (cmd != null) {
            writeRaw(cmd)
            delay(55)
          } else {
            delay(25)
          }
        }
      }

    // Sync clock upon connect
    val nowCal = java.util.Calendar.getInstance()
    val hh = String.format("%02d", nowCal.get(java.util.Calendar.HOUR_OF_DAY))
    val mm = String.format("%02d", nowCal.get(java.util.Calendar.MINUTE))
    sendCommand("AT+TIME=\"$hh$mm\"")

    pollJob =
      scope.launch(Dispatchers.IO) {
        while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
          // Fast dynamic telemetry every cycle (~500ms)
          enqueueIfMissing("AT+SPD?")
          enqueueIfMissing("AT+IBAT?")
          enqueueIfMissing("AT+VBAT?")
          enqueueIfMissing("AT+TRT?")
          if (pollCycle % 2 == 0) {
            enqueueIfMissing("AT+SOC?")
            enqueueIfMissing("AT+ERR?")
            enqueueIfMissing("AT+MODE?")
            enqueueIfMissing("AT+PARK?")
          }
          if (pollCycle % 6 == 0) {
            enqueueIfMissing("AT+CBAT?")
            enqueueIfMissing("AT+ODO?")
            enqueueIfMissing("AT+TINV?")
            enqueueIfMissing("AT+TMOT?")
            enqueueIfMissing("AT+TBAT?")
            enqueueIfMissing("AT+SOH?")
          }
          pollCycle++
          delay(500)
        }
      }
  }

  private fun enqueueIfMissing(cmd: String) {
    if (txQueue.size < 32 && !txQueue.contains(cmd)) {
      txQueue.offer(cmd)
    }
  }

  fun sendCommand(cmd: String) {
    if (_connectionState.value != ConnectionState.CONNECTED) return
    val normalized = if (cmd.endsWith("\r\n")) cmd else "$cmd\r\n"
    if (txQueue.size < 40) txQueue.offer(normalized)
  }

  fun sendTripRange(rangeKm: Float) {
    sendCommand("AT+TRIP=\"${String.format(java.util.Locale.US, "%.1f", rangeKm)}\"")
  }

  fun sendNavText(rawText: String) {
    val ascii =
      rawText
        .replace(Regex("[^\\x20-\\x7E]"), " ")
        .trim()
        .take(20)
        .padEnd(20, ' ')
    sendCommand("AT+NAV=\"$ascii\"")
  }

  fun restoreOdo(odoKm: Float) {
    sendCommand("AT+ODO=\"${odoKm.toInt()}\"")
  }

  @SuppressLint("MissingPermission")
  fun sendMapsFrame(base64Jpeg: String, leChunkSize: Int = 100, leDelayMs: Int = 30) {
    if (_connectionState.value != ConnectionState.CONNECTED || mapsSending || base64Jpeg.isBlank()) {
      return
    }
    mapsSending = true
    scope.launch(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      try {
        if (!isBleTransport) {
          val cmd = "AT+MAPS=\"$base64Jpeg\"\r\n"
          sppOutput?.write(cmd.toByteArray(Charsets.UTF_8))
          sppOutput?.flush()
          val elapsed = System.currentTimeMillis() - t0
          _mapsLastWriteMs.value = elapsed
          _mapsLastChars.value = base64Jpeg.length
          _mapsFramesSent.value = _mapsFramesSent.value + 1
          onLog("MOTOR", "-> AT+MAPS (SPP) ${base64Jpeg.length} chr · $elapsed ms", false)
        } else {
          val gatt = bleGatt ?: return@launch
          val ch = bleWriteChar ?: return@launch
          val chunkSize = leChunkSize.coerceIn(20, 400)
          val gap = leDelayMs.coerceIn(0, 300).toLong()

          @Suppress("DEPRECATION")
          ch.value = "AT+MAPS=\"".toByteArray(Charsets.UTF_8)
          @Suppress("DEPRECATION")
          gatt.writeCharacteristic(ch)
          delay(50)

          var offset = 0
          var chunks = 0
          while (offset < base64Jpeg.length && _connectionState.value == ConnectionState.CONNECTED) {
            val end = minOf(offset + chunkSize, base64Jpeg.length)
            val part = base64Jpeg.substring(offset, end)
            @Suppress("DEPRECATION")
            ch.value = part.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
            chunks++
            offset = end
            if (gap > 0) delay(gap)
          }

          @Suppress("DEPRECATION")
          ch.value = "\"\r\n".toByteArray(Charsets.UTF_8)
          @Suppress("DEPRECATION")
          gatt.writeCharacteristic(ch)

          val elapsed = System.currentTimeMillis() - t0
          _mapsLastWriteMs.value = elapsed
          _mapsLastChars.value = base64Jpeg.length
          _mapsFramesSent.value = _mapsFramesSent.value + 1
          onLog("MOTOR", "-> AT+MAPS (LE) ${base64Jpeg.length} chr · $chunks potong · $elapsed ms", false)
        }
      } catch (e: Exception) {
        onLog("MOTOR", "Gagal kirim AT+MAPS: ${e.message}", true)
      } finally {
        mapsSending = false
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun writeRaw(cmd: String) {
    val line = if (cmd.endsWith("\r\n")) cmd else "$cmd\r\n"
    try {
      if (!isBleTransport) {
        sppOutput?.write(line.toByteArray(Charsets.UTF_8))
        sppOutput?.flush()
      } else {
        val gatt = bleGatt ?: return
        val ch = bleWriteChar ?: return
        val bytes = line.toByteArray(Charsets.UTF_8)
        @Suppress("DEPRECATION")
        ch.value = bytes
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(ch)
      }
    } catch (e: Exception) {
      onLog("MOTOR", "Gagal kirim ($cmd): ${e.message}", true)
    }
  }

  fun parseAtLine(rawLine: String) {
    val line = rawLine.trim()
    if (line.isEmpty() || line == "OK") return
    onLog("MOTOR_RX", line, false)
    val eq = line.indexOf('=')
    if (eq <= 0) return
    val key = line.substring(0, eq).trim().uppercase()
    val valStr = line.substring(eq + 1).trim().trim('"')
    val num = valStr.toFloatOrNull()
    val now = System.currentTimeMillis()

    val cur = _motorData.value
    val updated =
      when {
        key.contains("SPD") && num != null -> cur.copy(speedKmh = num.coerceAtLeast(0f), lastUpdatedMs = now)
        key.contains("VBAT") && num != null -> cur.copy(vbat = num, lastUpdatedMs = now)
        key.contains("IBAT") && num != null -> cur.copy(ibat = num, lastUpdatedMs = now)
        key.contains("TRT") && num != null -> cur.copy(throttlePct = num.coerceIn(0f, 100f), lastUpdatedMs = now)
        key.contains("SOC") && num != null -> cur.copy(socPct = num.coerceIn(0f, 100f), lastUpdatedMs = now)
        key.contains("SOH") && num != null -> cur.copy(sohPct = num.coerceIn(0f, 100f), lastUpdatedMs = now)
        key.contains("CBAT") && num != null -> cur.copy(cbatCycles = num.toInt(), lastUpdatedMs = now)
        key.contains("ODO") && num != null -> {
          if (num in 0f..399_999f) {
            onValidOdoBackup(num)
            cur.copy(odoKm = num, lastUpdatedMs = now)
          } else {
            onLog("MOTOR", "ODO korup diabaikan: $num", true)
            cur
          }
        }
        key.contains("ERR") -> {
          val parsedMask =
            valStr.toIntOrNull(16)
              ?: valStr.toIntOrNull()
              ?: 0
          cur.copy(errMask = parsedMask, lastUpdatedMs = now)
        }
        key.contains("MODE") && num != null -> cur.copy(modeIndex = num.toInt(), lastUpdatedMs = now)
        key.contains("PARK") && num != null -> cur.copy(isPark = num.toInt() == 1, lastUpdatedMs = now)
        key.contains("TINV") && num != null -> cur.copy(tempInverterC = num, lastUpdatedMs = now)
        key.contains("TMOT") && num != null -> cur.copy(tempMotorC = num, lastUpdatedMs = now)
        key.contains("TBAT") && num != null -> cur.copy(tempBatteryC = num, lastUpdatedMs = now)
        else -> cur
      }
    _motorData.value = updated
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    stopBleScan()
    cleanupConnection()
    onLog("MOTOR", "Motor diputus manual.", false)
  }

  @SuppressLint("MissingPermission")
  private fun cleanupConnection() {
    pollJob?.cancel()
    txJob?.cancel()
    readJob?.cancel()
    txQueue.clear()
    try {
      sppSocket?.close()
    } catch (_: Exception) {}
    sppSocket = null
    sppOutput = null
    try {
      bleGatt?.disconnect()
      bleGatt?.close()
    } catch (_: Exception) {}
    bleGatt = null
    bleWriteChar = null
    _connectionState.value = ConnectionState.DISCONNECTED
  }
}
