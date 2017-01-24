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

    private long timeStamp; //Timestamp that marks the beginning of a record
    private String patientId; //Patient Identification string
    private long start; //Timestamp that marks the beginning of a record segment
    private long end; //Timestamp that marks the end of a record segment
    private String type; //Type of recorded data
    private int pEStart; //Pain event start marker
    private int pEEnd; //Pain event end marker
    private String secret; //Static secret key for temporary authentication implementation
    private double temp; //Temperature value recorded at the end of record segment
    private ArrayList<Double> chOne; // Channel one data
    private ArrayList<Double> chTwo; //Channel two data
    private ArrayList<Double> chThree; //Channel three data

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
        temp = 0.0;
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

    public double getTemp(){
        return temp;
    }

    public int getPEStart(){
        return pEStart;
    }

    public int getPEEnd(){
        return pEEnd;
    }

    public ArrayList<Double> getChOne(){
        if(chOne.size() > 0)
            return new ArrayList<>(chOne);
        else
            return null;
    }

    public ArrayList<Double> getChTwo(){
        if(chTwo.size() > 0)
            return new ArrayList<>(chTwo);
        else
            return null;
    }

    public ArrayList<Double> getChThree(){
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

    public void setTemp(double temp){
        this.temp = temp;
    }

    public void addToChOne(Double data){
        chOne.add(data);
    }

    public void addToChTwo(Double data){
        chTwo.add(data);
    }

    public void addToChThree(Double data){
        chThree.add(data);
    }

    public boolean isEmpty(){
        return chOne.isEmpty();
    }

    public static String toJson(Record record){

        Gson gson = new Gson();
        return gson.toJson(record);
    }

    public static Record fromJson(String json){
        Gson gson = new Gson();
        return gson.fromJson(json, Record.class);
    }

    public Record copy(Record rec){
        if(rec != null) {
            this.chOne = rec.getChOne();
            this.chTwo = rec.getChTwo();
            this.chThree = rec.getChThree();
            this.start = rec.start;
            this.end = rec.end;
            this.patientId = rec.patientId;
            this.timeStamp = rec.timeStamp;
            this.pEEnd = rec.pEEnd;
            this.pEStart = rec.pEStart;
            this.secret = rec.secret;
            this.temp = rec.temp;

            return this;
        }else
            return null;
    }
}