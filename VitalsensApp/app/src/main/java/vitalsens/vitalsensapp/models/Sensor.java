package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;


public class Sensor {
    private String name;
    private String address;

    /*Sensor datatypes*/
    public static final String ECG_ONE_DATA = "ECG";
    public static final String ECG_THREE_DATA = "ECG";
    public static final String PPG_ONE_DATA = "PPG";
    public static final String PPG_TWO_DATA = "PPG";
    public static final String ACCEL_DATA = "ACC";
    public static final String IMPEDANCE_DATA = "IMP";

    public static final Map<String, Integer> DATA_TYPES;
    static
    {
        DATA_TYPES = new HashMap<String, Integer>();
        DATA_TYPES.put(ECG_ONE_DATA, 0);
        DATA_TYPES.put(ECG_THREE_DATA, 1);
        DATA_TYPES.put(PPG_ONE_DATA, 2);
        DATA_TYPES.put(PPG_TWO_DATA, 3);
        DATA_TYPES.put(ACCEL_DATA, 4);
        DATA_TYPES.put(IMPEDANCE_DATA, 5);
    }

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
