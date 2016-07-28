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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.activities.util.SystemUiHider;
import vitalsens.vitalsensapp.fragments.ChannelOneFragment;
import vitalsens.vitalsensapp.fragments.ChannelThreeFragment;
import vitalsens.vitalsensapp.fragments.ChannelTwoFragment;
import vitalsens.vitalsensapp.models.DataPacket;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.models.Sensor;
import vitalsens.vitalsensapp.services.BLEService;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final int MAIN_LAYOUT = 0;
    private static final int ONE_CHANNEL_LAYOUT = 1;
    private static final int TWO_CHANNELS_LAYOUT = 2;
    private static final int THRE_CHANNELS_LAYOUT = 3;
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int MAX_DATA_RECORDING_TIME_IN_MINS = 600;
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND_IN_MILLIS = 1000;

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private TextView btnRecord, connectedDevices, curDispDataType,
            batLevel, curTemperature;
    private LinearLayout chOne, chTwo, chThree;
    private Handler mHandler;
    private BLEService mService;
    private ArrayList<Sensor> mConnectedSensors;
    private ArrayList<Record> mRecords;
    private ArrayList<String> mAvailableDataTypes;
    ArrayList<String> mSensorAddresses;
    private int mConnectionState;
    private int mRecTimerCounter, min, sec, hr;
    private int mNextIndex;
    private String mTimerString;
    private boolean mShowECGOne, mShowECGThree, mShowPPGOne,
            mShowPPGTwo, mShowAccel, mShowImpedance;
    private boolean mRecording;
    private boolean mInitDataDispOn;
    private boolean mUserInitiatedDisconnection;
    private boolean mReconnecting;

    private String mPatientId;

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
        btnRecord = (TextView) findViewById(R.id.btn_record);
        curTemperature = (TextView) findViewById(R.id.cur_temp);
        batLevel = (TextView) findViewById(R.id.bat_level);
        TextView btnInc = (TextView) findViewById(R.id.btn_inc);
        TextView btnDec = (TextView) findViewById(R.id.btn_dec);
        Button btnHistory = (Button) findViewById(R.id.btn_history);
        curDispDataType = (TextView) findViewById(R.id.cur_disp_dataType);
        connectedDevices = (TextView) findViewById(R.id.connected_devices);
        RelativeLayout btnRecordLayout = (RelativeLayout)findViewById(R.id.btn_record_layout);
        chOne = (LinearLayout) findViewById(R.id.channel1_fragment);
        chTwo = (LinearLayout) findViewById(R.id.channel2_fragment);
        chThree = (LinearLayout) findViewById(R.id.channel3_fragment);

        mHandler = new Handler();
        mConnectedSensors = new ArrayList<>();
        mRecords = new ArrayList<>();
        mAvailableDataTypes = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mRecording = false;
        mInitDataDispOn = false;
        mUserInitiatedDisconnection = false;
        mReconnecting = false;

        min = sec =  hr = 0;
        mTimerString = "";

        mPatientId = "p101";

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

        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                        if(mReconnecting){
                            mReconnecting = false;
                            mUserInitiatedDisconnection = true;
                            mService.disconnect(null, mSensorAddresses);
                        }
                        Intent newIntent = new Intent(MainActivity.this, SensorList.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else if (mConnectionState == BLEService.STATE_CONNECTED) {
                        mUserInitiatedDisconnection = true;
                        mService.disconnect(mConnectedSensors, null);
                    }
                }
            }
        });

        btnRecordLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mConnectionState == BLEService.STATE_CONNECTED){
                    if(mRecording)
                        stopRecordingData();
                    else {
                        long timeStamp = System.currentTimeMillis();
                        for(int i = 0; i < 6; i++){
                            mRecords.add( new Record(1, timeStamp, mPatientId, i));
                        }
                        mRecording = true;
                        btnRecord.setText("Stop");
                        mRecordTimer.run();
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
            Intent newIntent = new Intent(MainActivity.this, SensorList.class);
            startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
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

            if (action.equals(BLEService.ACTION_GATT_CONNECTED)) {
                final String sensorStr = intent.getStringExtra(Intent.EXTRA_TEXT);
                runOnUiThread(new Runnable() {
                    public void run() {
                        handleGattConnectionEvent(sensorStr);
                    }
                });
            }
            if (action.equals(BLEService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        handleGattDisconnectionEvent();
                    }
                });
            }
            if (action.equals(BLEService.ACTION_GATT_SERVICES_DISCOVERED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "Gatt Services discovered");
                    }
                });
            }
            if (action.equals(BLEService.ONE_CHANNEL_ECG)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.ECG_ONE_DATA)) {
                                mAvailableDataTypes.add(Sensor.ECG_ONE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording)
                                mRecords.get(0).addToChOne(dp.getChOne());
                            if (mShowECGOne) {
                                for(int i = 1; i < samples.length; i++){
                                    mChannelOne.updateGraph(samples[i]);
                                }
                                String str = "{ECG1 Samples: " + dp.getChOne() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.THREE_CHANNEL_ECG)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.ECG_THREE_DATA)) {
                                mAvailableDataTypes.add(Sensor.ECG_THREE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording){
                                mRecords.get(1).addToChOne(dp.getChOne());
                                mRecords.get(1).addToChTwo(dp.getChTwo());
                                mRecords.get(1).addToChThree(dp.getChThree());
                            }
                            if (mShowECGThree) {
                                for(int i = 1; i < samples.length; i += 3){
                                    mChannelOne.updateGraph(samples[i]);
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                    mChannelThree.updateGraph(samples[i + 2]);
                                }
                                String str = "{ECG3 Samples: " +
                                        "chOne: " + dp.getChOne() +
                                        " chTwo: " + dp.getChTwo() +
                                        " chThree: " + dp.getChThree() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.ONE_CHANNEL_PPG)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.PPG_ONE_DATA)) {
                                mAvailableDataTypes.add(Sensor.PPG_ONE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording){
                                mRecords.get(2).addToChOne(dp.getChOne());
                            }
                            if (mShowPPGOne) {
                                for(int i = 1; i < samples.length; i ++){
                                    mChannelOne.updateGraph(samples[i]);
                                }
                                String str = "{PPG1 Samples: " + dp.getChOne() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.TWO_CHANNEL_PPG)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.PPG_TWO_DATA)) {
                                mAvailableDataTypes.add(Sensor.PPG_TWO_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording){
                                mRecords.get(3).addToChOne(dp.getChOne());
                                mRecords.get(3).addToChTwo(dp.getChTwo());
                            }
                            if (mShowPPGTwo) {
                                for(int i = 1; i < samples.length; i += 2){
                                    mChannelOne.updateGraph(samples[i]);
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                }
                                String str = "{PPG2 Samples: " +
                                        "chOne: " + dp.getChOne() +
                                        " chTwo: " + dp.getChTwo() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.THREE_CHANNEL_ACCELERATION)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.ACCEL_DATA)) {
                                mAvailableDataTypes.add(Sensor.ACCEL_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording){
                                mRecords.get(4).addToChOne(dp.getChOne());
                                mRecords.get(4).addToChTwo(dp.getChTwo());
                                mRecords.get(4).addToChThree(dp.getChThree());
                            }
                            if (mShowAccel) {
                                for(int i = 1; i < samples.length; i += 3){
                                    mChannelOne.updateGraph(samples[i]);
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                    mChannelThree.updateGraph(samples[i + 2]);
                                }
                                String str = "{ACCEL Samples: " +
                                        "chOne: " + dp.getChOne() +
                                        " chTwo: " + dp.getChTwo() +
                                        " chThree: " + dp.getChThree() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            DataPacket dp = new DataPacket(samples);
                            if(!mAvailableDataTypes.contains(Sensor.IMPEDANCE_DATA)) {
                                mAvailableDataTypes.add(Sensor.IMPEDANCE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording){
                                mRecords.get(5).addToChOne(dp.getChOne());
                                mRecords.get(5).addToChTwo(dp.getChTwo());
                                mRecords.get(5).addToChThree(dp.getChThree());
                            }
                            if (mShowImpedance) {
                                for(int i = 1; i < samples.length; i ++){
                                    mChannelOne.updateGraph(samples[i]);
                                }
                                String str = "{IMPEDANCE Samples: " + dp.getChOne() + "}";
                                Log.d(TAG, str);
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BLEService.TEMP_VALUE)) {
                final double tempValue = intent.getDoubleExtra(Intent.EXTRA_TEXT, 1000);
                try {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (tempValue != 1000) {
                                curTemperature.setText(tempValue + "\u00B0C");
                            }else{
                                curTemperature.setText("Temp: N/A");
                            }
                        }
                    });
                }catch (Exception e){
                    Log.e(TAG, e.getMessage());
                }
            }
            if (action.equals(BLEService.BATTERY_LEVEL)) {
                final int batteryLevel = intent.getIntExtra(Intent.EXTRA_TEXT, 200);
                try {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (batteryLevel != 200) {
                                batLevel.setText("Battery: " + batteryLevel + "%");
                            }else{
                                batLevel.setText("Battery: N/A");
                            }
                        }
                    });
                }catch (Exception e){
                    Log.e(TAG, e.getMessage());
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
        return intentFilter;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mSensorAddresses = data.getStringArrayListExtra("SENSOR_LIST");
                    mService.connect(mSensorAddresses);
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
        mInitDataDispOn = false;
    }

    private void displayData(){

        String dataType = mAvailableDataTypes.get(mNextIndex);

        switch (dataType){
            case Sensor.ECG_ONE_DATA:
                curDispDataType.setText(Sensor.ECG_ONE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowECGOne = true;
                break;
            case Sensor.ECG_THREE_DATA:
                curDispDataType.setText(Sensor.ECG_THREE_DATA);
                setGraphLayout(THRE_CHANNELS_LAYOUT);
                mShowECGThree = true;
                break;
            case Sensor.PPG_ONE_DATA:
                curDispDataType.setText(Sensor.PPG_ONE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowPPGOne = true;
                break;
            case Sensor.PPG_TWO_DATA:
                curDispDataType.setText(Sensor.PPG_TWO_DATA);
                setGraphLayout(TWO_CHANNELS_LAYOUT);
                mShowPPGTwo = true;
                break;
            case Sensor.ACCEL_DATA:
                curDispDataType.setText(Sensor.ACCEL_DATA);
                setGraphLayout(THRE_CHANNELS_LAYOUT);
                mShowAccel = true;
                break;
            case Sensor.IMPEDANCE_DATA:
                curDispDataType.setText(Sensor.IMPEDANCE_DATA);
                setGraphLayout(ONE_CHANNEL_LAYOUT);
                mShowImpedance = true;
                break;
            default:
                break;
        }
    }


    private void handleGattConnectionEvent(String sensorStr) {
        Sensor sensor = new Sensor();
        String connectedDevicesStr = connectedDevices.getText().toString();
        sensor.fromJson(sensorStr);
        Log.d(TAG, "Connected to" + sensor.getName() +
                " : " + sensor.getAddress());
        mConnectedSensors.add(sensor);
        mConnectionState=BLEService.STATE_CONNECTED;
        btnConnectDisconnect.setText("Disconnect");
        if(mReconnecting){
            mReconnecting = false;
        }
        if(connectedDevicesStr.equals(""))
            connectedDevices.setText(sensor.getName());
        else
            connectedDevices.setText(connectedDevicesStr + " " + sensor.getName());
    }

    private void handleGattDisconnectionEvent(){
        mConnectedSensors.clear();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        btnConnectDisconnect.setText("Connect");
        curDispDataType.setText("");
        connectedDevices.setText("");
        curTemperature.setText("");
        batLevel.setText("");
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo =
                mShowAccel = mShowImpedance = false;
        chOne.setVisibility(View.GONE);
        chTwo.setVisibility(View.GONE);
        chThree.setVisibility(View.GONE);
        mNextIndex = 0;
        mAvailableDataTypes.clear();
        mInitDataDispOn = false;
        mReconnecting = false;
        clearGraphLayout();
        if(mRecording)
            stopRecordingData();
        if(mUserInitiatedDisconnection) {
            mUserInitiatedDisconnection = false;
            mSensorAddresses.clear();
        }
        else{
            mService.connect(mSensorAddresses);
            mReconnecting = true;
        }

    }

    private void saveRecords(){
        if(isExternalStorageWritable()){
            new Thread(new Runnable(){
                public void run(){
                    File root = android.os.Environment.getExternalStorageDirectory();
                    File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
                    if(!dir.isDirectory())
                        dir.mkdirs();
                    File file;
                    for(int i = 0; i<mRecords.size(); i++) {
                        Record record = mRecords.get(i);
                        Date date = new Date(record.getTimeStamp());
                        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss", Locale.US);
                        String dataType = record.getType();
                        String fileName = dataType + "_" + sdf.format(date) + ".txt";
                        file = new File(dir, fileName);
                        if(!record.getChOne().equals("")) {
                            try {
                                FileWriter fw = new FileWriter(file, true);
                                fw.append(record.toJson());
                                fw.flush();
                                fw.close();
                                showMessage(dataType + " Record Saved");
                            } catch (IOException e) {
                                Log.e(TAG, e.toString());
                                showMessage("Problem writing to Storage");
                            }
                        }
                    }
                    mRecords.clear();
                }
            }).start();
        }
        else
            showMessage("Cannot write to storage");
    }


    public boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
                if (mRecTimerCounter < SECONDS_IN_ONE_MINUTE) {
                    sec = mRecTimerCounter;
                } else if (mRecTimerCounter < SECONDS_IN_ONE_HOUR) {
                    min = mRecTimerCounter / SECONDS_IN_ONE_MINUTE;
                    sec = mRecTimerCounter % SECONDS_IN_ONE_MINUTE;
                } else {
                    hr = mRecTimerCounter / SECONDS_IN_ONE_HOUR;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) / SECONDS_IN_ONE_MINUTE;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) % SECONDS_IN_ONE_MINUTE;
                }
                updateTimer();
                if (mRecTimerCounter >= MAX_DATA_RECORDING_TIME_IN_MINS) {
                    stopRecordingData();
                    return;
                }
                if ((MAX_DATA_RECORDING_TIME_IN_MINS - mRecTimerCounter) < 5)//Five seconds to the end of timer
                    ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.green));
                mRecTimerCounter++;
            mHandler.postDelayed(mRecordTimer, ONE_SECOND_IN_MILLIS);
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
        ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.black));
    }

    private void updateTimer(){
        mTimerString = String.format("%02d:%02d:%02d", hr,min,sec);
        ((TextView) findViewById(R.id.timer_view)).setText(mTimerString);
    }

    private void stopRecordingData(){
        if(mRecording) {
            saveRecords();
            mRecording = false;
            btnRecord.setText("Record");
            mHandler.removeCallbacks(mRecordTimer);
            ((TextView) findViewById(R.id.timer_view)).setText("");
            refreshTimer();
        }
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
