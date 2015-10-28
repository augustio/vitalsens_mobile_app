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
        import android.bluetooth.BluetoothManager;
        import android.bluetooth.BluetoothProfile;
        import android.content.Context;
        import android.content.Intent;
        import android.os.Binder;
        import android.os.IBinder;
        import android.util.Log;

        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.Map;



/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLEService extends Service {
    private final static String TAG = BLEService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Thread mConnectionThread, mDisconnectionThread;
    private Map<String, BluetoothGatt> mConnectedSensors = new HashMap<>();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String sensorAddress = gatt.getDevice().getAddress();
            String sensorName = gatt.getDevice().getName();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to " + sensorAddress + ": " + sensorName);
                mConnectedSensors.put(sensorAddress, gatt);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from " + sensorAddress + ": " + sensorName);
                mConnectedSensors.remove(sensorAddress);
            }
        }
    };


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


    private void connectionLoop(final ArrayList<String> sensors){
        for(int i = 0; i < sensors.size(); i++){
            mBluetoothAdapter.getRemoteDevice(sensors.get(i))
                    .connectGatt(getApplicationContext(), false, mGattCallback);
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {}
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

    private void disconnectionLoop(final ArrayList<String> sensors){
        for(int i = 0; i < sensors.size(); i++){
            String sensorAddress = sensors.get(i);
            BluetoothGatt gatt = mConnectedSensors.get(sensorAddress);
            if(gatt != null){
                gatt.disconnect();
                mConnectedSensors.remove(sensorAddress);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {}
        }
    }


}
