package kartiksakhuja.hexoskin_heart_rate_monitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import java.util.*

class DeviceScanScreenActivity : AppCompatActivity() {
    private var mArrayAdapter: ArrayBLEAdapter? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mScanCallback: ScanCallback? = null
    private var mDeviceListView: ListView? = null
    private var mScanning = false
    private var mHandler: Handler? = null
    var devicesDiscovered = ArrayList<BluetoothDevice>()
    var progressBar: ProgressBar? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_device)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.adapter
        }
        if (!checkBluetoothSupport(mBluetoothAdapter)) {
            finish()
        }
        progressBar = findViewById(R.id.progress_bar)
        mDeviceListView = findViewById(R.id.device_list)
        mArrayAdapter = ArrayBLEAdapter(this, devicesDiscovered)
        mDeviceListView!!.setAdapter(mArrayAdapter)
        mDeviceListView!!.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long -> onClickToConnect(position) })
    }

    override fun onResume() {
        super.onResume()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.v(TAG, "No LE Support.")
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
        devicesDiscovered.clear()
        mDeviceListView!!.adapter = mArrayAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_scan_device, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.scan -> {
                startScan()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * When the user click on a device to connect, intent extras are passed to the deviceActivity
     * and this activity is initiated
     */
    private fun onClickToConnect(position: Int) {
        if (devicesDiscovered.isEmpty()) return
        if (position >= devicesDiscovered.size) {
            Log.w(TAG, "Illegal position.")
            return
        }

        /* Ensure closing scanning before going to next activity */if (mScanning) {
            mScanning = false
            mBluetoothLeScanner!!.flushPendingScanResults(mScanCallback)
            mBluetoothLeScanner!!.stopScan(mScanCallback)
            SystemClock.sleep(500)
        }
        val selectedDevice = devicesDiscovered[position]
        runOnUiThread {
        }
        Log.d(TAG, "Connecting to device " + selectedDevice.name)
        val intent = Intent(this, DeviceScreenActivity::class.java)
        intent.putExtra(DeviceScreenActivity.EXTRAS_DEVICE_NAME, selectedDevice.name)
        intent.putExtra(DeviceScreenActivity.EXTRAS_DEVICE_ADDRESS, selectedDevice.address)
        startActivity(intent)
    }

    /**
     * Bluetooth scan that will save all ScanResults into a Arraylist
     */
    private fun startScan() {
        if (!hasPermissions() || mScanning) return
        devicesDiscovered.clear()
        mScanCallback = BleScanCallback(devicesDiscovered)
        mBluetoothLeScanner = mBluetoothAdapter!!.bluetoothLeScanner
        val filters: List<ScanFilter> = ArrayList()

        /* Only scan for BLE devices */
        val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
        mBluetoothLeScanner!!.startScan(filters, settings, mScanCallback)
        mHandler = Handler()
        mHandler!!.postDelayed({ stopScan() }, SCAN_PERIOD.toLong())
        progressBar!!.visibility = View.VISIBLE
        mScanning = true
        Log.d(TAG, "Started scanning.")
    }

    /**
     * Stop scanning for BLE devices after 5 seconds
     */
    private fun stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter!!.isEnabled && mBluetoothLeScanner != null) {
            mBluetoothLeScanner!!.stopScan(mScanCallback)
            scanComplete()
        }
        mScanCallback = null
        mScanning = false
        mHandler = null
    }

    private fun scanComplete() {
        if (devicesDiscovered.isEmpty()) {
            return
        }
        for (device in devicesDiscovered) {
            Log.d(TAG, "Found device: " + device.name + " " + device.address)
        }
        progressBar!!.visibility = View.INVISIBLE
    }

    /**
     * Verify bluetooth support on this hardware
     * @param bluetoothAdapter [BluetoothAdapter]
     * @return true if Bluetooth is properly supported, false otherwise
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }
        return true
    }

    /**
     * Verify permissions and enable bluetooth if it's not
     * @return false when Bluetooth or permission are not enabled
     */
    private fun hasPermissions(): Boolean {
        if (mBluetoothAdapter == null || !mBluetoothAdapter!!.isEnabled) {
            requestBluetoothEnable()
            return false
        } else if (!hasLocationPermissions()) {
            requestLocationPermission()
            return false
        }
        return true
    }

    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.")
    }

    private fun hasLocationPermissions(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
    }

    /**
     * Extends the ScanCallback class to add results in the Arraylist
     */
    private inner class BleScanCallback internal constructor(private val devicesDiscovered: ArrayList<BluetoothDevice>) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (devicesDiscovered.isEmpty()) {
                Log.d(TAG, "First device found: " + result.device.address)
                addLeScanResult(result)
                return
            }
            /* This would be better with a HashMap */if (!devicesDiscovered.contains(result.device)) {
                addLeScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed with code $errorCode")
        }

        private fun addLeScanResult(result: ScanResult) {
            devicesDiscovered.add(result.device)
            Log.d(TAG, "Added device: " + result.device.name
                    + ", with address: " + result.device.address)
            mDeviceListView!!.adapter = mArrayAdapter
        }
    }

    /**
     * Custom adapter for the [ListView]
     */
    private inner class ArrayBLEAdapter constructor(private val mContext: Context, list: List<BluetoothDevice>) : ArrayAdapter<BluetoothDevice?>(mContext, 0, list) {
        private val devices: List<BluetoothDevice>
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            if (convertView == null) convertView = LayoutInflater.from(mContext).inflate(R.layout.device_list,
                    parent, false)
            val currentDevice = devices[position]
            val deviceAddress = convertView!!.findViewById<TextView>(R.id.device_address)
            deviceAddress.text = currentDevice.address
            val deviceName = convertView.findViewById<TextView>(R.id.device_name)
            if (currentDevice.name != null) {
                deviceName.text = currentDevice.name
            } else {
                deviceName.setText(R.string.no_name)
            }
            return convertView
        }

        init {
            devices = list
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_COARSE_LOCATION = 1
        private val TAG = DeviceScanScreenActivity::class.java.simpleName
        private const val SCAN_PERIOD = 5000 /* 5 seconds scan period */
    }
}