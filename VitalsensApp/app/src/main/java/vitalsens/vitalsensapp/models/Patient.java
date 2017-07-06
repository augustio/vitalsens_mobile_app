package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

/**
 * Created by augustineonubeze on 06/07/2017.
 */

public class Patient {
    private String userId;
    private String password;
    private String sensorId;
    private String authKey;

    public Patient(){
        userId = "";
        password = "";
        sensorId = "";
        authKey = "";
    }
    public Patient(String pId, String pwd, String sId, String aKey){
        userId = pId;
        password = pwd;
        sensorId = sId;
        authKey = aKey;
    }

    public void setPatientId(String pId){
        this.userId = pId;
    }

    public String getPatientId(){
        return userId;
    }

    public void setPassword(String pwd){
        this.password = pwd;
    }

    public String getPassword(){
        return password;
    }

    public void setSensorId(String sId){
        this.sensorId = sId;
    }

    public String getSensorId(){
        return sensorId;
    }

    public void setAuthKey(String aKey){
        this.authKey = aKey;
    }

    public String getAuthKey(){
        return authKey;
    }

    public static String toJson(Patient patient){
        if(patient == null){
            return null;
        }
        Gson gson = new Gson();
        return gson.toJson(patient);
    }

    public static Patient fromJson(String json){
        if(json == null || json.isEmpty()){
            return null;
        }
        Gson gson = new Gson();
        return gson.fromJson(json, Patient.class);
    }

    public Patient copy(Patient patient){
        if(patient == null){
            return null;
        }
        this.userId = patient.userId;
        this.password = patient.password;
        this.sensorId = patient.sensorId;
        this.authKey = patient.authKey;

        return this;
    }
}
