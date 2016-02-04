package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.fragments.MainFragment;
import vitalsens.vitalsensapp.models.Sensor;
import vitalsens.vitalsensapp.services.BLEService;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private TextView connectedSensorsLabel;
    private Handler mHandler;
    private BLEService mService;
    private ArrayList<Sensor> mConnectedSensors;
    private int mConnectionState;
    private String mSensorLabels;

    private MainFragment mainFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainFrag = new MainFragment();
        getFragmentManager().beginTransaction()
                .add(R.id.main_container, mainFrag)
                .commit();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect=(Button) findViewById(R.id.btn_connect);
        connectedSensorsLabel = (TextView)findViewById(R.id.connected_sensors);

        mHandler = new Handler();
        mConnectedSensors = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mSensorLabels = "";

        service_init();

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                        //Connect button pressed, open SensorList class, with popup window that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, SensorList.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else if(mConnectionState == BLEService.STATE_CONNECTED) {
                        mService.disconnect(mConnectedSensors);
                    }
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<String> sensorAddresses = data.getStringArrayListExtra("SENSOR_LIST");
                    mService.connect(sensorAddresses);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    showMessage("Bluetooth has turned on ");

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    showMessage("Problem in BT Turning ON ");
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    //BLE service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((BLEService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BLEService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(SensorStatusChangeReceiver, sensorStatusUpdateIntentFilter());
    }

    private final BroadcastReceiver SensorStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BLEService.ACTION_GATT_CONNECTED)) {
                final String sensorStr = intent.getStringExtra(Intent.EXTRA_TEXT);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Sensor sensor = new Sensor();
                        sensor.fromJson(sensorStr);
                        Log.d(TAG, "Connected to " + sensor.getName() +
                                " : " + sensor.getAddress());
                        mConnectedSensors.add(sensor);
                        mConnectionState = BLEService.STATE_CONNECTED;
                        btnConnectDisconnect.setText("Disconnect");
                        if(!mSensorLabels.isEmpty())
                            mSensorLabels+=",  ";
                        connectedSensorsLabel.setText(mSensorLabels+=sensor.getName());
                    }
                });
            }
            if (action.equals(BLEService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "Disconnected from sensors");
                        mConnectionState = BLEService.STATE_DISCONNECTED;
                        btnConnectDisconnect.setText("Connect");
                        mConnectedSensors.clear();
                        mSensorLabels = "";
                        connectedSensorsLabel.setText(mSensorLabels);
                    }
                });
            }
            if (action.equals(BLEService.ACTION_GATT_SERVICES_DISCOVERED)) {
                final String service = intent.getStringExtra(Intent.EXTRA_TEXT);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "Gatt Services discovered");
                    }
                });
            }
        }

    };

    private static IntentFilter sensorStatusUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);

    }

    @Override
    public void onBackPressed() {
        if(mConnectionState == BLEService.STATE_CONNECTED){
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.disconnect_message)
                    .setPositiveButton(R.string.popup_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.quit_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }
}
