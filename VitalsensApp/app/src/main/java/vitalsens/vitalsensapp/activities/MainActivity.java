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
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.fragments.GraphviewFragment;
import vitalsens.vitalsensapp.fragments.RecordAnalysisFragment;
import vitalsens.vitalsensapp.fragments.RecordFragment;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.services.BLEService;
import vitalsens.vitalsensapp.services.CloudAccessService;
import vitalsens.vitalsensapp.services.SaveRecordService;
import vitalsens.vitalsensapp.utils.ConnectDialog;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final String DIRECTORY_NAME_IDS = "/VITALSENSE_IDS";
    private static final String DIRECTORY_NAME_RECORDS = "/VITALSENSE_RECORDS";
    private static final String PATIENT_DEVICE_IDS_FILE_PATH = "patient_device_ids.txt";
    private static final int MAIN_LAYOUT = 0;
    private static final int ONE_CHANNEL_LAYOUT = 1;
    private static final int TWO_CHANNELS_LAYOUT = 2;
    private static final int THREE_CHANNELS_LAYOUT = 3;
    private static final int RECORD_TIMER_LAYOUT = -1;
    private static final int RECORD_ANALYSIS_LAYOUT = -2;
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CONNECT_PARAMS = 3;
    private static final int MAX_RECORD_SEGMENT_DURATION = 30; //In Seconds (I minute)
    private static final int MAX_RECORD_DURATION = 1800; //In seconds (30 minutes)
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND_IN_MILLIS = 1000;
    private static final int PRE_RECORD_START_DURATION = 10000; //In milliseconds
    private static final int PRE_AUTO_RECONNECTION_PAUSE_DURATION = 5000; //In milliseconds
    private static final int ECG = 1;
    private static final int PPG = 3;
    private static final int ACCEL = 4;
    private static final int IMPED = 5;
    private static final String CLOUD_ACCESS_KEY = "v1t4753n553cr3tk3y";

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private TextView connectedDevices, curDispDataType,
            batLevel, curTemperature, hrValue, patientId, batLevelTxt;
    private Handler mHandler;
    private BLEService mService;
    private HashMap<Integer, Record> mRecords;
    private ArrayList<String> mAvailableDataTypes;
    private ArrayList<String> mSensorAddresses;
    private CountDownTimer mAutoConnectTimer;
    private CountDownTimer mRecordStartTimer;
    private FragmentManager mFragManager;
    private int mConnectionState;
    private int min, sec, hr;
    private int mNextIndex;
    private int mXRange;
    private long mRecStart, mRecordingStart, mCurrentTimeStamp, mRecEnd, mRecSegmentEnd;
    private int mNumConnectedSensors;
    private double mCurTemp;
    private String mSensorId, mPatientId;
    private String mAnalysisResult;
    private boolean mShowECG, mShowPPG, mShowAccel, mShowImpedance;
    private boolean mRecording;
    private boolean mUserInitiatedDisconnection;
    private boolean mDataDisplayOn;
    private boolean mSamplesRecieved;
    private boolean mAutoConnectOn;
    private boolean mPainStart;

    private GraphviewFragment mGraphFragment;
    private RecordFragment mRecordFragment;
    private RecordAnalysisFragment mRecordAnalysisFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int screenSize = getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        switch(screenSize) {
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                mXRange = 1250;
                break;
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                mXRange = 675;
                break;
            default:
                mXRange = 600;
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
        Button btnPain = (Button) findViewById(R.id.pain_btn);

        mHandler = new Handler();
        mRecordStartTimer = null;
        mAutoConnectTimer = null;
        mSensorId = "";
        mRecords = new HashMap<>();
        mAvailableDataTypes = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mRecording = false;
        mUserInitiatedDisconnection = false;
        mDataDisplayOn = false;
        mSamplesRecieved = false;
        mAutoConnectOn = false;
        mPainStart = false;

        min = sec =  hr = 0;
        mNextIndex = 0;
        mNumConnectedSensors = 0;
        mPatientId = "";
        mAnalysisResult = "";

        mFragManager = getFragmentManager();

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

        btnPain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mRecording){
                    if(mPainStart){
                        mPainStart = false;
                        if(mRecords.get(ECG) != null) {
                            mRecords.get(ECG).setPEEnd(mRecords.get(ECG).getChOne().size());
                        }
                        cancelPainEventMark();
                    }
                    else{
                        mPainStart = true;
                        if(mRecords.get(ECG) != null) {
                            mRecords.get(ECG).setPEStart(mRecords.get(ECG).getChOne().size());
                        }
                        markPainEvent();
                    }
                }
            }
        });

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                    Intent intent = new Intent(MainActivity.this, History.class);
                    intent.putExtra(Intent.EXTRA_TEXT, DIRECTORY_NAME_RECORDS);
                    startActivity(intent);
                }
            }
        });

        btnInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNextIndex < (mAvailableDataTypes.size() - 1) && mConnectionState == BLEService.STATE_CONNECTED) {
                    mNextIndex++;
                    displayData();
                }
            }
        });

        btnDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNextIndex >= -1 && mConnectionState == BLEService.STATE_CONNECTED) {
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
                    String sensorName = intent.getStringExtra(Intent.EXTRA_TEXT);
                    handleGattConnectionEvent(sensorName);
                    break;
                case BLEService.ACTION_GATT_DISCONNECTED:
                    handleGattDisconnectionEvent();
                    break;
                case BLEService.TEMP_VALUE:
                    updateTempValue(intent.getDoubleExtra(Intent.EXTRA_TEXT, 1000));
                    break;
                case BLEService.BATTERY_LEVEL:
                    updateBatteryLevel(intent.getIntExtra(Intent.EXTRA_TEXT, 200));
                    break;
                case BLEService.HR:
                    updateHRValue(intent.getIntExtra(Intent.EXTRA_TEXT, 1000));

                    break;
                case CloudAccessService.ACTION_CLOUD_ACCESS_RESULT:
                    reportCloudAccessStatus(intent.getStringExtra(CloudAccessService.EXTRA_RESULT));
                    break;
                case SaveRecordService.ACTION_SAVE_RECORD_STATUS:
                    showMessage(intent.getStringExtra(SaveRecordService.EXTRA_STATUS));
                case BLEService.DATA_RECIEVED:
                    mSamplesRecieved = true;
                    final double[] samples = intent.getDoubleArrayExtra(Intent.EXTRA_TEXT);
                    if(samples != null) {
                        final int dataId = (int) samples[0];

                        if (mRecSegmentEnd > 0) {
                            long start, end;
                            start = end = mRecSegmentEnd;
                            mRecSegmentEnd = 0;
                            if (mRecEnd > 0) {
                                mRecStart = start;
                                mRecEnd = 0;
                            }

                            for (int key : mRecords.keySet()) {
                                Record rec = mRecords.put(key, new Record(mRecStart, mPatientId, start, key));
                                if (!rec.isEmpty()) {
                                    rec.setEnd(end);
                                    rec.setTemp(mCurTemp);
                                    rec.setSecret(CLOUD_ACCESS_KEY);
                                    CloudAccessService.startActionCloudAccess(MainActivity.this, rec);
                                    SaveRecordService.startActionSaveRecord(MainActivity.this, rec);
                                }
                            }
                        }
                        switch (dataId) {
                            case ECG:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i += 3) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(ECG).addToChOne(null);
                                                    mRecords.get(ECG).addToChTwo(null);
                                                    mRecords.get(ECG).addToChThree(null);
                                                } else {
                                                    mRecords.get(ECG).addToChOne(samples[i]);
                                                    mRecords.get(ECG).addToChTwo(samples[i + 1]);
                                                    mRecords.get(ECG).addToChThree(samples[i + 2]);
                                                }
                                            }
                                        }
                                        if (mShowECG) {
                                            for (int i = 1; i < samples.length; i += 3) {
                                                if (Double.isNaN(samples[i])) {
                                                    double[] result =
                                                            {samples[i], samples[i + 1], samples[i + 2]};
                                                    mGraphFragment.updateGraph(result);
                                                } else {
                                                    double[] result = {
                                                            samples[i + 2] - samples[i + 1],
                                                            samples[i] - samples[i + 1],
                                                            samples[i] - samples[i + 2]
                                                    };
                                                    mGraphFragment.updateGraph(result);
                                                }
                                            }
                                        }

                                    }
                                }).run();
                                break;
                            case PPG:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i += 2) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(PPG).addToChOne(null);
                                                    mRecords.get(PPG).addToChTwo(null);
                                                } else {
                                                    mRecords.get(PPG).addToChOne(samples[i]);
                                                    mRecords.get(PPG).addToChTwo(samples[i + 1]);
                                                }
                                            }
                                        }
                                        if (mShowPPG) {
                                            for (int i = 1; i < samples.length; i += 2) {
                                                double[] result =
                                                        {samples[i], samples[i + 1]};
                                                mGraphFragment.updateGraph(result);
                                            }
                                        }
                                    }
                                }).run();
                                break;
                            case ACCEL:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i += 3) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(ACCEL).addToChOne(null);
                                                    mRecords.get(ACCEL).addToChTwo(null);
                                                    mRecords.get(ACCEL).addToChThree(null);
                                                } else {
                                                    mRecords.get(ACCEL).addToChOne(samples[i]);
                                                    mRecords.get(ACCEL).addToChTwo(samples[i + 1]);
                                                    mRecords.get(ACCEL).addToChThree(samples[i + 2]);
                                                }
                                            }
                                        }
                                        if (mShowAccel) {
                                            for (int i = 1; i < samples.length; i += 3) {
                                                double[] result =
                                                        {samples[i], samples[i + 1], samples[i + 2]};
                                                mGraphFragment.updateGraph(result);
                                            }
                                        }
                                    }
                                }).run();
                                break;
                            case IMPED:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i++) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(IMPED).addToChOne(null);
                                                } else {
                                                    mRecords.get(IMPED).addToChOne(samples[i]);
                                                }
                                            }
                                        }
                                        if (mShowImpedance) {
                                            for (int i = 1; i < samples.length; i++) {
                                                double[] result =
                                                        {samples[i]};
                                                mGraphFragment.updateGraph(result);
                                            }
                                        }
                                    }
                                }).run();
                                break;
                        }
                    }
            }
        }
    };

    private static IntentFilter sensorStatusUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.DATA_RECIEVED);
        intentFilter.addAction(BLEService.TEMP_VALUE);
        intentFilter.addAction(BLEService.BATTERY_LEVEL);
        intentFilter.addAction(BLEService.HR);
        intentFilter.addAction(CloudAccessService.ACTION_CLOUD_ACCESS_RESULT);
        intentFilter.addAction(SaveRecordService.ACTION_SAVE_RECORD_STATUS);
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
                    btnConnectDisconnect.setEnabled(false);
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
        clearGraphLayout();
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
            case RECORD_TIMER_LAYOUT:
                mRecordFragment = new RecordFragment();
                mFragManager.beginTransaction()
                        .add(R.id.main_display, mRecordFragment)
                        .commit();
                break;
            case RECORD_ANALYSIS_LAYOUT:
                mRecordAnalysisFragment = new RecordAnalysisFragment();
                mFragManager.beginTransaction()
                        .add(R.id.main_display, mRecordAnalysisFragment)
                        .commit();
                break;
            case ONE_CHANNEL_LAYOUT:
                mGraphFragment = GraphviewFragment.createInstance(1, mXRange);
                mFragManager.beginTransaction()
                        .add(R.id.main_display, mGraphFragment)
                        .commit();
                break;
            case TWO_CHANNELS_LAYOUT:
                mGraphFragment = GraphviewFragment.createInstance(2, mXRange);
                mFragManager.beginTransaction()
                        .add(R.id.main_display, mGraphFragment)
                        .commit();
                break;
            case THREE_CHANNELS_LAYOUT:
                mGraphFragment = GraphviewFragment.createInstance(3, mXRange);
                mFragManager.beginTransaction()
                        .add(R.id.main_display, mGraphFragment)
                        .commit();
                break;

        }
    }

    private void clearGraphLayout() {
        mShowECG = mShowPPG = mShowAccel = mShowImpedance = false;
        if(mGraphFragment != null){
            mFragManager.beginTransaction()
                    .detach(mGraphFragment)
                    .commit();
            mGraphFragment.clearGraph();
            mGraphFragment = null;
        }
        if(mRecordFragment != null){
            mFragManager.beginTransaction()
                    .detach(mRecordFragment)
                    .commit();
            mRecordFragment.clearRecordTimer();
            mRecordFragment = null;
        }
        if(mRecordAnalysisFragment != null){
            mFragManager.beginTransaction()
                    .detach(mRecordAnalysisFragment)
                    .commit();
            mRecordAnalysisFragment.clearView();
            mRecordAnalysisFragment = null;
        }
    }

    private void displayData(){

        if(mAvailableDataTypes.size() <= mNextIndex)
            return;

        if(mNextIndex == RECORD_TIMER_LAYOUT){
            setGraphLayout(RECORD_TIMER_LAYOUT);
            curDispDataType.setText("");
            return;
        }

        if(mNextIndex == RECORD_ANALYSIS_LAYOUT){
            setGraphLayout(RECORD_ANALYSIS_LAYOUT);
            curDispDataType.setText("");
            return;
        }

        String dataType = mAvailableDataTypes.get(mNextIndex);

        switch (dataType){
            case Record.ECG_DATA:
                curDispDataType.setText(Record.ECG_DATA);
                setGraphLayout(THREE_CHANNELS_LAYOUT);
                mShowECG = true;
                break;
            case Record.PPG_DATA:
                curDispDataType.setText(Record.PPG_DATA);
                setGraphLayout(TWO_CHANNELS_LAYOUT);
                mShowPPG = true;
                break;
            case Record.ACCEL_DATA:
                curDispDataType.setText(Record.ACCEL_DATA);
                setGraphLayout(THREE_CHANNELS_LAYOUT);
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
            connectedDevices.setText(sensorName);
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
                mRecordingStart = mRecStart = System.currentTimeMillis();;
                for(int dataId : Record.DATA_TYPES.values()){
                    Record rec = new Record(mRecStart, mPatientId, mRecStart, dataId);
                    mRecords.put(dataId, rec);
                }

                mRecEnd = mRecSegmentEnd = 0;
                mRecording = true;
                mRecordTimer.run();
                mNextIndex = RECORD_TIMER_LAYOUT;
                displayData();
            }
        }.start();
    }

    private void handleGattDisconnectionEvent(){
        curDispDataType.setText("");
        connectedDevices.setText("");
        curTemperature.setText("");
        mCurTemp = 0;
        batLevel.setBackgroundResource(0);
        batLevelTxt.setText("");
        hrValue.setText("");
        patientId.setText("");
        mNextIndex = 0;
        mAnalysisResult = "";
        mNumConnectedSensors = 0;
        mAvailableDataTypes.clear();
        if(mRecordStartTimer != null)
            mRecordStartTimer.cancel();
        if(mAutoConnectTimer != null)
            mAutoConnectTimer.cancel();
        mDataDisplayOn = false;
        mSamplesRecieved = false;
        mPainStart = false;
        cancelPainEventMark();
        clearGraphLayout();
        if(mRecording) {
            mRecording = false;
            stopRecordingData();
        }
        if(mUserInitiatedDisconnection) {
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
            long now = SystemClock.uptimeMillis();
            if(mSamplesRecieved) {
                mCurrentTimeStamp = System.currentTimeMillis();
                long recordDuration = (mCurrentTimeStamp - mRecordingStart) / ONE_SECOND_IN_MILLIS;
                if ((recordDuration % MAX_RECORD_SEGMENT_DURATION) == 0 && recordDuration != 0) {
                    mRecSegmentEnd = mCurrentTimeStamp;
                    if ((recordDuration % MAX_RECORD_DURATION) == 0) {
                        mRecEnd = mCurrentTimeStamp;
                    }
                }
                hr = (int) recordDuration / SECONDS_IN_ONE_HOUR;
                min = (int) (recordDuration % SECONDS_IN_ONE_HOUR) / SECONDS_IN_ONE_MINUTE;
                sec = (int) (recordDuration % SECONDS_IN_ONE_HOUR) % SECONDS_IN_ONE_MINUTE;
                String timerStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hr, min, sec);
                if (mRecordFragment != null && mRecordFragment.isAdded()) {
                    mRecordFragment.setRecordTimer(timerStr);
                    ((TextView) findViewById(R.id.timer_view)).setText("");
                } else
                    ((TextView) findViewById(R.id.timer_view)).setText(timerStr);
            }
            long next = now + (ONE_SECOND_IN_MILLIS - (now % ONE_SECOND_IN_MILLIS));
            mHandler.postAtTime(mRecordTimer, next);
        }
    };

    private void stopRecordingData(){
        long end = System.currentTimeMillis();
        for(int key : mRecords.keySet()){
            Record rec = mRecords.get(key);
            if(!rec.isEmpty()) {
                rec.setEnd(end);
                rec.setTemp(mCurTemp);
                rec.setSecret(CLOUD_ACCESS_KEY);
                CloudAccessService.startActionCloudAccess(MainActivity.this, rec);
                SaveRecordService.startActionSaveRecord(MainActivity.this, rec);
            }
        }
        mRecords.clear();
        mHandler.removeCallbacks(mRecordTimer);
        ((TextView) findViewById(R.id.timer_view)).setText("");
        hr = min = sec = 0;
        mRecStart = mRecordingStart = 0;
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
                mAvailableDataTypes.add(Record.ECG_DATA);
                mAvailableDataTypes.add(Record.ACCEL_DATA);
                break;
            case "PPG":
                mAvailableDataTypes.add(Record.PPG_DATA);
                break;
            case "ACL":
                mAvailableDataTypes.add(Record.ACCEL_DATA);
                break;
            case "IMP":
                mAvailableDataTypes.add(Record.IMPEDANCE_DATA);
                break;
        }
    }

    private void savePatientAndDeviceIds() {
        if (isExternalStorageWritable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME_IDS);
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
        File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME_IDS);
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

    private void updateTempValue(double tempValue){
        String tempStr;
        if (tempValue != 1000) {
            mCurTemp = tempValue;
            tempStr = tempValue + "\u00B0C";
            curTemperature.setText(tempStr);
        }
    }

    private void updateBatteryLevel(int batteryLevel){
        String str = batteryLevel + "%";
        batLevelTxt.setText(str);
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

    private void updateHRValue(int hr){
        if(hr != 1000){
            String hrStr = hr + " BPM";
            hrValue.setText(hrStr);
        }else{
            hrValue.setText(R.string.hr_NA);
        }
    }

    private void reportCloudAccessStatus(String status){
        switch(status){
            case CloudAccessService.NO_NETWORK_CONNECTION:
                connectedDevices.setText(CloudAccessService.NO_NETWORK_CONNECTION);
                break;
            case CloudAccessService.SERVER_ERROR:
                connectedDevices.setText(CloudAccessService.SERVER_ERROR);
                break;
            case CloudAccessService.CONNECTION_ERROR:
                connectedDevices.setText(CloudAccessService.CONNECTION_ERROR);
                break;
            default:
                connectedDevices.setText(CloudAccessService.DATA_SENT);
                mAnalysisResult = status;
                updateAnalysisResult(status);
        }
    }

    private void markPainEvent(){
        if(mGraphFragment != null && mGraphFragment.isAdded() && mShowECG)
            mGraphFragment.setColor(Color.RED);
    }

    private void cancelPainEventMark(){
        if(mGraphFragment != null && mGraphFragment.isAdded() && mShowECG)
            mGraphFragment.setColor(Color.WHITE);
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private void updateAnalysisResult(String analysisStr){
        if(mRecordAnalysisFragment != null && mRecordAnalysisFragment.isAdded())
            mRecordAnalysisFragment.updateView(analysisStr);
    }
}
