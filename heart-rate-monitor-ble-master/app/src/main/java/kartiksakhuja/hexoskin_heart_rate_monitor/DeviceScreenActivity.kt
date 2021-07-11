package kartiksakhuja.hexoskin_heart_rate_monitor

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kartiksakhuja.hexoskin_heart_rate_monitor.BLEService.LocalBinder


class DeviceScreenActivity : AppCompatActivity() {
    private var mBLEService: BLEService? = null
    private var mDeviceAddress: String? = null
    var batteryLevelTextView: TextView? = null
    var heartRateTextView: TextView? = null
    var heartRateChart: LineChart? = null
    var actualHeartRateValue = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        val intent = intent
        val mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)
        this.title = mDeviceName
        Log.d(TAG, "Device address: $mDeviceAddress")
        heartRateTextView = findViewById(R.id.heartRateValueText)
        heartRateChart = findViewById(R.id.heartRateChart)
        setHeartRateChart()
        val bleServiceIntent = Intent(this, BLEService::class.java)
        bindService(bleServiceIntent, mServiceConnection, BIND_AUTO_CREATE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_disconnect, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.disconnect -> {
                val intent = Intent(this, DeviceScanScreenActivity::class.java)
                mBLEService!!.disconnectGattServer()
                startActivity(intent)
                Log.d(TAG, "Going back to scanning activity")
                finish()
                super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mGattUpdateReceiver, makeUpdateIntentFilter())
        if (mBLEService != null) {
            val result = mBLEService!!.connectToDevice(mDeviceAddress)
            Log.d(TAG, "The connection was = $result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mGattUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mBLEService = null
    }

    private fun ClearTextViews() {
        batteryLevelTextView!!.setText(R.string.no_battery_level)
        heartRateTextView!!.setText(R.string.no_data)
    }

    /**
     * Bind to a service to interact with it and perform interprocess communication (IPC)
     */
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBLEService = (service as LocalBinder).service
            if (!mBLEService!!.initBluetooth()) {
                Log.e(TAG, "Failure to start bluetooth")
                finish()
            }

            /* Automatically connects to the device upon successful start-up initialization. */mBLEService!!.connectToDevice(mDeviceAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBLEService = null
        }
    }

    /**
     * Handles various events fired by the Service: ACTION_GATT_CONNECTED, ACTION_GATT_DISCONNECTED,
     * ACTION_GATT_SERVICES_DISCOVERED ACTION_DATA_AVAILABLE. This can be a
     * result of read or notification operations.
     */
    private val mGattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BLEService.ACTION_CONNECTED == action) {
                Log.d(TAG, "ACTION_CONNECTED")
                updateConnectionState(R.string.connection_success)
            } else if (BLEService.ACTION_DISCONNECTED == action) {
                Log.d(TAG, "ACTION_DISCONNECTED")
                updateConnectionState(R.string.connection_failure)
                ClearTextViews()
            } else if (BLEService.ACTION_DATA_AVAILABLE == action) {
                Log.d(TAG, "ACTION_DATA_AVAILABLE")
                mBLEService!!.battery
                displayHeartRateData(intent.getStringExtra(BLEService.EXTRA_DATA_HEART_RATE))
                displayBatteryData(intent.getStringExtra(BLEService.EXTRA_DATA_BATTERY))
            }
        }
    }

    private fun updateConnectionState(resourceId: Int) {
        Log.d(TAG, "New connection state is: $resourceId")
    }

    private fun displayHeartRateData(data: String?) {
        if (data != null) {
            Log.d(TAG, "Heart rate received: $data")
            heartRateTextView!!.text = data
            actualHeartRateValue = data.toInt()
            addHeartRateEntry()
        }
    }

    private fun displayBatteryData(data: String?) {
        if (data != null) {
            Log.d(TAG, "Battery level received: $data")
            batteryLevelTextView!!.text = getString(R.string.battery_value, data.toString())
        }
    }

    /**
     * Set all parameters relative to the heart rate chart
     */
    fun setHeartRateChart() {
        heartRateChart!!.description.isEnabled = true
        heartRateChart!!.setDrawGridBackground(false)
        heartRateChart!!.setBackgroundColor(Color.TRANSPARENT)
        val heartRateData = LineData()
        heartRateChart!!.data = heartRateData
        heartRateChart!!.minimumHeight = 350
        val xAxis = heartRateChart!!.xAxis
        xAxis.textColor = Color.WHITE
        xAxis.setDrawGridLines(false)
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.isEnabled = true
        val yAxis = heartRateChart!!.axisLeft
        yAxis.textColor = Color.WHITE
        yAxis.axisMaximum = 140f
        yAxis.axisMinimum = 50f
        yAxis.setDrawGridLines(true)
        val legend = heartRateChart!!.legend
        legend.isEnabled = false
        val rightAxis = heartRateChart!!.axisRight
        rightAxis.isEnabled = false
    }

    /**
     * Every time a new heart rate value is received, it is added to the real time chart
     */
    private fun addHeartRateEntry() {
        val data = heartRateChart!!.data
        if (data != null) {
            var set = data.getDataSetByIndex(0)
            if (set == null) {
                set = createSet()
                data.addDataSet(set)
            }
            data.addEntry(Entry(set.entryCount.toFloat(), actualHeartRateValue.toFloat()), 0)
            data.notifyDataChanged()
            heartRateChart!!.notifyDataSetChanged()
            heartRateChart!!.setVisibleXRangeMaximum(120f)
            heartRateChart!!.moveViewToX(data.entryCount.toFloat())
        }
    }

    /**
     * Set the parameters for the LineDataSet
     * @return a [LineDataSet] for the heart rate chart
     */
    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Heart Rate Data")
        set.axisDependency = YAxis.AxisDependency.LEFT
        set.color = Color.RED
        set.setCircleColor(Color.RED)
        set.lineWidth = 2f
        set.circleRadius = 1f
        set.fillAlpha = 65
        set.fillColor = Color.RED
        set.highLightColor = Color.RED
        set.valueTextColor = Color.RED
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    companion object {
        private val TAG = DeviceScreenActivity::class.java.simpleName
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        private fun makeUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(BLEService.ACTION_CONNECTED)
            intentFilter.addAction(BLEService.ACTION_DISCONNECTED)
            intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE)
            return intentFilter
        }

        var UIHandler: Handler? = null
        fun runOnUI(runnable: Runnable?) {
            UIHandler!!.post(runnable)
        }

        init {
            UIHandler = Handler(Looper.getMainLooper())
        }
    }
}