package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

import java.util.Arrays;

public class DataPacket {

    private long packetNumber;
    private int dataId;
    private int[] data;

    public DataPacket(int[] dataPacketArray){
        if(dataPacketArray.length >= 3) {
            packetNumber = dataPacketArray[1];
            dataId = dataPacketArray[0];
            data = Arrays.copyOfRange(dataPacketArray, 2, dataPacketArray.length);
        }
        else{
            packetNumber = -1;
            dataId = -1;
            data = null;
        }
    }

    public int[] getData(){
        return data;
    }

    public long getPacketNumber(){
        return packetNumber;
    }

    public int getDataId(){
        return dataId;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}

