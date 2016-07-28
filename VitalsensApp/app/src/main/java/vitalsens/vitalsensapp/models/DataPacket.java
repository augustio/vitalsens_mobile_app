package vitalsens.vitalsensapp.models;

import com.google.gson.Gson;

public class DataPacket {

    private int dataId;
    private String chOne;
    private String chTwo;
    private String chThree;

    public DataPacket(int[] dataPacketArray){
        if(dataPacketArray.length >= 3) {
            dataId = dataPacketArray[0];
            int len = dataPacketArray.length;
            chOne = "";
            chTwo = "";
            chThree = "";
            switch (dataId) {
                case 0:
                case 2:
                case 5:
                    for (int i = 1; i < len; i++) {
                        if (!chOne.equals("")) {
                            chOne += ",";
                        }
                        chOne += dataPacketArray[i];
                    }
                    break;
                case 3:
                    for (int i = 1; i < len; i += 2) {
                        if (!chOne.equals("")) {
                            chOne += ",";
                            chTwo += ",";
                        }
                        chOne += dataPacketArray[i];
                        chTwo += dataPacketArray[i + 1];
                    }
                    break;
                case 1:
                case 4:
                    for (int i = 1; i < len; i += 3) {
                        if (!chOne.equals("")) {
                            chOne += ",";
                            chTwo += ",";
                            chThree += ",";
                        }
                        chOne += dataPacketArray[i];
                        chTwo += dataPacketArray[i + 1];
                        chThree += dataPacketArray[i + 2];
                    }
                    break;
                default:
            }
        }
    }

    public String getChOne(){
        return chOne;
    }

    public String getChTwo(){
        return chTwo;
    }

    public String getChThree(){
        return chThree;
    }

    public int getDataId(){
        return dataId;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}

