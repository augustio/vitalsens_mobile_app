package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.fragments.GraphviewFragment;
import vitalsens.vitalsensapp.fragments.RecordAnalysisFragment;
import vitalsens.vitalsensapp.fragments.RecordFragment;
import vitalsens.vitalsensapp.models.Patient;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.services.BLEService;
import vitalsens.vitalsensapp.services.CloudAccessService;
import vitalsens.vitalsensapp.services.SaveRecordService;
import vitalsens.vitalsensapp.utils.LoginConnectDialog;
import vitalsens.vitalsensapp.utils.IOOperations;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final String DIRECTORY_NAME_RECORDS = "/VITALSENSE_RECORDS";
    private static final String PATIENT_DIRECTORY = "/VITALSENS_PATIENT";
    private static final String PATIENT_FILE = "patient_auth_connection_credentials.txt";
    private static final int MAIN_LAYOUT = 0;
    private static final int ONE_CHANNEL_LAYOUT = 1;
    private static final int TWO_CHANNELS_LAYOUT = 2;
    private static final int THREE_CHANNELS_LAYOUT = 3;
    private static final int RECORD_TIMER_LAYOUT = -1;
    private static final int RECORD_ANALYSIS_LAYOUT = -2;
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_SENSOR_ID = 3;
    private static final int REQUEST_LOGIN = 4;
    private static final int MAX_RECORD_SEGMENT_DURATION = 30; //In Seconds (I minute)
    private static final int MAX_RECORD_DURATION = 1800; //In seconds (30 minutes)
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND_IN_MILLIS = 1000;
    private static final int PRE_RECORD_START_DURATION = 10000; //In milliseconds
    private static final int PRE_AUTO_RECONNECTION_PAUSE_DURATION = 5000; //In milliseconds
    private static final int GRAPH_DISPLAY_TIMEOUT = 120000; //In milliseconds
    private static final int ECG = 1;
    private static final int PPG = 3;
    private static final int ACC = 4;
    private static final int IMP = 5;
    private static final int mNotifId = 0;

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private TextView feedBackMsgTV, curDispDataType,
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
    private boolean mShowECG, mShowPPG, mShowAccel, mShowImpedance;
    private boolean mRecording;
    private boolean mUserInitiatedDisconnection;
    private boolean mDataDisplayOn;
    private boolean mSamplesRecieved;
    private boolean mAutoConnectOn;
    private boolean mPainStart;

    private Patient mPatient;

    private GraphviewFragment mGraphFragment;
    private RecordFragment mRecordFragment;
    private RecordAnalysisFragment mRecordAnalysisFragment;

    private NotificationManager mNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.vitalsens_launcher)
                        .setContentTitle("Vitalsens App")
                        .setContentText("Touch to return to App");
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        mBuilder.setContentIntent(pendingIntent);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(mNotifId, mBuilder.build());

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

        btnConnectDisconnect = (Button) findViewById(R.id.btn_connect);
        curTemperature = (TextView) findViewById(R.id.cur_temp);
        batLevel = (TextView) findViewById(R.id.bat_level);
        batLevelTxt = (TextView) findViewById(R.id.bat_level_text);
        hrValue = (TextView) findViewById(R.id.hr_value);
        patientId = (TextView) findViewById(R.id.patient_id);
        TextView btnNavRight = (TextView) findViewById(R.id.btn_nav_right);
        TextView btnNavLeft = (TextView) findViewById(R.id.btn_nav_left);
        Button btnHistory = (Button) findViewById(R.id.btn_history);
        curDispDataType = (TextView) findViewById(R.id.cur_disp_dataType);
        feedBackMsgTV = (TextView) findViewById(R.id.connected_devices);
        Button btnPain = (Button) findViewById(R.id.pain_btn);

        mHandler = new Handler();
        mRecordStartTimer = null;
        mAutoConnectTimer = null;
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

        mFragManager = getFragmentManager();

        setGraphLayout(MAIN_LAYOUT);

        service_init();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                        Intent getSensorIdIntent = new Intent(MainActivity.this, LoginConnectDialog.class);
                        getSensorIdIntent.setType(LoginConnectDialog.ACTION_GET_SENSOR_ID);
                        startActivityForResult(getSensorIdIntent, REQUEST_SENSOR_ID);
                    } else if(mConnectionState == BLEService.STATE_CONNECTING){
                        if(mAutoConnectTimer != null && !mAutoConnectOn) {
                            mAutoConnectTimer.cancel();
                            mConnectionState = BLEService.STATE_DISCONNECTED;
                            btnConnectDisconnect.setText(R.string.connect);
                            feedBackMsgTV.setText(R.string.empty);
                            btnConnectDisconnect.setEnabled(true);
                        }else if(mAutoConnectOn) {
                            mAutoConnectOn = false;
                            feedBackMsgTV.setText(R.string.disconnecting);
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

        btnNavRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNextIndex < (mAvailableDataTypes.size() - 1) && mConnectionState == BLEService.STATE_CONNECTED) {
                    mNextIndex++;
                    displayData();
                }
            }
        });

        btnNavLeft.setOnClickListener(new View.OnClickListener() {
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
        }
        Intent loginIntent = new Intent(MainActivity.this, LoginConnectDialog.class);
        loginIntent.setType(LoginConnectDialog.ACTION_LOGIN);
        startActivityForResult(loginIntent, REQUEST_LOGIN);
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
                case CloudAccessService.ACTION_CLOUD_ACCESS_RESULT:
                    String res = intent.getStringExtra(CloudAccessService.EXTRA_RESULT);
                    try{
                        if(res.contains("heartRate") && mConnectionState == BLEService.STATE_CONNECTED){
                            int hr = new JSONObject(res).getInt("heartRate");
                            updateHRValue(hr);
                        }
                        if(res.contains("message")){
                            res = new JSONObject(res).getString("message");
                        }
                    }catch(Exception e){
                        Log.e(TAG, "JSON Parser Error: "+ e.getLocalizedMessage());
                    }
                    feedBackMsgTV.setText(res);
                    CloudAccessService.startActionCloudAccess(MainActivity.this, mPatient.getAuthKey());
                    break;
                case SaveRecordService.ACTION_SAVE_RECORD_STATUS:
                    showMessage(intent.getStringExtra(SaveRecordService.EXTRA_STATUS));
                case BLEService.DATA_RECIEVED:
                    mSamplesRecieved = true;
                    final double[] samples = intent.getDoubleArrayExtra(Intent.EXTRA_TEXT);
                    if(samples != null) {
                        final int dataId = (int) samples[0];
                        long recEndTimeStamp = 0;

                        if (mRecSegmentEnd > 0) {
                            long start, end;
                            start = end = mRecSegmentEnd;
                            mRecSegmentEnd = 0;
                            if (mRecEnd > 0) {
                                recEndTimeStamp = System.currentTimeMillis();
                                mRecStart = start;
                                mRecEnd = 0;
                            }

                            for (int key : mRecords.keySet()) {
                                Record rec = mRecords.put(key, new Record(mRecStart, mPatient.getPatientId(), start, key));
                                if (!rec.isEmpty()) {
                                    rec.setEnd(end);
                                    rec.setTemp(mCurTemp);
                                    rec.setRecEnd(recEndTimeStamp);
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
                                            for (int i = 1, j = 0; i < samples.length; i += 3, j++) {
                                                if (Double.isNaN(samples[i])) {
                                                    double[] result =
                                                            {samples[i], samples[i + 1], samples[i + 2]};
                                                    if(j%2 == 0){
                                                        mGraphFragment.updateGraph(result);
                                                    }
                                                } else {
                                                    double[] result = {
                                                            samples[i + 2] - samples[i + 1],
                                                            samples[i] - samples[i + 1],
                                                            samples[i] - samples[i + 2]
                                                    };
                                                    if(j%2 == 0){
                                                        mGraphFragment.updateGraph(result);
                                                    }
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
                            case ACC:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i += 3) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(ACC).addToChOne(null);
                                                    mRecords.get(ACC).addToChTwo(null);
                                                    mRecords.get(ACC).addToChThree(null);
                                                } else {
                                                    mRecords.get(ACC).addToChOne(samples[i]);
                                                    mRecords.get(ACC).addToChTwo(samples[i + 1]);
                                                    mRecords.get(ACC).addToChThree(samples[i + 2]);
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
                            case IMP:
                                (new Runnable() {
                                    public void run() {
                                        if (mRecording) {
                                            for (int i = 1; i < samples.length; i++) {
                                                if (Double.isNaN(samples[i])) {
                                                    mRecords.get(IMP).addToChOne(null);
                                                } else {
                                                    mRecords.get(IMP).addToChOne(samples[i]);
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
                    feedBackMsgTV.setText(R.string.connecting);
                    mService.connect(mSensorAddresses);
                    btnConnectDisconnect.setEnabled(false);
                }else if(resultCode == SensorList.DEVICE_NOT_FOUND){
                    showMessage(mPatient.getSensorId() + " not found, try again");
                    Intent getSensorIdIntent = new Intent(MainActivity.this, LoginConnectDialog.class);
                    getSensorIdIntent.setType(LoginConnectDialog.ACTION_GET_SENSOR_ID);
                    startActivityForResult(getSensorIdIntent, REQUEST_SENSOR_ID);
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
            case REQUEST_SENSOR_ID:
                if(resultCode == Activity.RESULT_OK && data != null){
                    String sId = data.getStringExtra(Intent.EXTRA_TEXT);
                    if(sId != null) {
                        mPatient.setSensorId(sId);
                    }
                    Intent getSensorIntent = new Intent(MainActivity.this, SensorList.class);
                    getSensorIntent.putExtra(Intent.EXTRA_TEXT, mPatient.getSensorId());
                    startActivityForResult(getSensorIntent, REQUEST_SELECT_DEVICE);
                }else if(resultCode == Activity.RESULT_CANCELED){
                    Log.d(TAG, "User canceled request");
                }
                break;
            case REQUEST_LOGIN:
                if(resultCode == Activity.RESULT_OK && data != null){
                    String pStr = data.getStringExtra(Intent.EXTRA_TEXT);
                    if(pStr != null) {
                        mPatient = Patient.fromJson(pStr);
                    }
                    Intent getSensorIdIntent = new Intent(MainActivity.this, LoginConnectDialog.class);
                    getSensorIdIntent.setType(LoginConnectDialog.ACTION_GET_SENSOR_ID);
                    startActivityForResult(getSensorIdIntent, REQUEST_SENSOR_ID);
                }else if(resultCode == Activity.RESULT_CANCELED){
                    finish();
                } else{
                    Log.d(TAG, "User Authentication failed");
                    Intent loginIntent = new Intent(MainActivity.this, LoginConnectDialog.class);
                    loginIntent.setType(LoginConnectDialog.ACTION_LOGIN);
                    startActivityForResult(loginIntent, REQUEST_LOGIN);
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
        mNotificationManager.cancel(mNotifId);
        mPatient.setAuthKey("");
        mPatient.setPassword("");
        savePatient(mPatient);
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

        setGraphDisplayTimeout(GRAPH_DISPLAY_TIMEOUT);

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
        addAvailableDataType(mPatient.getSensorId());
        if(!mDataDisplayOn) {
            displayData();
            mDataDisplayOn = true;
        }
        mConnectionState=BLEService.STATE_CONNECTED;
        btnConnectDisconnect.setText(R.string.disconnect);
        btnConnectDisconnect.setEnabled(true);
        if(mNumConnectedSensors == 0) {
            feedBackMsgTV.setText(sensorName);
        }
        else{
            String str = feedBackMsgTV.getText().toString() + "," + sensorName;
            feedBackMsgTV.setText(str);
        }
        mNumConnectedSensors++;
        patientId.setText(mPatient.getPatientId());

        mRecordStartTimer = new CountDownTimer(PRE_RECORD_START_DURATION, 1000){
            public void onTick(long millisUntilFinished) {
                Log.w(TAG, "seconds remaining: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                mRecordingStart = mRecStart = System.currentTimeMillis();;
                for(int dataId : Record.DATA_TYPES.values()){
                    Record rec = new Record(mRecStart, mPatient.getPatientId(), mRecStart, dataId);
                    mRecords.put(dataId, rec);
                }

                mRecEnd = mRecSegmentEnd = 0;
                mRecording = true;
                mRecordTimer.run();
                CloudAccessService.startActionCloudAccess(MainActivity.this, mPatient.getAuthKey());
                setGraphDisplayTimeout(GRAPH_DISPLAY_TIMEOUT);
            }
        }.start();
    }

    private void handleGattDisconnectionEvent(){
        curDispDataType.setText("");
        feedBackMsgTV.setText("");
        curTemperature.setText("");
        mCurTemp = 0;
        batLevel.setBackgroundResource(0);
        batLevelTxt.setText("");
        hrValue.setText("");
        patientId.setText("");
        mNextIndex = 0;
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
                    feedBackMsgTV.setText(str);
                }

                public void onFinish() {
                    feedBackMsgTV.setText(R.string.starting_auto_connect);
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
                rec.setRecEnd(end);
                if(mPainStart){
                    rec.setPEEnd(rec.getChOne().size());
                    mPainStart = false;
                }
                rec.setTemp(mCurTemp);
                CloudAccessService.startActionCloudAccess(MainActivity.this, mPatient.getAuthKey());
                SaveRecordService.startActionSaveRecord(MainActivity.this, rec);
            }
        }
        mRecords.clear();
        mHandler.removeCallbacks(mRecordTimer);
        mHandler.removeCallbacks(mDisplayTimerView);
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

    private void savePatient(Patient patient) {
        String pStr = Patient.toJson(patient);
        if(pStr != null) {
            if (IOOperations.isExternalStorageWritable()) {
                String status = IOOperations.writeFileExternal(PATIENT_DIRECTORY, PATIENT_FILE, pStr, false);
                if(status.equals(IOOperations.DATA_SAVED)){
                    showMessage("Patient's Info saved");
                }else{
                    showMessage(status);
                }
            } else {
                showMessage("Cannot write to storage!");
            }
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
        if(hr == 1000){
            hrValue.setText(R.string.hr_NA);
        }else{
            String hrStr = hr + " BPM";
            hrValue.setText(hrStr);
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

    private Runnable mDisplayTimerView = new Runnable() {
        public void run() {
            if(mNextIndex == RECORD_TIMER_LAYOUT){
                return;
            }
            mNextIndex = RECORD_TIMER_LAYOUT;
            displayData();
        }
    };

    private void setGraphDisplayTimeout(int timeout){
        mHandler.removeCallbacks(mDisplayTimerView);
        mHandler.postAtTime(mDisplayTimerView, SystemClock.uptimeMillis() + timeout);
    }
}
