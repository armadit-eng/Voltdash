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
import com.example.model.ConnectionState
import com.example.model.DiscoveredDevice
import com.example.model.JkBmsData
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JkBmsBleManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onLog: (source: String, msg: String, isError: Boolean) -> Unit,
) {
  companion object {
    val JK_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val JK_NOTIFY_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val JK_WRITE_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }

  private val btAdapter: BluetoothAdapter?
    get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _connectedName = MutableStateFlow("")
  val connectedName: StateFlow<String> = _connectedName.asStateFlow()

  private val _jkData = MutableStateFlow(JkBmsData())
  val jkData: StateFlow<JkBmsData> = _jkData.asStateFlow()

  private val _scannedDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val scannedDevices: StateFlow<List<DiscoveredDevice>> = _scannedDevices.asStateFlow()

  private var gatt: BluetoothGatt? = null
  private var writeChar: BluetoothGattCharacteristic? = null
  private var pollJob: Job? = null
  private var scanCb: ScanCallback? = null
  private val frameBuf = ByteArrayOutputStream()

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
      onLog("JKBMS", "Bluetooth tidak aktif atau izin belum diberikan.", true)
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
              dev.name ?: result.scanRecord?.deviceName ?: "JK-BMS"
            } catch (_: SecurityException) {
              "JK-BMS"
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
      onLog("JKBMS", "Gagal scan JK-BMS: ${e.message}", true)
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
  fun connect(address: String) {
    if (!hasPermission()) return
    val dev =
      try {
        btAdapter?.getRemoteDevice(address)
      } catch (_: Exception) {
        null
      } ?: return

    disconnect()
    stopScan()
    _connectionState.value = ConnectionState.CONNECTING
    val name =
      try {
        dev.name ?: address
      } catch (_: SecurityException) {
        address
      }
    _connectedName.value = name
    onLog("JKBMS", "Menghubungkan JK-BMS $name…", false)

    gatt =
      dev.connectGatt(
        context,
        false,
        object : BluetoothGattCallback() {
          override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
              g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
              onLog("JKBMS", "JK-BMS terputus.", true)
              cleanup()
            }
          }

          override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(JK_SERVICE_UUID)
            if (svc == null) {
              onLog("JKBMS", "UUID 0xFFE0 JK-BMS tidak ditemukan.", true)
              cleanup()
              return
            }
            val notifyChar = svc.getCharacteristic(JK_NOTIFY_UUID)
            writeChar = svc.getCharacteristic(JK_WRITE_UUID) ?: notifyChar
            if (notifyChar != null) {
              g.setCharacteristicNotification(notifyChar, true)
              val cccd = notifyChar.getDescriptor(CCCD_UUID)
              if (cccd != null) {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
              }
            }
            _connectionState.value = ConnectionState.CONNECTED
            onLog("JKBMS", "Terhubung ke JK-BMS ($name)", false)
            startPolling()
          }

          @Deprecated("Deprecated in API 33")
          override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
          ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            onBytesReceived(data)
          }

          override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
          ) {
            onBytesReceived(value)
          }
        },
        BluetoothDevice.TRANSPORT_LE,
      )
  }

  private fun startPolling() {
    pollJob?.cancel()
    pollJob =
      scope.launch(Dispatchers.IO) {
        delay(300)
        // Handshake 0x97 (device info) then poll 0x96 (cell & pack data) every 2.5s
        sendJkCommand(0x97)
        delay(450)
        while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
          sendJkCommand(0x96)
          delay(2500)
        }
      }
  }

  @SuppressLint("MissingPermission")
  private fun sendJkCommand(reg: Int) {
    val g = gatt ?: return
    val ch = writeChar ?: return
    val cmd = ByteArray(20)
    cmd[0] = 0xAA.toByte()
    cmd[1] = 0x55.toByte()
    cmd[2] = 0x90.toByte()
    cmd[3] = 0xEB.toByte()
    cmd[4] = reg.toByte()
    cmd[5] = 0x00
    var sum = 0
    for (i in 0 until 19) {
      sum += cmd[i].toInt() and 0xFF
    }
    cmd[19] = (sum and 0xFF).toByte()
    try {
      @Suppress("DEPRECATION")
      ch.value = cmd
      @Suppress("DEPRECATION")
      g.writeCharacteristic(ch)
    } catch (e: Exception) {
      onLog("JKBMS", "Gagal kirim cmd 0x${reg.toString(16)}: ${e.message}", true)
    }
  }

  private fun onBytesReceived(chunk: ByteArray) {
    // Check header 55 AA EB 90
    if (
      chunk.size >= 4 &&
        (chunk[0].toInt() and 0xFF) == 0x55 &&
        (chunk[1].toInt() and 0xFF) == 0xAA &&
        (chunk[2].toInt() and 0xFF) == 0xEB &&
        (chunk[3].toInt() and 0xFF) == 0x90
    ) {
      frameBuf.reset()
    }
    frameBuf.write(chunk)
    val bytes = frameBuf.toByteArray()
    if (bytes.size >= 300) {
      frameBuf.reset()
      parse300ByteFrame(bytes.copyOfRange(0, 300))
    }
  }

  private fun parse300ByteFrame(buf: ByteArray) {
    if (
      (buf[0].toInt() and 0xFF) != 0x55 ||
        (buf[1].toInt() and 0xFF) != 0xAA ||
        (buf[2].toInt() and 0xFF) != 0xEB ||
        (buf[3].toInt() and 0xFF) != 0x90
    ) {
      return
    }
    if ((buf[4].toInt() and 0xFF) != 0x02) return // only cell/pack frame

    var crc = 0
    for (i in 0 until 299) crc += (buf[i].toInt() and 0xFF)
    if ((crc and 0xFF) != (buf[299].toInt() and 0xFF)) {
      onLog("JKBMS", "Checksum frame JK-BMS tidak cocok", true)
      return
    }

    fun u16le(off: Int): Int =
      (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    fun i16le(off: Int): Int {
      val v = u16le(off)
      return if (v >= 0x8000) v - 0x10000 else v
    }

    fun u32le(off: Int): Long =
      (buf[off].toLong() and 0xFF) or
        ((buf[off + 1].toLong() and 0xFF) shl 8) or
        ((buf[off + 2].toLong() and 0xFF) shl 16) or
        ((buf[off + 3].toLong() and 0xFF) shl 24)

    fun i32le(off: Int): Long {
      val v = u32le(off)
      return if (v >= 0x80000000L) v - 0x100000000L else v
    }

    val cells = mutableListOf<Float>()
    for (i in 0 until 24) {
      val mv = u16le(6 + i * 2)
      if (mv in 1500..5000) cells.add(mv / 1000f)
    }

    val vpack24 = u32le(118) * 0.001f
    val vpack32 = u32le(134) * 0.001f
    val cellSum = cells.sum()
    val use32SOffset = abs(cellSum - vpack32) < abs(cellSum - vpack24)
    val off = if (use32SOffset) 16 else 0

    val vpack = u32le(118 + off) * 0.001f
    val ibat = i32le(126 + off) * 0.001f
    val temp1 = i16le(130 + off) * 0.1f
    val temp2 = i16le(132 + off) * 0.1f
    val tempMos = i16le(112 + off) * 0.1f
    val remainAh = u32le(142 + off) * 0.001f
    val capAh = u32le(146 + off) * 0.001f
    val cycles = u32le(150 + off).toInt()
    val soc = (buf[141 + off].toInt() and 0xFF).toFloat().coerceIn(0f, 100f)
    val soh = (buf[158 + off].toInt() and 0xFF).takeIf { it in 1..100 }?.toFloat()

    val minCell = cells.minOrNull()
    val maxCell = cells.maxOrNull()
    val minIdx = if (minCell != null) cells.indexOf(minCell) else -1
    val maxIdx = if (maxCell != null) cells.indexOf(maxCell) else -1
    val delta = if (minCell != null && maxCell != null) (maxCell - minCell) else null

    _jkData.value =
      JkBmsData(
        vpack = vpack,
        ibat = ibat,
        socPct = soc,
        sohPct = soh,
        temp1C = temp1,
        temp2C = temp2,
        tempMosC = tempMos,
        deltaCellV = delta,
        minCellV = minCell,
        maxCellV = maxCell,
        minCellIndex = minIdx,
        maxCellIndex = maxIdx,
        cellCount = cells.size,
        cellVoltages = cells,
        cycles = cycles,
        remainAh = remainAh,
        capacityAh = capAh,
        lastUpdatedMs = System.currentTimeMillis(),
      )
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    stopScan()
    cleanup()
    onLog("JKBMS", "JK-BMS diputus.", false)
  }

  @SuppressLint("MissingPermission")
  private fun cleanup() {
    pollJob?.cancel()
    frameBuf.reset()
    try {
      gatt?.disconnect()
      gatt?.close()
    } catch (_: Exception) {}
    gatt = null
    writeChar = null
    _connectionState.value = ConnectionState.DISCONNECTED
  }
}
