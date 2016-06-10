package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;


public class Sensor {
    private String name;
    private String address;

    /*Sensor types*/
    public static final String ECG1 = "Vitalsens_ECG1";
    public static final String ECG3 = "HRTZ1000";
    public static final String PPG1 = "Vitalsens_PPG1";
    public static final String PPG2 = "Vitalsens_PPG2";
    public static final String ACCEL = "Vitalsens_ACCEL";
    public static final String IMPEDANCE = "Vitalsens_IMPED";

    /*Sensor datatypes*/
    public static final String ECG_ONE_DATA = "Single Channel ECG";
    public static final String ECG_THREE_DATA = "Three Channels ECG";
    public static final String PPG_ONE_DATA = "Single Channel PPG";
    public static final String PPG_TWO_DATA = "Two_Channels_PPG";
    public static final String ACCEL_DATA = "Accelerometer";
    public static final String IMPEDANCE_DATA = "Impedance Pneumography";

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
