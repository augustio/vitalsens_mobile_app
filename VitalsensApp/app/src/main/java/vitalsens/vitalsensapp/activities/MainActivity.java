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
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
import vitalsens.vitalsensapp.fragments.ChannelOneFragment;
import vitalsens.vitalsensapp.fragments.ChannelThreeFragment;
import vitalsens.vitalsensapp.fragments.ChannelTwoFragment;
import vitalsens.vitalsensapp.fragments.MainFragment;
import vitalsens.vitalsensapp.models.DataPacket;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.models.Sensor;
import vitalsens.vitalsensapp.services.BLEService;

public class MainActivity extends Activity {

    private static final String TAG = "VitalsensApp";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int MAX_DATA_RECORDING_TIME = 120;// 2 minutes
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND = 1000;// 1000 milliseconds in one second

    private BluetoothAdapter mBluetoothAdapter;
    private Button btnConnectDisconnect;
    private Button btnRecord;
    private TextView sensorNamesView;
    private TextView ecgOneViewCtr, ecgThreeViewCtr;
    private TextView ppgOneViewCtr, ppgTwoViewCtr;
    private TextView accelViewCtr, impedanceViewCtr;
    private LinearLayout sensorVeiwCtrlPad, mainContainer, chOne, chTwo, chThree;
    private Handler mHandler;
    private BLEService mService;
    private ArrayList<Sensor> mConnectedSensors;
    private ArrayList<Record> mRecords;
    private ArrayList<DataPacket> mECG1Collection;
    private ArrayList<DataPacket> mECG3Collection;
    private ArrayList<DataPacket> mPPG1Collection;
    private ArrayList<DataPacket> mPPG2Collection;
    private ArrayList<DataPacket> mACCELCollection;
    private ArrayList<DataPacket> mIMPEDANCECollection;
    private int mConnectionState;
    private int mRecTimerCounter, min, sec, hr;
    private String mTimerString;
    private boolean mShowECGOne, mShowECGThree, mShowPPGOne,
            mShowPPGTwo, mShowAccel, mShowImpedance;
    private boolean mRecording;

    private MainFragment mainFrag;
    private ChannelOneFragment mChannelOne;
    private ChannelTwoFragment mChannelTwo;
    private ChannelThreeFragment mChannelThree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect = (Button) findViewById(R.id.btn_connect);
        btnRecord = (Button) findViewById(R.id.btn_record);
        sensorNamesView = (TextView) findViewById(R.id.connected_sensors);
        sensorNamesView.setText("Connected Sensors:");
        sensorVeiwCtrlPad = (LinearLayout) findViewById(R.id.sensor_view_ctr_layout);
        mainContainer = (LinearLayout) findViewById(R.id.main_container);
        chOne = (LinearLayout) findViewById(R.id.channel1_fragment);
        chTwo = (LinearLayout) findViewById(R.id.channel2_fragment);
        chThree = (LinearLayout) findViewById(R.id.channel3_fragment);
        ecgOneViewCtr = (TextView) findViewById(R.id.ecg1_ctr);
        ecgThreeViewCtr = (TextView) findViewById(R.id.ecg3_ctr);
        ppgOneViewCtr = (TextView) findViewById(R.id.ppg1_ctr);
        ppgTwoViewCtr = (TextView) findViewById(R.id.ppg2_ctr);
        accelViewCtr = (TextView) findViewById(R.id.accel_ctr);
        impedanceViewCtr = (TextView) findViewById(R.id.impedance_ctr);

        mHandler = new Handler();
        mConnectedSensors = new ArrayList<>();
        mRecords = new ArrayList<>();
        mECG1Collection = new ArrayList<>();
        mECG3Collection = new ArrayList<>();
        mPPG1Collection = new ArrayList<>();
        mPPG2Collection = new ArrayList<>();
        mACCELCollection = new ArrayList<>();
        mIMPEDANCECollection = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo =
                mShowAccel = mShowImpedance = false;
        mRecording = false;

        min = sec =  hr = 0;
        mTimerString = "";

