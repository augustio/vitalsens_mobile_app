package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

public class Record {
    private String sensor;
    private String timeStamp;
    private String data;
    private int dataType;

    public Record(){
    }

    public Record(String sensor, String timeStamp, int dataType){
        this.sensor = sensor;
        this.timeStamp = timeStamp;
        data = "";
        this.dataType = dataType;
    }

    public String getSensor() {
        return sensor;
    }

    public void setSensor(String sensor) {
        this.sensor = sensor;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public String toJson(){

        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void fromJson(String json){
        Gson gson = new Gson();
        Record record = gson.fromJson(json, Record.class);
        this.sensor = record.getSensor();
        this.timeStamp = record.getTimeStamp();
        this.data = record.getData();
        this.dataType = record.getDataType();
    }
}