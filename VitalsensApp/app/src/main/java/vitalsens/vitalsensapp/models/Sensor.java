package vitalsens.vitalsensapp.models;

import android.bluetooth.BluetoothGatt;

import com.google.gson.Gson;

/**
 * Created by augustio on 3.2.2016.
 */
public class Sensor {
    private String name;
    private String address;
    private String type;

    public Sensor(){
        name = "";
        address = "";
        type = "";
    }

    public Sensor(String name, String address){
        this.name = name;
        this.address = address;
        this.type = "";
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

    public String getType(){
        return type;
    }

    public void setType(String type){
        this.type = type;
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
        this.type = sensor.getType();
    }
}