        mainFrag = new MainFragment();
        mChannelOne = new ChannelOneFragment();
        mChannelTwo = new ChannelTwoFragment();
        mChannelThree = new ChannelThreeFragment();

        FragmentManager fragmentManager = getFragmentManager();

        fragmentManager.beginTransaction()
                .add(R.id.main_container, mainFrag, "main")
                .add(R.id.channel1_fragment, mChannelOne, "chOne")
                .add(R.id.channel2_fragment, mChannelTwo, "chTwo")
                .add(R.id.channel3_fragment, mChannelThree, "chThree")
                .commit();

        setGraphLayout(0);

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
                        Intent newIntent = new Intent(MainActivity.this, SensorList.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else if (mConnectionState == BLEService.STATE_CONNECTED) {
                        mService.disconnect(mConnectedSensors);
                    }
                }
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mConnectionState == BLEService.STATE_CONNECTED){
                    if(mRecording)
                        stopRecordingData();
                    else {
                        setGraphLayout(0);
                        enableSensorViewControlPad(false);
                        for(Sensor sensor : mConnectedSensors) {
                            mRecords.add(new Record(sensor.getName(),
                                    new SimpleDateFormat("yyMMddHHmmss",
                                            Locale.US).format(new Date())));
                        }
                        mRecording = true;
                        btnRecord.setText("Stop");
                        mRecordTimer.run();
                    }
                }
            }
        });

        ecgOneViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowECGOne && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGThree = mShowPPGOne = mShowPPGTwo =
                            mShowAccel = mShowImpedance = false;
                    setGraphLayout(1);
                    mShowECGOne = true;
                }
            }
        });
        ecgThreeViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowECGThree && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGOne = mShowPPGOne = mShowPPGTwo =
                            mShowAccel = mShowImpedance = false;
                    setGraphLayout(3);
                    mShowECGThree = true;
                }
            }
        });
        ppgOneViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowPPGOne && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGOne = mShowECGThree = mShowPPGTwo =
                            mShowAccel = mShowImpedance = false;
                    setGraphLayout(1);
                    mShowPPGOne = true;
                }
            }
        });
        ppgTwoViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowPPGTwo && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGOne = mShowECGThree = mShowPPGOne =
                            mShowAccel = mShowImpedance = false;
                    setGraphLayout(2);
                    mShowPPGTwo = true;
                }
            }
        });
        accelViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowAccel && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGOne = mShowECGThree = mShowPPGOne =
                            mShowPPGTwo = mShowImpedance = false;
                    setGraphLayout(3);
                    mShowAccel = true;
                }
            }
        });
        impedanceViewCtr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowImpedance && mConnectionState == BLEService.STATE_CONNECTED) {
                    mShowECGOne = mShowECGThree = mShowPPGOne =
                            mShowPPGTwo = mShowAccel = false;
                    setGraphLayout(1);
                    mShowImpedance = true;
                }
            }
        });
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
                final String sensorStr = intent.getStringExtra(Intent.EXTRA_TEXT);
                runOnUiThread(new Runnable() {
                    public void run() {
                        handleGattDisconnectionEvent(sensorStr);
                    }
                });
            }
            if (action.equals(BLEService.ACTION_ALL_SENSORS_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        handleSensorsDisconnectionEvent();
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
            if (action.equals(BLEService.ACTION_DESCRIPTOR_WRITTEN)) {
                final String str = intent.getStringExtra(Intent.EXTRA_TEXT);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, str);
                    }
                });
            }
            if (action.equals(BLEService.ONE_CHANNEL_ECG)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            if(mRecording)
                                mECG1Collection.add(new DataPacket(samples));
                            if (mShowECGOne) {
                                String str = "Packet Number: " + samples[1] + "{ECG1 Samples: ";
                                for (int i = 2; i < samples.length; i++) {
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
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
                            if(mRecording)
                                mECG3Collection.add(new DataPacket(samples));
                            if (mShowECGThree) {
                                String str = "Packet Number: " + samples[1] + "{ECG3 Samples: ";
                                for (int i = 2; i < samples.length; i += 3) {
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                    str += (samples[i + 1] + "-");
                                    mChannelThree.updateGraph(samples[i + 2]);
                                    str += (samples[i + 2] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
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
                            if(mRecording)
                                mPPG1Collection.add(new DataPacket(samples));
                            if (mShowPPGOne) {
                                String str = "Packet Number: " + samples[1] + "{PPG1 Samples: ";
                                for (int i = 2; i < samples.length; i++) {
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
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
                            if(mRecording)
                                mPPG2Collection.add(new DataPacket(samples));
                            if (mShowPPGTwo) {
                                String str = "Packet Number: " + samples[1] + "{PPG2 Samples: ";
                                for (int i = 2; i < samples.length; i += 2) {
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                    str += (samples[i + 1] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
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
                            if(mRecording)
                                mACCELCollection.add(new DataPacket(samples));
                            if (mShowAccel) {
                                String str = "Packet Number: " + samples[1] + "{Accel Samples: ";
                                for (int i = 2; i < samples.length; i += 3) {
                                    if (samples[i] == 0)
                                        continue;
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                    mChannelTwo.updateGraph(samples[i + 1]);
                                    str += (samples[i + 1] + "-");
                                    mChannelThree.updateGraph(samples[i + 2]);
                                    str += (samples[i + 2] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
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
                            if(mRecording)
                                mIMPEDANCECollection.add(new DataPacket(samples));
                            if (mShowImpedance) {
                                String str = "Packet Number: " + samples[1] + "{Impedance Samples: ";
                                for (int i = 2; i < samples.length; i++) {
                                    mChannelOne.updateGraph(samples[i]);
                                    str += (samples[i] + "-");
                                }
                                str += "}";
                                Log.d(TAG, str.replace("-}", "}"));
                            }
                        }
                    }
                }).run();
            }
        }
    };

    private static IntentFilter sensorStatusUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DESCRIPTOR_WRITTEN);
        intentFilter.addAction(BLEService.ACTION_ALL_SENSORS_DISCONNECTED);
        intentFilter.addAction(BLEService.ONE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.ONE_CHANNEL_PPG);
        intentFilter.addAction(BLEService.TWO_CHANNEL_PPG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ACCELERATION);
        intentFilter.addAction(BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY);
        intentFilter.addAction(BLEService.OTHER_DATA_TYPES);
        return intentFilter;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<String> sensorAddresses = data.getStringArrayListExtra("SENSOR_LIST");
                    mService.connect(sensorAddresses);
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


    private void setGraphLayout(int channels) {
        clearGraphLayout();
        switch (channels) {
            case 0:
                mainContainer.setVisibility(View.VISIBLE);
                break;
            case 1:
                chOne.setVisibility(View.VISIBLE);
                break;
            case 2:
                chOne.setVisibility(View.VISIBLE);
                chTwo.setVisibility(View.VISIBLE);
                break;
            case 3:
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
        mainContainer.setVisibility(View.GONE);
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo
                = mShowAccel = mShowImpedance = false;
    }

    private void enableSensorViewControlPad(Boolean enable){
        if(enable) {
            sensorVeiwCtrlPad.setEnabled(true);
            sensorVeiwCtrlPad.setVisibility(View.VISIBLE);
        }else{
            sensorVeiwCtrlPad.setEnabled(false);
            sensorVeiwCtrlPad.setVisibility(View.GONE);
        }
    }

    private void enableControlButton(String type) {
        View v = null;
        switch (type){
            case BLEService.TYPE0:
                v = ecgOneViewCtr;
                break;
            case BLEService.TYPE1:
                v = ecgThreeViewCtr;
                break;
            case BLEService.TYPE2:
                v = ppgOneViewCtr;
                break;
            case BLEService.TYPE3:
                v = ppgTwoViewCtr;
                break;
            case BLEService.TYPE4:
                v = accelViewCtr;
                break;
            case BLEService.TYPE5:
                v = impedanceViewCtr;
                break;
        }
        if (v != null) {
            v.setEnabled(true);
            v.setVisibility(View.VISIBLE);
        }
    }

    private void disableControlButton(String type) {
        View v = null;
        switch (type){
            case BLEService.TYPE0:
                v = ecgOneViewCtr;
                break;
            case BLEService.TYPE1:
                v = ecgThreeViewCtr;
                break;
            case BLEService.TYPE2:
                v = ppgOneViewCtr;
                break;
            case BLEService.TYPE3:
                v = ppgTwoViewCtr;
                break;
            case BLEService.TYPE4:
                v = accelViewCtr;
                break;
            case BLEService.TYPE5:
                v = impedanceViewCtr;
                break;
        }
        if (v != null) {
            v.setEnabled(false);
            v.setVisibility(View.GONE);
        }
    }

    private void handleGattConnectionEvent(String sensorStr) {
        Sensor sensor = new Sensor();
        sensor.fromJson(sensorStr);
        enableControlButton(sensor.getType());
        Log.d(TAG, "Connected to" + sensor.getName() +
                " : " + sensor.getAddress() +" : "+sensor.getType());
        mConnectedSensors.add(sensor);
        mConnectionState=BLEService.STATE_CONNECTED;
        btnConnectDisconnect.setText("Disconnect");
        sensorNamesView.setText(sensorNamesView.getText()+"  "+sensor.getName());
    }

    private void handleGattDisconnectionEvent(String sensorStr){
        Sensor sensor = new Sensor();
        sensor.fromJson(sensorStr);
        Log.d(TAG, "Disconnected from" + sensor.getName() +
                " : " + sensor.getAddress());
        disableControlButton(sensor.getType());
    }

    private void handleSensorsDisconnectionEvent(){
        mConnectedSensors.clear();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        btnConnectDisconnect.setText("Connect");
        sensorNamesView.setText("Connected Sensors:");
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo =
                mShowAccel = mShowImpedance = false;
        chOne.setVisibility(View.GONE);
        chTwo.setVisibility(View.GONE);
        chThree.setVisibility(View.GONE);
        mainContainer.setVisibility(View.VISIBLE);
        setGraphLayout(0);
        if(mRecording)
            stopRecordingData();
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
                        String fileName = record.getSensor() + "_" + record.getTimeStamp() + ".txt";
                        file = new File(dir, fileName);
                        Type type = new TypeToken<ArrayList<DataPacket>>() {
                        }.getType();
                        switch (record.getSensor()) {
                            case BLEService.ECG1:
                                record.setData(new Gson().toJson(mECG1Collection, type));
                                mECG1Collection.clear();
                                break;
                            case BLEService.ECG3:
                                record.setData(new Gson().toJson(mECG3Collection, type));
                                mECG3Collection.clear();
                                break;
                            case BLEService.PPG1:
                                record.setData(new Gson().toJson(mPPG1Collection, type));
                                mPPG1Collection.clear();
                                break;
                            case BLEService.PPG2:
                                record.setData(new Gson().toJson(mPPG2Collection, type));
                                mPPG2Collection.clear();
                                break;
                            case BLEService.ACCEL:
                                record.setData(new Gson().toJson(mACCELCollection, type));
                                mACCELCollection.clear();
                                break;
                            case BLEService.IMPEDANCE:
                                record.setData(new Gson().toJson(mIMPEDANCECollection, type));
                                mIMPEDANCECollection.clear();
                                break;
                            default:
                                break;
                        }
                        try {
                            FileWriter fw = new FileWriter(file, true);
                            fw.append(record.toJson());
                            fw.flush();
                            fw.close();
                            showMessage(record.getSensor() + " Record Saved");
                        } catch (IOException e) {
                            Log.e(TAG, e.toString());
                            showMessage("Problem writing to Storage");
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
                if (mRecTimerCounter >= MAX_DATA_RECORDING_TIME) {
                    stopRecordingData();
                    return;
                }
                if ((MAX_DATA_RECORDING_TIME - mRecTimerCounter) < 5)//Five seconds to the end of timer
                    ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.green));
                mRecTimerCounter++;
            mHandler.postDelayed(mRecordTimer, ONE_SECOND);
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
        ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.red));
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
            enableSensorViewControlPad(true);
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
