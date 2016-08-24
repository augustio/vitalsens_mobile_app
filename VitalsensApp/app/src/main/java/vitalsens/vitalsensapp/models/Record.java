package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

import java.util.ArrayList;

public class Record {
    private long sequenceId;
    private long timeStamp;
    private String patientId;
    private long duration;
    private String type;
    private ArrayList<Integer> chOne;
    private ArrayList<Integer> chTwo;
    private ArrayList<Integer> chThree;

    public Record(){
    }

    public Record(long sequenceId, long timeStamp, String patientId, int type){
        this.sequenceId = sequenceId;
        this.timeStamp = timeStamp;
        this.patientId = patientId;
        duration = 0;
        chOne = new ArrayList<>();
        chTwo = new ArrayList<>();
        chThree = new ArrayList<>();
        switch (type){
            case 0:
                this.type = Sensor.ECG_ONE_DATA;
                break;
            case 1:
                this.type = Sensor.ECG_THREE_DATA;
                break;
            case 2:
                this.type = Sensor.PPG_ONE_DATA;
                break;
            case 3:
                this.type = Sensor.PPG_TWO_DATA;
                break;
            case 4:
                this.type = Sensor.ACCEL_DATA;
                break;
            case 5:
                this.type = Sensor.IMPEDANCE_DATA;
                break;
        }
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public long getTimeStamp(){
        return timeStamp;
    }

    public String getPatientId(){
        return patientId;
    }

    public String getType(){
        return type;
    }

    public long getDuration(){
        return duration;
    }

    public ArrayList<Integer> getChOne(){
        if(chOne.size() > 0)
            return chOne;
        else
            return null;
    }

    public ArrayList<Integer> getChTwo(){
        if(chTwo.size() > 0)
            return chTwo;
        else
            return null;
    }

    public ArrayList<Integer> getChThree(){
        if(chThree.size() > 0)
            return chThree;
        else
            return null;
    }

    public void setSequenceId(long sequenceId) {
        this.sequenceId = sequenceId;
    }

    public void setTimeStamp(long timeStamp){
        this.timeStamp = timeStamp;
    }

    public void setPatientId(String patientId){
        this.patientId =  patientId;
    }

    public void setType(String type){
        this.type = type;
    }

    public void setDuration(long duration){
        this.duration = duration;
    }

    public void addToChOne(int data){
        chOne.add(data);
    }

    public void addToChTwo(int data){
        chTwo.add(data);
    }

    public void addToChThree(int data){
        chThree.add(data);
    }

    public boolean isEmpty(){
        return (getChOne() == null);
    }

    public String toJson(){

        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void fromJson(String json){
        Gson gson = new Gson();
        Record record = gson.fromJson(json, Record.class);
        this.sequenceId = record.getSequenceId();
        this.timeStamp = record.getTimeStamp();
        this.patientId = record.getPatientId();
        this.type = record.getType();
        duration = record.getDuration();
        chOne = record.getChOne();
        chTwo = record.getChTwo();
        chThree = record.getChThree();
    }
}