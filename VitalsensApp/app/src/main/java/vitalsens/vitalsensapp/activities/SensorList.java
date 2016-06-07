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
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vitalsens.vitalsensapp.R;

public class SensorList extends Activity {

    public static final String TAG = "SensorList";

    private static final String[] UUIDS = {"6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                                            "0000180D-0000-1000-8000-00805f9b34fb"};
    private static final long SCAN_PERIOD = 10000;
    private static final int MAX_SENSORS = 4;

    List<BluetoothDevice> mSensorList;
    private DeviceAdapter mDeviceAdapter;
    Map<String, Integer> mDevRssiValues;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private ArrayList<String> mSelectedSensors;
    private Handler mHandler;
    private boolean mScanning;
    private Button btnOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);
        setContentView(R.layout.activity_sensor_list);
        android.view.WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
        layoutParams.gravity= Gravity.TOP;
        layoutParams.y = 200;
        mHandler = new Handler();

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
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

        populateList();
        btnOK = (Button) findViewById(R.id.btn_ok);
        btnOK.setEnabled(false);
        Button btnScanCancel = (Button) findViewById(R.id.btn_cancel);

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLEScanner.stopScan(mLeScanCallback);

                Intent result = new Intent();
                result.putStringArrayListExtra("SENSOR_LIST", mSelectedSensors);
                ;
                setResult(Activity.RESULT_OK, result);
                finish();
            }
        });

        btnScanCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!mScanning){
                    scanLeDevice(true);
                }
                else finish();
            }
        });
    }

    private void populateList() {
        /* Initialize device list container */
        Log.d(TAG, "populateList");
        mSensorList = new ArrayList<>();
        mDeviceAdapter = new DeviceAdapter(this, mSensorList);
        mDevRssiValues = new HashMap<>();
        mSelectedSensors = new ArrayList<>();

        ListView newSensorsListView = (ListView) findViewById(R.id.new_sensors);
        newSensorsListView.setAdapter(mDeviceAdapter);
        newSensorsListView.setOnItemClickListener(mSensorClickListener);

        scanLeDevice(true);

    }

    private void scanLeDevice(final boolean enable) {
        final Button cancelButton = (Button) findViewById(R.id.btn_cancel);
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mLEScanner.stopScan(mLeScanCallback);

                    cancelButton.setText(R.string.scan);

                }
            }, SCAN_PERIOD);

            mScanning = true;
            mLEScanner.startScan(filters, settings, mLeScanCallback);
            cancelButton.setText(R.string.cancel);
        } else {
            mScanning = false;
            mLEScanner.stopScan(mLeScanCallback);
            cancelButton.setText(R.string.scan);
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
                            addSensor(result.getDevice(), result.getRssi());
                        }
                    });

                }
            });
        }
    };

    private void addSensor(BluetoothDevice sensor, int rssi) {
        boolean sensorFound = false;

        for (BluetoothDevice listDev : mSensorList) {
            if (listDev.getAddress().equals(sensor.getAddress())) {
                sensorFound = true;
                break;
            }
        }


        mDevRssiValues.put(sensor.getAddress(), rssi);
        if (!sensorFound) {
            mSensorList.add(sensor);




            mDeviceAdapter.notifyDataSetChanged();
        }
    }

    private OnItemClickListener mSensorClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String sensor = mSensorList.get(position).getAddress();
            if(!mSelectedSensors.contains(sensor) && mSelectedSensors.size() < MAX_SENSORS) {
                mSelectedSensors.add(sensor);
                view.setBackgroundResource(R.drawable.device_element_sel_bg);
                if(!btnOK.isEnabled())
                    btnOK.setEnabled(true);
            }
            else{
                mSelectedSensors.remove(sensor);
                view.setBackgroundResource(R.drawable.device_element_bg);
                if(mSelectedSensors.isEmpty() && btnOK.isEnabled())
                    btnOK.setEnabled(false);
            }
        }
    };

    class DeviceAdapter extends BaseAdapter {
        Context context;
        List<BluetoothDevice> sensors;
        LayoutInflater inflater;

        public DeviceAdapter(Context context, List<BluetoothDevice> sensors) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.sensors = sensors;
        }

        @Override
        public int getCount() {
            return sensors.size();
        }

        @Override
        public Object getItem(int position) {
            return sensors.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.sensor_element, null);
            }

            BluetoothDevice sensor = sensors.get(position);
            final TextView tvadd = ((TextView) vg.findViewById(R.id.address));
            final TextView tvname = ((TextView) vg.findViewById(R.id.name));
            final TextView tvpaired = (TextView) vg.findViewById(R.id.paired);
            final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);

            tvrssi.setVisibility(View.VISIBLE);
            byte rssival = (byte) mDevRssiValues.get(sensor.getAddress()).intValue();
            if (rssival != 0) {
                tvrssi.setText("Rssi = " + String.valueOf(rssival));
            }

            tvname.setText(sensor.getName());
            tvadd.setText(sensor.getAddress());
            if (sensor.getBondState() == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "sensor::" + sensor.getName());
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setTextColor(Color.GRAY);
                tvpaired.setVisibility(View.VISIBLE);
                tvpaired.setText(R.string.paired);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);

            } else {
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setVisibility(View.GONE);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);
            }
            return vg;
        }
    }
}
