package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


import vitalsens.vitalsensapp.R;

public class SensorList extends Activity {

    public static final String TAG = "SensorList";
    public static final String EXTRA_SENSOR_ADDRESSES = "SENSOR_ADDRESSES";
    public static final int DEVICE_NOT_FOUND = -100;

    private static final String[] UUIDS = {"6e400001-b5a3-f393-e0a9-e50e24dcca9e", //UART Service UUID
                                            "0000180D-0000-1000-8000-00805f9b34fb"}; //Heart Rate Service UUID
    private static final long SCAN_PERIOD = 10000;

    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private Handler mHandler;

    private String mSensorId;
    private ArrayList<String> mTargetSensorAddresses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sensor_list);

        ProgressBar mScanProgress = (ProgressBar) findViewById(R.id.scan_progress);
        TextView mScanning = (TextView) findViewById(R.id.scanning);
        mScanProgress.setVisibility(View.GONE);
        mScanning.setVisibility(View.GONE);

        mHandler = new Handler();

        Intent intent = getIntent();
        mSensorId = intent.getStringExtra(Intent.EXTRA_TEXT);

        mTargetSensorAddresses = new ArrayList<>();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<>();
        for (String uuid: UUIDS) {
            ScanFilter ecgFilter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(uuid)).build();

            filters.add(ecgFilter);
        }

        scanLeDevice(true);
        String scanStr = this.getString(R.string.scanning) + " " + mSensorId;
        mScanning.setText(scanStr);
        mScanning.setVisibility(View.VISIBLE);
        mScanProgress.setVisibility(View.VISIBLE);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mLeScanCallback);
                    if(mTargetSensorAddresses.size() < 1){
                        setResult(DEVICE_NOT_FOUND);
                        finish();
                    }
                }
            }, SCAN_PERIOD);

            mLEScanner.startScan(filters, settings, mLeScanCallback);
        } else {
            mLEScanner.stopScan(mLeScanCallback);
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BluetoothDevice device = result.getDevice();
                            if(device.getName().equalsIgnoreCase(mSensorId)){
                                mTargetSensorAddresses.add(device.getAddress());
                                scanLeDevice(false);
                                Intent result = new Intent();
                                result.putStringArrayListExtra(EXTRA_SENSOR_ADDRESSES, mTargetSensorAddresses);
                                setResult(Activity.RESULT_OK, result);
                                finish();
                            }
                        }
                    });

                }
            });
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        scanLeDevice(false);
    }
}
