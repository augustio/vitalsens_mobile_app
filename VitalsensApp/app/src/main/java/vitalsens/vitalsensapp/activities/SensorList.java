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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    public static final String CURRENT_SENSOR_ID = "currentSensor";

    private static final String[] UUIDS = {"6e400001-b5a3-f393-e0a9-e50e24dcca9e", //UART Service UUID
                                            "0000180D-0000-1000-8000-00805f9b34fb"}; //Heart Rate Service UUID
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
    private Button btnConnect, btnScanCancel;
    private TextView btnBack;

    private String mSensorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sensor_list);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setEnabled(false);
        btnScanCancel = (Button) findViewById(R.id.btn_scan_cancel);
        btnBack = (TextView) findViewById(R.id.btn_back);

        mHandler = new Handler();

        mSensorList = new ArrayList<>();
        mDeviceAdapter = new DeviceAdapter(this, mSensorList);
        mDevRssiValues = new HashMap<>();
        mSelectedSensors = new ArrayList<>();

        Intent intent = getIntent();
        mSensorId = intent.getStringExtra(Intent.EXTRA_TEXT);

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

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLEScanner.stopScan(mLeScanCallback);

                Intent result = new Intent();
                result.putStringArrayListExtra("SENSOR_LIST", mSelectedSensors);
                setResult(Activity.RESULT_OK, result);
                finish();
            }
        });

        btnScanCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mScanning){
                    mSensorList.clear();
                    mDeviceAdapter.notifyDataSetChanged();
                    btnConnect.setEnabled(false);
                    scanLeDevice(true);
                }
                else{
                    scanLeDevice(false);
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(false);
                finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(CURRENT_SENSOR_ID, mSensorId);

        super.onSaveInstanceState(outState);
    }

    private void populateList() {
        /* Initialize device list container */
        Log.d(TAG, "populateList");
        ListView newSensorsListView = (ListView) findViewById(R.id.new_sensors);
        newSensorsListView.setAdapter(mDeviceAdapter);
        newSensorsListView.setOnItemClickListener(mSensorClickListener);

        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mLEScanner.stopScan(mLeScanCallback);
                    btnScanCancel.setText(R.string.scan);
                    if(mSensorList.size() == 0)
                        showMessage(mSensorId + " not found!");
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mLEScanner.startScan(filters, settings, mLeScanCallback);
            btnScanCancel.setText(R.string.cancel);
        } else {
            mScanning = false;
            mLEScanner.stopScan(mLeScanCallback);
            btnScanCancel.setText(R.string.scan);
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
        if (!sensorFound && sensor.getName().equals(mSensorId)) {
            mSensorList.add(sensor);
            scanLeDevice(false);
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
                if(!btnConnect.isEnabled())
                    btnConnect.setEnabled(true);
            }
            else{
                mSelectedSensors.remove(sensor);
                view.setBackgroundResource(R.drawable.device_element_bg);
                if(mSelectedSensors.isEmpty() && btnConnect.isEnabled())
                    btnConnect.setEnabled(false);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        scanLeDevice(false);
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);

    }
}
