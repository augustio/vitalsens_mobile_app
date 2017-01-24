/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vitalsens.vitalsensapp.services;

        import android.app.Service;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothGatt;
        import android.bluetooth.BluetoothGattCallback;
        import android.bluetooth.BluetoothGattCharacteristic;
        import android.bluetooth.BluetoothGattDescriptor;
        import android.bluetooth.BluetoothGattService;
        import android.bluetooth.BluetoothManager;
        import android.bluetooth.BluetoothProfile;
        import android.content.Context;
        import android.content.Intent;
        import android.os.Binder;
        import android.os.IBinder;
        import android.support.v4.content.LocalBroadcastManager;
        import android.util.Log;

        import java.lang.reflect.Array;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLEService extends Service {
    private final static String TAG = BLEService.class.getSimpleName();

    public static final int STATE_DISCONNECTED = 10;
    public static final int STATE_CONNECTING = 20;
    public static final int STATE_CONNECTED = 30;
    public static final int ECG = 1;
    public static final int PPG = 3;
    public static final int ACCELERATION = 4;
    public static final int IMPEDANCE_PNEUMOGRAPHY = 5;
    public static final int NAN = -4096;

    public final static String ACTION_GATT_CONNECTED =
            "vitalsens.vitalsensapp.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "vitalsens.vitalsensapp.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "vitalsens.vitalsensapp.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_CLOUD_ACCESS_RESULT =
            "vitalsens.vitalsensapp.ACTION_CLOUD_ACCESS_RESULT";
    public static final String ECG_DATA_RECIEVED =
            "vitalsens.vitalsensapp.ECG_DATA_RECIEVED";
    public static final String PPG_DATA_RECIEVED =
            "vitalsens.vitalsensapp.PPG_DATA_RECIEVED";
    public static final String ACCELERATION_DATA_RECIEVED =
            "vitalsens.vitalsensapp.ACCELERATION_DATA_RECIEVED";
    public static final String IMPEDANCE_PNEUMOGRAPHY_DATA_RECIEVED =
            "vitalsens.vitalsensapp.IMPEDANCE_PNEUMOGRAPHY_DATA_RECIEVED";
    public static final String TEMP_VALUE =
            "vitalsens.vitalsensapp.TEMP_VALUE";
    public static final String BATTERY_LEVEL =
            "vitalsens.vitalsensapp.BATTERY_LEVEL";
    public static final String HR = "vitalsens.vitalsensapp.HR";


    private final static UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID HT_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    public  final static UUID HR_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private final static UUID HR_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private final static UUID HT_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private final static int HIDE_MSB_8BITS_OUT_OF_32BITS = 0x00FFFFFF;
    private final static int HIDE_MSB_8BITS_OUT_OF_16BITS = 0x00FF;
    private final static int SHIFT_LEFT_8BITS = 8;
    private final static int SHIFT_LEFT_16BITS = 16;
    private final static int GET_BIT24 = 0x00400000;
    private final static int FIRST_BIT_MASK = 0x01;
    private final static int NUM_DATA_TYPES = 6;
    private final static int INVALID_PKT_N0 = -1;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Thread mConnectionThread, mDisconnectionThread, mNotificationThread;
    private int mConnectionState;
    private ArrayList<Integer> mPrevPktNums = new ArrayList<>(6);

    private BluetoothGattCharacteristic mRXCharacteristic = null, mHTCharacteristic = null,
    mBatteryLevelCharacteristic = null, mHRCharacteristic = null;

    private Map<String, BluetoothGatt> mConnectedSensors = new HashMap<>();

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String sensorAddress = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectedSensors.put(sensorAddress, gatt);
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(ACTION_GATT_CONNECTED, deviceName);
                for(int i = 0; i < NUM_DATA_TYPES; i++)
                    mPrevPktNums.add(INVALID_PKT_N0);
                Log.d(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close();
                mConnectedSensors.remove(sensorAddress);
                Log.d(TAG, "Disconnected from " + deviceName + ": " + sensorAddress);
                mConnectionState = STATE_DISCONNECTED;
                if(mConnectedSensors.isEmpty()) {
                    mConnectionState = STATE_DISCONNECTED;
                    mRXCharacteristic = mHTCharacteristic = mBatteryLevelCharacteristic =
                            mHRCharacteristic = null;
                    broadcastUpdate(ACTION_GATT_DISCONNECTED);
                    Log.d(TAG, "Disconnected from all sensors");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "BluetoothGatt = " + gatt);
                List<BluetoothGattService> services = gatt.getServices();
                ArrayList<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
                for (BluetoothGattService service : services) {
                    if (service.getUuid().equals(UART_SERVICE_UUID)) {
                        mRXCharacteristic = service.getCharacteristic(RX_CHAR_UUID);
                        characteristics.add(mRXCharacteristic);
                    }else if (service.getUuid().equals(HT_SERVICE_UUID)) {
                        mHTCharacteristic = service.getCharacteristic(HT_MEASUREMENT_CHARACTERISTIC_UUID);
                        characteristics.add(mHTCharacteristic);
                    }else if (service.getUuid().equals(BATTERY_SERVICE_UUID)) {
                        mBatteryLevelCharacteristic = service.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID);
                        characteristics.add(mBatteryLevelCharacteristic);
                    }else if (service.getUuid().equals(HR_SERVICE_UUID)){
                        mHRCharacteristic = service.getCharacteristic(HR_CHARACTERISTIC_UUID);
                        characteristics.add(mHRCharacteristic);
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                enableNotification(characteristics, gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().equals(RX_CHAR_UUID)) {
                processRXData(characteristic.getValue());
            }
            if(characteristic.getUuid().equals(HT_MEASUREMENT_CHARACTERISTIC_UUID)){
                try {
                    double tempValue = decodeTemperature(characteristic.getValue());
                    broadcastUpdate(TEMP_VALUE, tempValue);
                } catch (Exception e) {
                    Log.e(TAG, "Invalid temperature value: "+e.getMessage());
                }
            }
            if(characteristic.getUuid().equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)){
                int batLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                broadcastUpdate(BATTERY_LEVEL, batLevel);
            }
            if(characteristic.getUuid().equals(HR_CHARACTERISTIC_UUID)){
                int hr = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                broadcastUpdate(HR, hr);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(status != BluetoothGatt.GATT_SUCCESS ){
                Log.d(TAG, "GATT operation failed with status = " + status);
            }
            else {
                Log.d(TAG,  "Descriptor written: uuid: " + descriptor.getCharacteristic().getUuid().toString() + " status = " + status );
            }
        }
    };

    private void broadcastUpdate(final String action, String strValue ) {
        final Intent intent = new Intent(action);
        intent.putExtra(Intent.EXTRA_TEXT, strValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, double doubleValue ) {
        final Intent intent = new Intent(action);
        intent.putExtra(Intent.EXTRA_TEXT, doubleValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, int intValue ) {
        final Intent intent = new Intent(action);
        intent.putExtra(Intent.EXTRA_TEXT, intValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final int[] value) {
        final Intent intent = new Intent(action);
        intent.putExtra(Intent.EXTRA_TEXT, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public void connect(final ArrayList<String> sensorAddresses) {
        if(sensorAddresses == null)
            return;
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized ");
            return;
        }

        if(mConnectionThread == null){
            mConnectionThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectionLoop(sensorAddresses);
                    mConnectionThread.interrupt();
                    mConnectionThread = null;
                }
            });

            mConnectionThread.start();
        }
    }


    private void connectionLoop(final ArrayList<String> sensorAddresses){
        for(String address : sensorAddresses){
            if(mConnectedSensors.containsKey(address)){
                BluetoothGatt gatt = mConnectedSensors.get(address);
                gatt.connect();
            }else{
                mBluetoothAdapter.getRemoteDevice(address)
                        .connectGatt(getApplicationContext(), false, mGattCallback);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    public void disconnect(final ArrayList<String> sensorAddresses) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized ");
            return;
        }

        if(mDisconnectionThread == null){
            mDisconnectionThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    disconnectionLoop(sensorAddresses);
                    mDisconnectionThread.interrupt();
                    mDisconnectionThread = null;
                }
            });

            mDisconnectionThread.start();
        }
    }

    private void disconnectionLoop(final ArrayList<String> sensorAddresses){
        if(sensorAddresses != null){
            for (String address : sensorAddresses) {
                BluetoothGatt gatt = mConnectedSensors.get(address);
                if (gatt != null) {
                    gatt.disconnect();
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        }
    }

    public void enableNotification(final ArrayList<BluetoothGattCharacteristic> characteristics, final BluetoothGatt gatt) {
        if(characteristics == null)
            return;
        if(mNotificationThread == null){
            mNotificationThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    notificationLoop(characteristics, gatt);
                    mNotificationThread.interrupt();
                    mNotificationThread = null;
                }
            });

            mNotificationThread.start();
        }
    }

    private void notificationLoop(final ArrayList<BluetoothGattCharacteristic> characteristics, BluetoothGatt gatt){
        for(BluetoothGattCharacteristic ch : characteristics){
            if (ch != null && mConnectionState == STATE_CONNECTED) {
                gatt.setCharacteristicNotification(ch, true);
                BluetoothGattDescriptor descriptor = ch.getDescriptor(CCCD_UUID);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        }
    }

    private int calculatePacketLoss(int pktNum, int prevPktNum){
        if(prevPktNum < 0)
            return 0;
        else if(prevPktNum == 255 || prevPktNum > pktNum)
            return (pktNum + 255) - prevPktNum;
        else
            return pktNum - (prevPktNum + 1);
    }

    private void processRXData(byte[] data){
        if(data.length == 20) {

            int dataId = ((data[0] & 0XFF) >> 5);
            int packetNumber = (data[1] & 0XFF);
            int numPacketsLost;

            int sensorData[] = new int[13];
            int lostData[] = new int[13];
            for (int i = 1; i < lostData.length; i++)
                lostData[i] = NAN;
            sensorData[0] = lostData[0] = dataId;

            for (int i = 1, j = 2; i < sensorData.length; i += 2, j += 3) {
                sensorData[i] = (data[j] & 0XFF) << 4 | (data[j + 1] & 0XF0) >> 4;
                sensorData[i + 1] = (data[j + 1] & 0X0F) << 8 | (data[j + 2] & 0XFF);
            }

            switch (dataId) {
                case ECG:
                    numPacketsLost = calculatePacketLoss(packetNumber, mPrevPktNums.get(dataId));
                    mPrevPktNums.set(dataId, packetNumber);
                    if (numPacketsLost > 0) {
                        lostData[0] = dataId;
                        for (int i = 0; i < numPacketsLost; i++) {
                            broadcastUpdate(ECG_DATA_RECIEVED, lostData);
                        }
                        Log.e(TAG, "Packet Lost (ECG3): " + numPacketsLost);
                    }
                    broadcastUpdate(ECG_DATA_RECIEVED, sensorData);
                    break;
                case PPG:
                    numPacketsLost = calculatePacketLoss(packetNumber, mPrevPktNums.get(dataId));
                    mPrevPktNums.set(dataId, packetNumber);
                    if (numPacketsLost > 0) {
                        lostData[0] = dataId;
                        for (int i = 0; i < numPacketsLost; i++) {
                            broadcastUpdate(PPG_DATA_RECIEVED, lostData);
                        }
                        Log.e(TAG, "Packet Lost (PPG2): " + numPacketsLost);
                    }
                    broadcastUpdate(PPG_DATA_RECIEVED, sensorData);
                    break;
                case ACCELERATION:
                //Get negative acceleration values. Max unsigned value = 4096.
                //Max positive signed value = 2047
                    for (int i = 0; i < sensorData.length; i++) {
                        int value = sensorData[i];
                        if (value > 2047) {
                            sensorData[i] = value - 4096;
                        }
                    }
                    numPacketsLost = calculatePacketLoss(packetNumber, mPrevPktNums.get(dataId));
                    mPrevPktNums.set(dataId, packetNumber);
                    if (numPacketsLost > 0) {
                        lostData[0] = dataId;
                        for (int i = 0; i < numPacketsLost; i++) {
                            broadcastUpdate(ACCELERATION_DATA_RECIEVED, lostData);
                        }
                        Log.e(TAG, "Packet Lost (ACCELERATION): " + numPacketsLost);
                    }
                    broadcastUpdate(ACCELERATION_DATA_RECIEVED, sensorData);
                    break;
                case IMPEDANCE_PNEUMOGRAPHY:
                    numPacketsLost = calculatePacketLoss(packetNumber, mPrevPktNums.get(dataId));
                    mPrevPktNums.set(dataId, packetNumber);
                    if (numPacketsLost > 0) {
                        lostData[0] = dataId;
                        for (int i = 0; i < numPacketsLost; i++) {
                            broadcastUpdate(IMPEDANCE_PNEUMOGRAPHY_DATA_RECIEVED, lostData);
                        }
                        Log.e(TAG, "Packet Lost (IMPEDANCE PNEUMOGRAPHY): " + numPacketsLost);
                    }
                    broadcastUpdate(IMPEDANCE_PNEUMOGRAPHY_DATA_RECIEVED, sensorData);
                    break;
                default:
                    break;
            }
        }
    }

    private double decodeTemperature(byte[] data) throws Exception {
        double temperatureValue;
        byte flag = data[0];
        byte exponential = data[4];
        short firstOctet = convertNegativeByteToPositiveShort(data[1]);
        short secondOctet = convertNegativeByteToPositiveShort(data[2]);
        short thirdOctet = convertNegativeByteToPositiveShort(data[3]);
        int mantissa = ((thirdOctet << SHIFT_LEFT_16BITS) | (secondOctet << SHIFT_LEFT_8BITS) | (firstOctet)) & HIDE_MSB_8BITS_OUT_OF_32BITS;
        mantissa = getTwosComplimentOfNegativeMantissa(mantissa);
        temperatureValue = (mantissa * Math.pow(10, exponential));

        if ((flag & FIRST_BIT_MASK) != 0) {
            temperatureValue = (float) ((temperatureValue - 32) * (5 / 9.0));
        }
        return temperatureValue;
    }

    private short convertNegativeByteToPositiveShort(byte octet) {
        if (octet < 0) {
            return (short) (octet & HIDE_MSB_8BITS_OUT_OF_16BITS);
        } else {
            return octet;
        }
    }

    private int getTwosComplimentOfNegativeMantissa(int mantissa) {
        if ((mantissa & GET_BIT24) != 0) {
            return ((((~mantissa) & HIDE_MSB_8BITS_OUT_OF_32BITS) + 1) * (-1));
        } else {
            return mantissa;
        }
    }

    private void logData(String str, byte[] data){
        String sample = "";
        for(int i:data) {
            sample += "[";
            sample += (i & 0xFF);
            sample += "]";
        }

        Log.d(TAG, str + ": " + sample);
    }

    private void logData(String str, double [] data){
        String sample = "";
        for(double d:data) {
            sample += "[";
            sample += d;
            sample += "]";
        }

        Log.d(TAG, str + ": " + sample);
    }

}
