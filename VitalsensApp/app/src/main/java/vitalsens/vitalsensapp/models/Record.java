package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Record {

    /*Sensor datatypes*/
    public static final String ECG_DATA = "ECG";
    public static final String PPG_DATA = "PPG";
    public static final String ACCEL_DATA = "Acceleration";
    public static final String IMPEDANCE_DATA = "Impedance Pneumography";

    public static final Map<String, Integer> DATA_TYPES;
    static
    {
        DATA_TYPES = new HashMap<String, Integer>();
        DATA_TYPES.put(ECG_DATA, 1);
        DATA_TYPES.put(PPG_DATA, 3);
        DATA_TYPES.put(ACCEL_DATA, 4);
        DATA_TYPES.put(IMPEDANCE_DATA, 5);
    }

    private long timeStamp;
    private String patientId;
    private long start;
    private long end;
    private String type;
    private int pEStart;
    private int pEEnd;
    private String secret;
    private ArrayList<Integer> chOne;
    private ArrayList<Integer> chTwo;
    private ArrayList<Integer> chThree;

    public Record(){
    }

    public Record(long timeStamp, String patientId, long start, int type){
        this.timeStamp = timeStamp;
        this.patientId = patientId;
        this.start = start;
        end = 0;
        pEStart = -1;
        pEEnd = -1;
        secret = "";
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

    public long getTimeStamp(){
        return timeStamp;
    }

    public String getPatientId(){
        return patientId;
    }

    public String getType(){
        return type;
    }

    public long getStart(){
        return start;
    }

    public long getEnd(){
        return end;
    }

    public String getSecret(){
        return secret;
    }

    public int getPEStart(){
        return pEStart;
    }

    public int getPEEnd(){
        return pEEnd;
    }

    public ArrayList<Integer> getChOne(){
        if(chOne.size() > 0)
            return new ArrayList<>(chOne);
        else
            return null;
    }

    public ArrayList<Integer> getChTwo(){
        if(chTwo.size() > 0)
            return new ArrayList<>(chTwo);
        else
            return null;
    }

    public ArrayList<Integer> getChThree(){
        if(chThree.size() > 0)
            return new ArrayList<>(chThree);
        else
            return null;
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

    public void setStart(long start){
        this.start = start;
    }

    public void setEnd(long end){
        this.end = end;
    }

    public void setPEStart(int pEStart){
        this.pEStart = pEStart;
    }

    public void setPEEnd(int pEEnd){
        this.pEEnd = pEEnd;
    }

    public void setSecret(String secret){
        this.secret = secret;
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
        return (chOne == null);
    }

    public static String toJson(Record record){

        Gson gson = new Gson();
        return gson.toJson(record);
    }

    public static Record fromJson(String json){
        Gson gson = new Gson();
        return gson.fromJson(json, Record.class);
    }

    public Record copy(){
        Record rec = new Record();
        rec.chOne = this.getChOne();
        rec.chTwo = this.getChTwo();
        rec.chThree = this.getChThree();
        rec.start = this.start;
        rec.end = this.end;
        rec.patientId = this.patientId;
        rec.timeStamp = this.timeStamp;
        rec.pEEnd = this.pEEnd;
        rec.pEStart = this.pEStart;
        rec.secret = this.secret;

        return rec;
    }
}