package vitalsens.vitalsensapp.models;

import android.bluetooth.BluetoothGatt;

import com.google.gson.Gson;

/**
 * Created by augustio on 3.2.2016.
 */
public class Sensor {
    private String name;
    private String address;

    public static final String ECG1 = "Vitalsens_ECG1";
    public static final String ECG3 = "Vitalsens_ECG3";
    public static final String PPG1 = "Vitalsens_PPG1";
    public static final String PPG2 = "Vitalsens_PPG2";
    public static final String ACCEL = "Vitalsens_ACCEL";
    public static final String IMPEDANCE = "Vitalsens_IMPED";

    public Sensor(){
        name = "";
        address = "";
    }

    public Sensor(String name, String address){
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void fromJson(String json){
        Gson gson = new Gson();
        Sensor sensor = gson.fromJson(json, Sensor.class);
        this.name = sensor.getName();
        this.address = sensor.getAddress();
    }
}
