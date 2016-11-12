package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.fragments.ChannelOneFragment;
import vitalsens.vitalsensapp.fragments.ChannelThreeFragment;
import vitalsens.vitalsensapp.fragments.ChannelTwoFragment;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.services.BLEService;
import vitalsens.vitalsensapp.utils.ConnectDialog;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final String PATIENT_DEVICE_IDS_FILE_PATH = "patient_device_ids.txt";
    private static final int MAIN_LAYOUT = 0;
    private static final int ONE_CHANNEL_LAYOUT = 1;
    private static final int TWO_CHANNELS_LAYOUT = 2;
    private static final int THRE_CHANNELS_LAYOUT = 3;
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CONNECT_PARAMS = 3;
    private static final int MAX_RECORD_SEGMENT_DURATION = 60; //In milliseconds
    private static final int MAX_RECORD_DURATION = 1800; //In milliseconds
    private static final int MAX_REC_COUNT = 86400000; //Number of milliseconds in one day
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND_IN_MILLIS = 1000;
    private static final int PRE_RECORD_START_DURATION = 10000; //In milliseconds
    private static final int PRE_AUTO_RECONNECTION_PAUSE_DURATION = 5000; //In milliseconds

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private TextView connectedDevices, curDispDataType,
            batLevel, curTemperature, hrValue, patientId, batLevelTxt;
    private LinearLayout chOne, chTwo, chThree;
    private Handler mHandler;
    private BLEService mService;
    private ArrayList<Record> mRecords;
    private ArrayList<String> mAvailableDataTypes;
    private ArrayList<String> mSensorAddresses;
    private CountDownTimer mAutoConnectTimer;
    private CountDownTimer mRecordStartTimer;
    private int mConnectionState;
    private int min, sec, hr;
    private int mNextIndex;
    private long mRecStart, mRecSegmentStart;
    private int mRecTimerCounter;
    private int mNumConnectedSensors;
    private String mTimerString, mSensorId, mPatientId;;
    private boolean mShowECGOne, mShowECGThree, mShowPPGOne,
            mShowPPGTwo, mShowAccel, mShowImpedance;
    private boolean mRecording;
    private boolean mUserInitiatedDisconnection;
    private boolean mDataDisplayOn;
    private boolean mSamplesRecieved;
    private boolean mRecSegmentTimeUp, mRecTimeUp;
    private boolean mAutoConnectOn;
    private boolean mShowAnalysis;

    private ChannelOneFragment mChannelOne;
    private ChannelTwoFragment mChannelTwo;
    private ChannelThreeFragment mChannelThree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int samplesDispWindowSize;
        int screenSize = getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        switch(screenSize) {
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                samplesDispWindowSize = 1250;
                break;
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                samplesDispWindowSize = 675;
                break;
            default:
                samplesDispWindowSize = 600;
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect = (Button) findViewById(R.id.btn_connect);
        curTemperature = (TextView) findViewById(R.id.cur_temp);
        batLevel = (TextView) findViewById(R.id.bat_level);
        batLevelTxt = (TextView) findViewById(R.id.bat_level_text);
        hrValue = (TextView) findViewById(R.id.hr_value);
        patientId = (TextView) findViewById(R.id.patient_id);
        TextView btnInc = (TextView) findViewById(R.id.btn_inc);
        TextView btnDec = (TextView) findViewById(R.id.btn_dec);
        Button btnHistory = (Button) findViewById(R.id.btn_history);
        curDispDataType = (TextView) findViewById(R.id.cur_disp_dataType);
        connectedDevices = (TextView) findViewById(R.id.connected_devices);
        chOne = (LinearLayout) findViewById(R.id.channel1_fragment);
        chTwo = (LinearLayout) findViewById(R.id.channel2_fragment);
        chThree = (LinearLayout) findViewById(R.id.channel3_fragment);

        mHandler = new Handler();
        mRecordStartTimer = null;
        mAutoConnectTimer = null;
        mSensorId = "";
        mRecords = new ArrayList<>();
        mAvailableDataTypes = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mRecording = false;
        mUserInitiatedDisconnection = false;
        mDataDisplayOn = false;
        mSamplesRecieved = false;
        mShowAnalysis = false;
        mRecSegmentTimeUp = mRecTimeUp = false;
        mAutoConnectOn = false;

        min = sec =  hr = 0;
        mNextIndex = 0;
        mRecTimerCounter = 0;
        mNumConnectedSensors = 0;
        mTimerString = "";
        mPatientId = "";

        mChannelOne = new ChannelOneFragment();
        mChannelTwo = new ChannelTwoFragment();
        mChannelThree = new ChannelThreeFragment();

        mChannelOne.setxRange(samplesDispWindowSize);
        mChannelTwo.setxRange(samplesDispWindowSize);
        mChannelThree.setxRange(samplesDispWindowSize);

        FragmentManager fragmentManager = getFragmentManager();

        fragmentManager.beginTransaction()
                .add(R.id.channel1_fragment, mChannelOne, "chOne")
                .add(R.id.channel2_fragment, mChannelTwo, "chTwo")
                .add(R.id.channel3_fragment, mChannelThree, "chThree")
                .commit();

        setGraphLayout(MAIN_LAYOUT);

        service_init();

        getPatientAndDeviceIds();

        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    btnConnectDisconnect.setEnabled(false);
                    if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                        Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
                        ArrayList<String> connParams = new ArrayList<>();
                        connParams.add(mSensorId);
                        connParams.add(mPatientId);
                        newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
                        startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
                    } else if(mConnectionState == BLEService.STATE_CONNECTING){
                        if(mAutoConnectTimer != null && !mAutoConnectOn) {
                            mAutoConnectTimer.cancel();
                            mConnectionState = BLEService.STATE_DISCONNECTED;
                            btnConnectDisconnect.setText(R.string.connect);
                            connectedDevices.setText(R.string.empty);
                            btnConnectDisconnect.setEnabled(true);
                        }else if(mAutoConnectOn) {
                            mAutoConnectOn = false;
                            connectedDevices.setText(R.string.disconnecting);
                            mUserInitiatedDisconnection = true;
                            mService.disconnect(mSensorAddresses);
                        }
                    }else if(mConnectionState == BLEService.STATE_CONNECTED){
                        mUserInitiatedDisconnection = true;
                        mService.disconnect(mSensorAddresses);
                    }
                }
            }
        });

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                    Intent intent = new Intent(MainActivity.this, History.class);
                    intent.putExtra(Intent.EXTRA_TEXT, DIRECTORY_NAME);
                    startActivity(intent);
                }
            }
        });

        btnInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNextIndex < (mAvailableDataTypes.size() - 1)) {
                    mNextIndex++;
                    displayData();
                }
            }
        });

        btnDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNextIndex > 0) {
                    mNextIndex--;
                    displayData();
                }
            }
        });

        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }else {
            Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
            ArrayList<String> connParams = new ArrayList<>();
            connParams.add(mSensorId);
            connParams.add(mPatientId);
            newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
            startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
        }
    }

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
            switch(action){
                case BLEService.ACTION_GATT_CONNECTED:
                    final String sensorName = intent.getStringExtra(Intent.EXTRA_TEXT);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            handleGattConnectionEvent(sensorName);
                        }
                    });
                    break;
                case BLEService.ACTION_GATT_DISCONNECTED:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            handleGattDisconnectionEvent();
                        }
                    });
                    break;
                case BLEService.ACTION_GATT_SERVICES_DISCOVERED:
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Log.d(TAG, "Gatt Services discovered");
                        }
                    });
                    break;
                case BLEService.TEMP_VALUE:
                    final double tempValue = intent.getDoubleExtra(Intent.EXTRA_TEXT, 1000);
                    try {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                String tempStr;
                                if (tempValue != 1000) {
                                    tempStr = tempValue + "\u00B0C";
                                    curTemperature.setText(tempStr);
                                }
                            }
                        });
                    }catch (Exception e){
                        Log.e(TAG, e.getMessage());
                    }
                    break;
                case BLEService.BATTERY_LEVEL:
                    final int batteryLevel = intent.getIntExtra(Intent.EXTRA_TEXT, 200);
                    try {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                String batLevelStr = batteryLevel + "%";
                                batLevelTxt.setText(batLevelStr);
                                if(batteryLevel == 100){
                                    batLevel.setBackgroundResource(R.drawable.battery8);
                                }else if(batteryLevel >= 80) {
                                    batLevel.setBackgroundResource(R.drawable.battery7);
                                }else if(batteryLevel >= 60) {
                                    batLevel.setBackgroundResource(R.drawable.battery6);
                                }else if(batteryLevel >= 50) {
                                    batLevel.setBackgroundResource(R.drawable.battery5);
                                }else if(batteryLevel >= 40) {
                                    batLevel.setBackgroundResource(R.drawable.battery4);
                                }else if(batteryLevel >= 30) {
                                    batLevel.setBackgroundResource(R.drawable.battery3);
                                }else if(batteryLevel >= 20) {
                                    batLevel.setBackgroundResource(R.drawable.battery2);
                                }else if(batteryLevel >= 10) {
                                    batLevel.setBackgroundResource(R.drawable.battery1);
                                }else if(batteryLevel < 10) {
                                    batLevel.setBackgroundResource(R.drawable.battery0);
                                }
                            }
                        });
                    }catch (Exception e){
                        Log.e(TAG, e.getMessage());
                    }
                    break;
                case BLEService.HR:
                    final int hr = intent.getIntExtra(Intent.EXTRA_TEXT, 1000);
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(hr != 1000){
                                    String hrStr = hr + " BPM";
                                    hrValue.setText(hrStr);
                                }else{
                                    hrValue.setText(R.string.hr_NA);
                                }
                            }
                        });
                    }catch(Exception e){
                        Log.e(TAG, e.getMessage());
                    }
                    break;
                case BLEService.ACTION_CLOUD_ACCESS_RESULT:
                    final String result = intent.getStringExtra(Intent.EXTRA_TEXT);
                    switch(result){
                        case BLEService.NO_NETWORK_CONNECTION:
                            showMessage(BLEService.NO_NETWORK_CONNECTION);
                            break;
                        case BLEService.SERVER_ERROR:
                            showMessage(BLEService.SERVER_ERROR);
                            break;
                        case BLEService.EMPTY_RECORD:
                            showMessage(BLEService.EMPTY_RECORD);
                            break;
                        case BLEService.CONNECTION_ERROR:
                            showMessage(BLEService.CONNECTION_ERROR);
                            break;
                        default:
                            showMessage("Data sent to cloud");
                            updateAnalysedResult(result);
                    }
                    break;
                default:
                    if(mRecSegmentTimeUp) {
                        long end = System.currentTimeMillis();
                        mRecSegmentStart = end;
                        mRecSegmentTimeUp = false;
                        if(mRecTimeUp) {
                            mRecStart = mRecSegmentStart;
                            mRecTimeUp = false;
                        }

                        for(int i = 0; i < 6; i++){
                            Record record = mRecords.get(i);
                            if(record != null) {
                                record.setEnd(end);
                                mService.sendToCloud(record);
                                mRecords.set(i, new Record(mRecStart, mPatientId, mRecSegmentStart, i));
                            }
                        }

                    }
                    switch(action){
                        case BLEService.ONE_CHANNEL_ECG:
                            final int[] samples0 = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples0 != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples0.length; i ++){
                                                mRecords.get(0).addToChOne(samples0[i]);
                                            }
                                        }
                                        if (mShowECGOne) {
                                            for (int i = 1; i < samples0.length; i ++) {
                                                mChannelOne.updateGraph(samples0[i]);
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                        case BLEService.THREE_CHANNEL_ECG:
                            final int[] samples1 = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples1 != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples1.length; i += 3){
                                                mRecords.get(1).addToChOne(samples1[i]);
                                                mRecords.get(1).addToChTwo(samples1[i + 1]);
                                                mRecords.get(1).addToChThree(samples1[i + 2]);
                                            }
                                        }
                                        if (mShowECGThree) {
                                            for (int i = 1; i < samples1.length; i += 3) {
                                                if(samples1[i] == BLEService.NAN) {
                                                    mChannelOne.updateGraph(samples1[i]);
                                                    mChannelTwo.updateGraph(samples1[i + 1]);
                                                    mChannelThree.updateGraph(samples1[i + 2]);
                                                }else{
                                                    mChannelOne.updateGraph(samples1[i + 2] - samples1[i + 1]);
                                                    mChannelTwo.updateGraph(samples1[i] - samples1[i + 1]);
                                                    mChannelThree.updateGraph(samples1[i] - samples1[i + 2]);
                                                }
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                        case BLEService.ONE_CHANNEL_PPG:
                            final int[] samples2 = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples2 != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples2.length; i ++){
                                                mRecords.get(2).addToChOne(samples2[i]);
                                            }
                                        }
                                        if (mShowPPGOne) {
                                            for (int i = 1; i < samples2.length; i ++) {
                                                mChannelOne.updateGraph(samples2[i]);
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                        case BLEService.TWO_CHANNEL_PPG:
                            final int[] samples3 = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples3 != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples3.length; i += 2){
                                                mRecords.get(3).addToChOne(samples3[i]);
                                                mRecords.get(3).addToChTwo(samples3[i + 1]);
                                            }
                                        }
                                        if (mShowPPGTwo) {
                                            for(int i = 1; i < samples3.length; i += 2){
                                                mChannelOne.updateGraph(samples3[i]);
                                                mChannelTwo.updateGraph(samples3[i + 1]);
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                        case BLEService.THREE_CHANNEL_ACCELERATION:
                            final int[] samples4 = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples4 != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples4.length; i += 3){
                                                mRecords.get(4).addToChOne(samples4[i]);
                                                mRecords.get(4).addToChTwo(samples4[i + 1]);
                                                mRecords.get(4).addToChThree(samples4[i + 2]);
                                            }
                                        }
                                        if (mShowAccel) {
                                            for(int i = 1; i < samples4.length; i += 3){
                                                mChannelOne.updateGraph(samples4[i]);
                                                mChannelTwo.updateGraph(samples4[i + 1]);
                                                mChannelThree.updateGraph(samples4[i + 2]);
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                        case BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY:
                            final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                            (new Runnable() {
                                public void run() {
                                    if (samples != null) {
                                        if(!mSamplesRecieved) mSamplesRecieved = true;
                                        if(mRecording){
                                            for(int i = 1; i < samples.length; i++) {
                                                mRecords.get(5).addToChOne(samples[i]);
                                            }
                                        }
                                        if (mShowImpedance) {
                                            for(int i = 1; i < samples.length; i ++){
                                                mChannelOne.updateGraph(samples[i]);
                                            }
                                        }
                                    }
                                }
                            }).run();
                            break;
                    }
            }
        }
    };

    private static IntentFilter sensorStatusUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ONE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.ONE_CHANNEL_PPG);
        intentFilter.addAction(BLEService.TWO_CHANNEL_PPG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ACCELERATION);
        intentFilter.addAction(BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY);
        intentFilter.addAction(BLEService.TEMP_VALUE);
        intentFilter.addAction(BLEService.BATTERY_LEVEL);
        intentFilter.addAction(BLEService.HR);
        intentFilter.addAction(BLEService.ACTION_CLOUD_ACCESS_RESULT);
        return intentFilter;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mSensorAddresses = data.getStringArrayListExtra(SensorList.EXTRA_SENSOR_ADDRESSES);
                    mConnectionState = BLEService.STATE_CONNECTING;
                    connectedDevices.setText(R.string.connecting);
                    mService.connect(mSensorAddresses);
                }else if(resultCode == SensorList.DEVICE_NOT_FOUND){
                    showMessage(mSensorId + " not found, try again");
                    Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
                    ArrayList<String> connParams = new ArrayList<>();
                    connParams.add(mSensorId);
                    connParams.add(mPatientId);
                    newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
                    startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    showMessage("Bluetooth has turned on ");

                } else {
                    Log.d(TAG, "BT not enabled");
                    showMessage("Problem in BT Turning ON ");
                    finish();
                }
                break;
            case REQUEST_CONNECT_PARAMS:
                if(resultCode == Activity.RESULT_OK && data != null){
                    ArrayList<String> connParams = data.getStringArrayListExtra(Intent.EXTRA_TEXT);
                    if(connParams != null && connParams.size() == 2) {
                        mSensorId = connParams.get(0);
                        mPatientId = connParams.get(1);
                    }
                    Intent getSensorIntent = new Intent(MainActivity.this, SensorList.class);
                    getSensorIntent.putExtra(Intent.EXTRA_TEXT, mSensorId);
                    startActivityForResult(getSensorIntent, REQUEST_SELECT_DEVICE);
                }else if(resultCode == Activity.RESULT_CANCELED){
                    Log.d(TAG, "User cancled request");
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(SensorStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        savePatientAndDeviceIds();
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (mConnectionState == BLEService.STATE_CONNECTED) {
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
        } else {
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


    private void setGraphLayout(int type) {
        clearGraphLayout();
        switch (type) {
            case MAIN_LAYOUT:
                break;
            case ONE_CHANNEL_LAYOUT:
                chOne.setVisibility(View.VISIBLE);
                break;
            case TWO_CHANNELS_LAYOUT:
                chOne.setVisibility(View.VISIBLE);
                chTwo.setVisibility(View.VISIBLE);
                break;
            case THRE_CHANNELS_LAYOUT:
                chOne.setVisibility(View.VISIBLE);
                chTwo.setVisibility(View.VISIBLE);
                chThree.setVisibility(View.VISIBLE);
                break;

        }
    }

    private void clearGraphLayout() {
        mChannelOne.clearGraph();
        mChannelTwo.clearGraph();
        mChannelThree.clearGraph();
        chOne.setVisibility(View.GONE);
        chTwo.setVisibility(View.GONE);
        chThree.setVisibility(View.GONE);
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo
                = mShowAccel = mShowImpedance = false;
    }

    private void displayData(){

        if(mAvailableDataTypes.size() <= mNextIndex)
            return;

        String dataType = mAvailableDataTypes.get(mNextIndex);

        switch (dataType){
            case Record.ECG_ONE_DATA:
                curDispDataType.setText(Record.ECG_ONE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowECGOne = true;
                break;
            case Record.ECG_THREE_DATA:
                curDispDataType.setText(Record.ECG_THREE_DATA);
                setGraphLayout(THRE_CHANNELS_LAYOUT);
                mShowECGThree = true;
                break;
            case Record.PPG_ONE_DATA:
                curDispDataType.setText(Record.PPG_ONE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowPPGOne = true;
                break;
            case Record.PPG_TWO_DATA:
                curDispDataType.setText(Record.PPG_TWO_DATA);
                setGraphLayout(TWO_CHANNELS_LAYOUT);
                mShowPPGTwo = true;
                break;
            case Record.ACCEL_DATA:
                curDispDataType.setText(Record.ACCEL_DATA);
                setGraphLayout(THRE_CHANNELS_LAYOUT);
                mShowAccel = true;
                break;
            case Record.IMPEDANCE_DATA:
                curDispDataType.setText(Record.IMPEDANCE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowImpedance = true;
                break;
            default:
                break;
        }
    }


    private void handleGattConnectionEvent(String sensorName) {
        Log.d(TAG, "Connected to " + sensorName);
        addAvailableDataType(mSensorId);
        if(!mDataDisplayOn) {
            displayData();
            mDataDisplayOn = true;
        }
        mConnectionState=BLEService.STATE_CONNECTED;
        btnConnectDisconnect.setText(R.string.disconnect);
        btnConnectDisconnect.setEnabled(true);
        if(mNumConnectedSensors == 0) {
            String str = "Connected to " + sensorName;
            connectedDevices.setText(str);
        }
        else{
            String str = connectedDevices.getText().toString() + "," + sensorName;
            connectedDevices.setText(str);
        }
        mNumConnectedSensors++;
        patientId.setText(mPatientId);

        mRecordStartTimer = new CountDownTimer(PRE_RECORD_START_DURATION, 1000){
            public void onTick(long millisUntilFinished) {
                Log.w(TAG, "seconds remaining: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                mRecStart = System.currentTimeMillis();
                mRecSegmentStart = mRecStart;
                for(int i = 0; i < 6; i++)
                    mRecords.add(null);
                for(String type : mAvailableDataTypes){
                    int dataId = Record.DATA_TYPES.get(type);
                    Record rec = new Record(mRecStart, mPatientId, mRecSegmentStart, dataId);
                    mRecords.add(dataId, rec);
                }
                mRecording = true;
                mRecordTimer.run();
            }
        }.start();
    }

    private void handleGattDisconnectionEvent(){
        curDispDataType.setText("");
        connectedDevices.setText("");
        curTemperature.setText("");
        batLevel.setBackgroundResource(0);
        batLevelTxt.setText("");
        hrValue.setText("");
        patientId.setText("");
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo =
                mShowAccel = mShowImpedance = false;
        chOne.setVisibility(View.GONE);
        chTwo.setVisibility(View.GONE);
        chThree.setVisibility(View.GONE);
        mNextIndex = 0;
        mNumConnectedSensors = 0;
        mAvailableDataTypes.clear();
        if(mRecordStartTimer != null)
            mRecordStartTimer.cancel();
        if(mAutoConnectTimer != null)
            mAutoConnectTimer.cancel();
        mDataDisplayOn = false;
        mShowAnalysis = false;
        mSamplesRecieved = false;
        mRecSegmentTimeUp = mRecTimeUp = false;
        clearGraphLayout();
        if(mRecording) {
            mRecording = false;
            stopRecordingData();
        }
        if(mUserInitiatedDisconnection) {
            Log.e(TAG, "USER INITIATED DISCONNECTION");
            mUserInitiatedDisconnection = false;
            mSensorAddresses.clear();
            mConnectionState = BLEService.STATE_DISCONNECTED;
            btnConnectDisconnect.setText(R.string.connect);
            btnConnectDisconnect.setEnabled(true);
        }
        else {
            mAutoConnectTimer = new CountDownTimer(PRE_AUTO_RECONNECTION_PAUSE_DURATION, 1000) {

                public void onTick(long millisUntilFinished) {
                    String str = millisUntilFinished / 1000 + " secs to  start auto-connect";
                    connectedDevices.setText(str);
                }

                public void onFinish() {
                    connectedDevices.setText(R.string.starting_auto_connect);
                    mService.connect(mSensorAddresses);
                    mAutoConnectTimer = null;
                    mAutoConnectOn = true;
                }
            }.start();
            mConnectionState = BLEService.STATE_CONNECTING;
            btnConnectDisconnect.setText(R.string.disconnect);
            btnConnectDisconnect.setEnabled(true);
        }
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
            if(mSamplesRecieved) {
                if(mRecTimerCounter % MAX_RECORD_SEGMENT_DURATION == 0 && mRecTimerCounter != 0){
                    mRecSegmentTimeUp = true;
                }
                if(mRecTimerCounter % MAX_RECORD_DURATION == 0 && mRecTimerCounter != 0){
                    mRecTimeUp = true;
                }
                if (mRecTimerCounter < SECONDS_IN_ONE_MINUTE) {
                    sec = mRecTimerCounter;
                } else if (mRecTimerCounter < SECONDS_IN_ONE_HOUR) {
                    min = mRecTimerCounter / SECONDS_IN_ONE_MINUTE;
                    sec = mRecTimerCounter % SECONDS_IN_ONE_MINUTE;
                } else {
                    hr = mRecTimerCounter / SECONDS_IN_ONE_HOUR;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) / SECONDS_IN_ONE_MINUTE;
                    sec = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) % SECONDS_IN_ONE_MINUTE;
                }
                updateTimer();
                if(mRecTimerCounter == MAX_REC_COUNT)
                    refreshTimer();
                else
                    mRecTimerCounter++;
            }
            mHandler.postDelayed(mRecordTimer, ONE_SECOND_IN_MILLIS);
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
    }

    private void updateTimer(){
        mTimerString = String.format("%02d:%02d:%02d", hr,min,sec);
        ((TextView) findViewById(R.id.timer_view)).setText(mTimerString);
    }

    private void stopRecordingData(){
        long end = System.currentTimeMillis();
        for(int i = 0; i < mRecords.size(); i++){
            Record rec = mRecords.get(i);
            if(rec == null)
                continue;
            if(!rec.isEmpty()) {
                rec.setEnd(end);
                mService.sendToCloud(rec);
            }
        }
        mRecords.clear();
        mHandler.removeCallbacks(mRecordTimer);
        ((TextView) findViewById(R.id.timer_view)).setText("");
        refreshTimer();
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);
    }

    private void addAvailableDataType(String sensorId){
        String type = sensorId.substring(0, 3);
        switch(type.toUpperCase()){
            case "ECG":
                mAvailableDataTypes.add(Record.ECG_THREE_DATA);
                mAvailableDataTypes.add(Record.ACCEL_DATA);
                break;
            case "PPG":
                mAvailableDataTypes.add(Record.PPG_TWO_DATA);
                break;
            case "ACL":
                mAvailableDataTypes.add(Record.ACCEL_DATA);
                break;
            case "IMP":
                mAvailableDataTypes.add(Record.IMPEDANCE_DATA);
                break;
        }
    }

    private void updateAnalysedResult(String result){
        try{
            JSONObject res = new JSONObject(result);
        }catch(Exception e){
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

    private void savePatientAndDeviceIds() {
        if (isExternalStorageWritable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME);
            if (!dir.isDirectory())
                dir.mkdirs();
            File file = new File(dir, PATIENT_DEVICE_IDS_FILE_PATH);
            try {
                FileWriter fw = new FileWriter(file);
                fw.write(mPatientId + "\n" + mSensorId);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                Log.e(TAG, "Problem writing to Storage");
            }
        }else{
            showMessage("Cannot write to storage!");
        }
    }

    private void getPatientAndDeviceIds(){
        if(!isExternalStorageReadable()) {
            showMessage("Cannot access external storage");
        }
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME);
        File file = new File(dir, PATIENT_DEVICE_IDS_FILE_PATH);
        if (file.exists()) {
            try {
                BufferedReader buf = new BufferedReader(new FileReader(file));
                mPatientId = buf.readLine();
                mSensorId = buf.readLine();
                buf.close();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                showMessage("Problem accessing mFile");
            }
        }else{
            showMessage("No saved ids");
        }
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }
}
