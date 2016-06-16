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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.w3c.dom.Text;

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
    private TextView btnRecord, connectedDevices, curDispDataType, curTemperature;
    private LinearLayout chOne, chTwo, chThree;
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
    private ArrayList<String> mAvailableDataTypes;
    private int mConnectionState;
    private int mRecTimerCounter, min, sec, hr;
    private int mNextIndex;
    private String mTimerString;
    private boolean mShowECGOne, mShowECGThree, mShowPPGOne,
            mShowPPGTwo, mShowAccel, mShowImpedance;
    private boolean mRecording;
    private boolean mInitDataDispOn;

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
        btnRecord = (TextView) findViewById(R.id.btn_record);
        curTemperature = (TextView) findViewById(R.id.cur_temp);
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
        mECG1Collection = new ArrayList<>();
        mECG3Collection = new ArrayList<>();
        mPPG1Collection = new ArrayList<>();
        mPPG2Collection = new ArrayList<>();
        mACCELCollection = new ArrayList<>();
        mIMPEDANCECollection = new ArrayList<>();
        mAvailableDataTypes = new ArrayList<>();
        mConnectionState = BLEService.STATE_DISCONNECTED;
        mRecording = false;
        mInitDataDispOn = false;

        min = sec =  hr = 0;
        mTimerString = "";

        mChannelOne = new ChannelOneFragment();
        mChannelTwo = new ChannelTwoFragment();
        mChannelThree = new ChannelThreeFragment();

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
                        Intent newIntent = new Intent(MainActivity.this, SensorList.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else if (mConnectionState == BLEService.STATE_CONNECTED) {
                        mService.disconnect(mConnectedSensors);
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
                        String date = new SimpleDateFormat("yyMMddHHmmss",
                                Locale.US).format(new Date());
                        for(Sensor sensor : mConnectedSensors) {
                            if(sensor.getName().equals(Sensor.ECG3))
                                mRecords.add(new Record(sensor.getName(), date));
                            mRecords.add(new Record(sensor.getName(), date));
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
        } else {
            if (mConnectionState == BLEService.STATE_DISCONNECTED) {
                Intent newIntent = new Intent(MainActivity.this, SensorList.class);
                startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
            } else if (mConnectionState == BLEService.STATE_CONNECTED) {
                mService.disconnect(mConnectedSensors);
            }
        }
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.graph_menu, menu);
            return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.samples_250) {
            mChannelOne.clearGraph();
            mChannelTwo.clearGraph();
            mChannelThree.clearGraph();
            mChannelOne.setxRange(250);
            mChannelTwo.setxRange(250);
            mChannelThree.setxRange(250);
            return true;
        }
        if (id == R.id.samples_500) {
            mChannelOne.clearGraph();
            mChannelTwo.clearGraph();
            mChannelThree.clearGraph();
            mChannelOne.setxRange(500);
            mChannelTwo.setxRange(500);
            mChannelThree.setxRange(500);
            return true;
        }
        if (id == R.id.samples_1000) {
            mChannelOne.clearGraph();
            mChannelTwo.clearGraph();
            mChannelThree.clearGraph();
            mChannelOne.setxRange(1000);
            mChannelTwo.setxRange(1000);
            mChannelThree.setxRange(1000);
            return true;
        }
        if (id == R.id.samples_2500) {
            mChannelOne.clearGraph();
            mChannelTwo.clearGraph();
            mChannelThree.clearGraph();
            mChannelOne.setxRange(2500);
            mChannelTwo.setxRange(2500);
            mChannelThree.setxRange(2500);
            return true;
        }
        if (id == R.id.samples_5000) {
            mChannelOne.clearGraph();
            mChannelTwo.clearGraph();
            mChannelThree.clearGraph();
            mChannelOne.setxRange(5000);
            mChannelTwo.setxRange(5000);
            mChannelThree.setxRange(5000);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    */

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
                            if(!mAvailableDataTypes.contains(Sensor.ECG_ONE_DATA)) {
                                mAvailableDataTypes.add(Sensor.ECG_ONE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
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
                            if(!mAvailableDataTypes.contains(Sensor.ECG_THREE_DATA)) {
                                mAvailableDataTypes.add(Sensor.ECG_THREE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
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
                            if(!mAvailableDataTypes.contains(Sensor.PPG_ONE_DATA)) {
                                mAvailableDataTypes.add(Sensor.PPG_ONE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
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
                            if(!mAvailableDataTypes.contains(Sensor.PPG_TWO_DATA)) {
                                mAvailableDataTypes.add(Sensor.PPG_TWO_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
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
                            if(!mAvailableDataTypes.contains(Sensor.ACCEL_DATA)) {
                                mAvailableDataTypes.add(Sensor.ACCEL_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
                            if(mRecording)
                                mACCELCollection.add(new DataPacket(samples));
                            if (mShowAccel) {
                                String str = "Packet Number: " + samples[1] + "{Accel Samples: ";
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
            if (action.equals(BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY)) {
                final int[] samples = intent.getIntArrayExtra(Intent.EXTRA_TEXT);
                (new Runnable() {
                    public void run() {
                        if (samples != null) {
                            if(!mAvailableDataTypes.contains(Sensor.IMPEDANCE_DATA)) {
                                mAvailableDataTypes.add(Sensor.IMPEDANCE_DATA);
                                if(!mInitDataDispOn){
                                    mInitDataDispOn = true;
                                    displayData();
                                }
                            }
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
            if (action.equals(BLEService.TEMP_VALUE)) {
                final double tempValue = intent.getDoubleExtra(Intent.EXTRA_TEXT, 1000);
                try {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (tempValue != 1000) {
                                curTemperature.setText("Temp: " + tempValue + "\u00B0");
                            }else{
                                curTemperature.setText("Temp: N/A");
                            }
                        }
                    });
                }catch (Exception e){
                    Log.e(TAG, "Following exception encountered: " + e.getMessage());
                }
            }
        }
    };

    private static IntentFilter sensorStatusUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DESCRIPTOR_WRITTEN);
        intentFilter.addAction(BLEService.ONE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ECG);
        intentFilter.addAction(BLEService.ONE_CHANNEL_PPG);
        intentFilter.addAction(BLEService.TWO_CHANNEL_PPG);
        intentFilter.addAction(BLEService.THREE_CHANNEL_ACCELERATION);
        intentFilter.addAction(BLEService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY);
        intentFilter.addAction(BLEService.TEMP_VALUE);
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
        curTemperature.setText("__");
        mShowECGOne = mShowECGThree = mShowPPGOne = mShowPPGTwo =
                mShowAccel = mShowImpedance = false;
        chOne.setVisibility(View.GONE);
        chTwo.setVisibility(View.GONE);
        chThree.setVisibility(View.GONE);
        mNextIndex = 0;
        mAvailableDataTypes.clear();
        mInitDataDispOn = false;
        clearGraphLayout();
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
                        Type type = new TypeToken<ArrayList<DataPacket>>() {
                        }.getType();
                        switch (record.getSensor()) {
                            case Sensor.ECG1:
                                if(!mECG1Collection.isEmpty()) {
                                    record.setData(new Gson().toJson(mECG1Collection, type));
                                    record.setDataType(0);
                                    mECG1Collection.clear();
                                }
                                break;
                            case Sensor.ECG3:
                                if(!mECG3Collection.isEmpty()) {
                                    record.setData(new Gson().toJson(mECG3Collection, type));
                                    record.setDataType(1);
                                    mECG3Collection.clear();
                                }
                                else if(!mACCELCollection.isEmpty()) {
                                    record.setData(new Gson().toJson(mACCELCollection, type));
                                    record.setDataType(4);
                                    mACCELCollection.clear();
                                }
                                break;
                            case Sensor.PPG1:
                                if(!mPPG1Collection.isEmpty()) {
                                    record.setData(new Gson().toJson(mPPG1Collection, type));
                                    record.setDataType(2);
                                    mPPG1Collection.clear();
                                }
                                break;
                            case Sensor.PPG2:
                                if(!mPPG2Collection.isEmpty()) {
                                    record.setData(new Gson().toJson(mPPG2Collection, type));
                                    record.setDataType(3);
                                    mPPG2Collection.clear();
                                }
                                break;
                            case Sensor.ACCEL:
                                if(!mACCELCollection.isEmpty()) {
                                    record.setData(new Gson().toJson(mACCELCollection, type));
                                    record.setDataType(4);
                                    mACCELCollection.clear();
                                }
                                break;
                            case Sensor.IMPEDANCE:
                                if(!mIMPEDANCECollection.isEmpty()) {
                                    record.setData(new Gson().toJson(mIMPEDANCECollection, type));
                                    record.setDataType(5);
                                    mIMPEDANCECollection.clear();
                                    break;
                                }
                            default:
                                break;
                        }
                        String fileName = record.getSensor() + "_" +
                                resolveDataType(record.getDataType()) + "_" +
                                record.getTimeStamp() + ".txt";
                        file = new File(dir, fileName);
                        if(!record.getData().equals("")) {
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
                        else{
                            showMessage("No " + record.getSensor() + " data recorded");
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

    private String resolveDataType(int intValue){
        String stringValue;
        switch (intValue){
            case 0:
                stringValue = Sensor.ECG_ONE_DATA;
                break;
            case 1:
                stringValue = Sensor.ECG_THREE_DATA;
                break;
            case 2:
                stringValue = Sensor.PPG_ONE_DATA;
                break;
            case 3:
                stringValue = Sensor.PPG_TWO_DATA;
                break;
            case 4:
                stringValue = Sensor.ACCEL_DATA;
                break;
            case 5:
                stringValue = Sensor.IMPEDANCE_DATA;
                break;
            default:
                stringValue = "";
        }
        return stringValue;
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
