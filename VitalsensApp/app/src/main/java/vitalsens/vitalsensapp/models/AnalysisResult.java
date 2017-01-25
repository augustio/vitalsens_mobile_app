package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;


public class AnalysisResult {

    private String pId;
    private String dType;
    private String recordTime;
    private int duration;
    private int heartRate;
    private int pvcCount;

    public AnalysisResult(){
        this.pId = "";
        this.dType = "";
        this.recordTime = "";
        this.duration = 0;
        this.heartRate = 0;
        this.pvcCount = 0;
    }

    public AnalysisResult(String pId, String dType, String recordTime, int duration,
                          int heartRate, int pvcCount) {
        this.pId = pId;
        this.dType = dType;
        this.recordTime = recordTime;
        this.duration = duration;
        this.heartRate = heartRate;
        this.pvcCount = pvcCount;
    }

    public String getpId() {
        return pId;
    }

    public void setpId(String pId) {
        this.pId = pId;
    }

    public String getdType() {
        return dType;
    }

    public void setdType(String dType) {
        this.dType = dType;
    }

    public String getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(String recordTime) {
        this.recordTime = recordTime;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public int getPvcCount() {
        return pvcCount;
    }

    public void setPvcCount(int pvcCount) {
        this.pvcCount = pvcCount;
    }

    public static AnalysisResult fromJson(String json){
        Gson gson = new Gson();
        return gson.fromJson(json, AnalysisResult.class);
    }

    public static String toJson(AnalysisResult analysis){
        Gson gson = new Gson();
        return gson.toJson(analysis);
    }
}
