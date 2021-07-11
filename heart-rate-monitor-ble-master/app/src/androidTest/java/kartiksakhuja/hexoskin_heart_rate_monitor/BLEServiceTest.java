package kartiksakhuja.hexoskin_heart_rate_monitor;

import android.content.Intent;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class BLEServiceTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Test
    public void testBluetoothLeService() throws TimeoutException {
        Intent serviceIntent =
                new Intent(InstrumentationRegistry.getTargetContext(), BLEService.class);
        int batteryLvl = 90;

        serviceIntent.putExtra(BLEService.EXTRA_DATA_BATTERY, batteryLvl);

        IBinder binder = mServiceRule.bindService(serviceIntent);

        BLEService service = ((BLEService.LocalBinder) binder).getService();

        assertEquals(true, service.initBluetooth());
    }
}