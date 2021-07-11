package kartiksakhuja.hexoskin_heart_rate_monitor

import android.support.v7.app.AppCompatActivity
import java.util.*

object GattAttributes : AppCompatActivity() {
    var gattAttributes = HashMap<UUID, String>()
    var HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D)
    var HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37)
    var HEART_RATE_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2A39)
    var BATTERY_SERVICE_UUID = convertFromInteger(0x180F)
    var BATTERY_LEVEL_UUID = convertFromInteger(0x2A19)
    var CLIENT_CHARACTERISTIC_CONFIG_UUID = convertFromInteger(0x2902)

    /**
     * convert from an integer to UUID.
     * @param i integer input
     * @return UUID
     */
    @JvmStatic
    fun convertFromInteger(i: Int): UUID {
        val MSB = 0x0000000000001000L
        val LSB = -0x7fffff7fa064cb05L
        val value = (i and -0x1).toLong()
        return UUID(MSB or (value shl 32), LSB)
    }

    init {
        gattAttributes[HEART_RATE_SERVICE_UUID] = "Heart rate service"
        gattAttributes[HEART_RATE_MEASUREMENT_CHAR_UUID] = "Heart rate measurement char"
        gattAttributes[HEART_RATE_CONTROL_POINT_CHAR_UUID] = "Heart rate control point"
        gattAttributes[BATTERY_SERVICE_UUID] = "Battery service"
        gattAttributes[BATTERY_LEVEL_UUID] = "Battery level"
        gattAttributes[CLIENT_CHARACTERISTIC_CONFIG_UUID] = "Client char config"
    }
}