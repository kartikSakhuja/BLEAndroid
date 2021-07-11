package kartiksakhuja.hexoskin_heart_rate_monitor

import android.app.Service
import android.os.IBinder
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Binder
import android.util.Log


class BLEService : Service() {
    private val mBinder: IBinder = LocalBinder()
    private var mGatt: BluetoothGatt? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        close()
        return super.onUnbind(intent)
    }

    inner class LocalBinder : Binder() {
        val service: BLEService
            get() = this@BLEService
    }

    /**
     * Initialize the bluetoothAdapter [BluetoothAdapter]
     * @return false when bluetooth initialization wasn't successful, true otherwise
     */
    fun initBluetooth(): Boolean {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.adapter
            true
        } else {
            Log.e(TAG, "Bluetooth adapter init didn't work")
            false
        }
    }

    /**
     * Connect to a BLE device
     * @param address MAC address
     * @return true if the connection was successful, false otherwise
     */
    fun connectToDevice(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "No Bluetooth adapter or no address")
            return false
        }
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        DeviceScreenActivity.runOnUI {
            mGatt = device.connectGatt(this, true, gattClientCallback)
            Log.d(TAG, "Connecting to selected device")
        }
        return true
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    fun close() {
        if (mGatt != null) {
            mGatt!!.close()
            mGatt = null
        }
    }

    /**
     * Disconnect the gatt server when a connection state changed happened and failed
     * i.e. when GATT_FAILED or !GATT_SUCCESS or STATE_DISCONNECTED
     */
    fun disconnectGattServer() {
        if (mGatt != null) {
            mGatt!!.disconnect()
            mGatt!!.close()
        }
    }

    /**
     * Gatt client callback: append the text view, show/hide contextual buttons
     * and discover services
     */
    private val gattClientCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val intentAction: String
            if (status == BluetoothGatt.GATT_FAILURE) {
                DeviceScreenActivity.runOnUI {
                    Log.d(TAG, "Connection failure, disconnected from server")
                    disconnectGattServer()
                }
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                DeviceScreenActivity.runOnUI {
                    Log.d(TAG, "Connection failure, disconnected from server")
                    disconnectGattServer()
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_CONNECTED
                broadcastUpdate(intentAction)
                DeviceScreenActivity.runOnUI {
                    Log.d(TAG, "Success: connecting to gatt and discovering services")
                    Log.i(TAG, "Attempting to start service discovery: " +
                            gatt.discoverServices())
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_DISCONNECTED
                broadcastUpdate(intentAction)
                DeviceScreenActivity.runOnUI {
                    Log.d(TAG, "Connection failure, disconnected from server")
                    disconnectGattServer()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Device service discovery unsuccessful, status $status")
                return
            }
            gatt.services
            setHeartRateNotification(gatt, true)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
                                       status: Int) {
            val characteristic = gatt.getService(GattAttributes.HEART_RATE_SERVICE_UUID)
                    .getCharacteristic(GattAttributes.HEART_RATE_CONTROL_POINT_CHAR_UUID)

            /* Write to Heart Rate Control Point Characteristic to get data streaming */characteristic.value = byteArrayOf(1, 1)
            gatt.writeCharacteristic(characteristic)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    /**
     * Enables the notification mode, writing the descriptor tells the sensor to
     * start streaming data
     * @param gatt [BluetoothGatt]
     * @param enabled set notification on for the heart rate service
     */
    fun setHeartRateNotification(gatt: BluetoothGatt,
                                 enabled: Boolean) {
        val characteristic = gatt.getService(GattAttributes.HEART_RATE_SERVICE_UUID)
                .getCharacteristic(GattAttributes.HEART_RATE_MEASUREMENT_CHAR_UUID)

        /* Enable notification  on the heart rate measurement characteristic */gatt.setCharacteristicNotification(characteristic, enabled)
        val descriptor = characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    /**
     * Notify DeviceActivity of key events happening in BluetoothLeService
     * @param action ACTION_CONNECTED, ACTION_DISCONNECTED, ACTION_DATA_AVAILABLE
     */
    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    /**
     * Notify DeviceActivity of key events happening in BluetoothLeService
     * @param action ACTION_CONNECTED, ACTION_DISCONNECTED, ACTION_DATA_AVAILABLE
     * @param characteristic [BluetoothGattCharacteristic]
     */
    private fun broadcastUpdate(action: String,
                                characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        /* Following heart rate profile specification */if (GattAttributes.HEART_RATE_MEASUREMENT_CHAR_UUID == characteristic.uuid) {
            val flag = characteristic.properties
            var format = -1
            if (flag and 0x01 != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16
                Log.d(TAG, "Heart rate format UINT16.")
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8
                Log.d(TAG, "Heart rate format UINT8.")
            }
            val heartRate = characteristic.getIntValue(format, 1)
            Log.d(TAG, String.format("Received heart rate: %d", heartRate))
            intent.putExtra(EXTRA_DATA_HEART_RATE, heartRate.toString())

            /* Get the battery level */
        } else if (GattAttributes.BATTERY_LEVEL_UUID == characteristic.uuid) {
            val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
            if (batteryLevel != 0) {
                Log.d(TAG, String.format("Received battery level: %d", batteryLevel))
                intent.putExtra(EXTRA_DATA_BATTERY, batteryLevel.toString())
            }
        }
        sendBroadcast(intent)
    }

    /**
     * Get the battery level from the battery service of the heart rate monitor
     */
    val battery: Unit
        get() {
            val batteryService = mGatt!!.getService(GattAttributes.BATTERY_SERVICE_UUID)
            if (batteryService == null) {
                Log.d(TAG, "Battery service not found!")
                return
            }
            val batteryLevel = batteryService.getCharacteristic(GattAttributes.BATTERY_LEVEL_UUID)
            if (batteryLevel == null) {
                Log.d(TAG, "Battery level not found!")
                return
            }
            Log.v(TAG, "batteryLevel = " + mGatt!!.readCharacteristic(batteryLevel))
            mGatt!!.readCharacteristic(batteryLevel)
        }

    companion object {
        const val ACTION_CONNECTED = "kartiksakhuja.heart_rate_monitor_ble.ACTION_GATT_CONNECTED"
        const val ACTION_DISCONNECTED = "kartiksakhuja.heart_rate_monitor_ble.ACTION_GATT_DISCONNECTED"
        const val ACTION_DATA_AVAILABLE = "kartiksakhuja.heart_rate_monitor_ble.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA_HEART_RATE = "kartiksakhuja.heart_rate_monitor_ble.EXTRA_DATA_HEART_RATE"
        const val EXTRA_DATA_BATTERY = "kartiksakhuja.heart_rate_monitor_ble.EXTRA_DATA_BATTERY"
        private val TAG = BLEService::class.java.simpleName
    }
}