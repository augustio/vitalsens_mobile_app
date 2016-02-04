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
        import android.support.v4.content.LocalBroadcastManager;
        import android.util.Log;

        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.Map;

        import vitalsens.vitalsensapp.models.Sensor;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLEService extends Service {
    private final static String TAG = BLEService.class.getSimpleName();

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "vitalsens.vitalsensapp.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "vitalsens.vitalsensapp.ACTION_GATT_DISCONNECTED";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Thread mConnectionThread, mDisconnectionThread;
    private int mConnectionState;

    private Map<String, BluetoothGatt> mConnectedSensors = new HashMap<>();


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Sensor sensor = new Sensor
                    (gatt.getDevice().getName(), gatt.getDevice().getAddress());
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectedSensors.put(sensor.getAddress(), gatt);
                mConnectionState = STATE_CONNECTED;
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction, sensor.toJson());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from " + sensor.getAddress() + ": " + sensor.getName());
                gatt.close();
                mConnectedSensors.remove(sensor.getAddress());
                if(mConnectedSensors.isEmpty()) {
                    mConnectionState = STATE_DISCONNECTED;
                    intentAction = ACTION_GATT_DISCONNECTED;
                    broadcastUpdate(intentAction);
                }
            }
        }
    };

    private void broadcastUpdate(final String action, String strValue ) {
        final Intent intent = new Intent(action);
        intent.putExtra(Intent.EXTRA_TEXT, strValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
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

    public void connect(final ArrayList<String> sensors) {
        if(sensors == null)
            return;
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized ");
            return;
        }

        if(mConnectionThread == null){
            mConnectionThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectionLoop(sensors);
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
            } catch (InterruptedException e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    public void disconnect(final ArrayList<Sensor> sensors) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized ");
            return;
        }

        if(mDisconnectionThread == null){
            mDisconnectionThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    disconnectionLoop(sensors);
                    mDisconnectionThread.interrupt();
                    mDisconnectionThread = null;
                }
            });

            mDisconnectionThread.start();
        }
    }

    private void disconnectionLoop(final ArrayList<Sensor> sensors){
        if(sensors == null)
            return;
        for (Sensor sensor:sensors){
            BluetoothGatt gatt = mConnectedSensors.get(sensor.getAddress());
            if(gatt != null){
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
