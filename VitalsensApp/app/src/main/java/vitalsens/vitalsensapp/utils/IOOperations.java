package vitalsens.vitalsensapp.utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;


/**
 * Created by augustineonubeze on 26/06/2017.
 */

public class IOOperations {

    private static final String TAG = "IOOperations";

    public static final String SERVER_ERROR = "No Response From Server!";
    public static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    public static final String CONNECTION_ERROR = "NO Internet/Server Not Found";
    public static final String DATA_SENT = "Record upload successful";
    public static final String DATA_SAVED = "Data Saved";

    private final static int URL_CONNECTION_TIMEOUT = 10000;


    private static final int READ = 0;
    private static final int WRITE = 1;

    public static boolean hasNetworkConnection(Context c){
        ConnectivityManager connMgr = (ConnectivityManager) c.getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    public static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private static File getFile(String dirName, String fileName, int mode) {
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File (root.getAbsolutePath() + dirName);
        if(mode == WRITE) {
            if(!dir.isDirectory())
                Log.d(TAG, "New Directory Created: "+dir.mkdirs());
        }
        return new File(dir, fileName);
    }

    public static String writeFileExternal(String dirName, String fileName, String data, boolean append){
        String status = DATA_SAVED;
        File file = getFile(dirName, fileName, WRITE);
        try {
            FileWriter fw = new FileWriter(file, append);
            fw.append(data);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            status = "Problem writing to Storage";
        }

        return status;
    }

    private static String getFileSizeStr(double len){
        String size;
        if(len <1000){
            size = len+"B";
        }
        else if(len < 1e+6){
            size = String.format(Locale.getDefault(),"%.2f", (len/1024))+"KB";
        }
        else if(len < 1e+9){
            size = String.format(Locale.getDefault(),"%.2f", (len/1.049e+6))+"MB";
        }
        else{
            size = String.format(Locale.getDefault(),"%.2f", (len/1.074e+9))+"GB";
        }
        return size;
    }

    private static boolean isEmptyFile(File f){
        return (f.length() <= Character.SIZE);
    }

    public static ArrayList readDirectory(String dirName, boolean fmt){
        if(!isExternalStorageWritable()){
            return null;
        }
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File (root.getAbsolutePath() + dirName);
        if(!dir.exists()){
            return null;
        }
        if(fmt){
            ArrayList<String> files = new ArrayList<>();
            for (File f : dir.listFiles()) {
                if (f.isFile()){
                    files.add(f.getName()+"\n"+getFileSizeStr(f.length()));
                }
            }
            return files;
        }else{
            ArrayList<File> files = new ArrayList<>();
            for (File f : dir.listFiles()) {
                if (f.isFile()){
                    files.add(f);
                }
            }
            return files;
        }
    }

    public static File getOldestFile(ArrayList files){
        if(files == null || files.size() < 1){
            return null;
        }
        File oldest = (File)files.get(0);

        for(int i = 0; i < files.size(); i++){
            File file = (File)files.get(i);
            if(file.lastModified() < oldest.lastModified()){
                oldest = file;
            }
        }
        return oldest;
    }

    public static String readFileExternal(String dirName, String fileName){
        String result = null;
        if(isExternalStorageReadable()) {
            File file = getFile(dirName, fileName, READ);
            if (!isEmptyFile(file)) {
                try {
                    BufferedReader buf = new BufferedReader(new FileReader(file));
                    result = buf.readLine();
                    buf.close();
                } catch (Exception e) {
                    Log.e("IOOPerations", e.toString());
                    result = null;
                }
            } else {
                Log.e("IOOperations", "Empty file");
                result = null;
            }
        }
        return result;
    }

    public static String readFileExternal(File file){
        String result = null;
        if(isExternalStorageReadable()) {
            if (!isEmptyFile(file)) {
                try {
                    BufferedReader buf = new BufferedReader(new FileReader(file));
                    result = buf.readLine();
                    buf.close();
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    result = null;
                }
            } else {
                Log.e(TAG, "Empty file");
                result = null;
            }
        }
        return result;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;
    }

    public static String POST(String serverUrl, String record, Context c, String auth){
        InputStream inputStream;
        String result;
        if(hasNetworkConnection(c)){
            URL url;
            HttpURLConnection urlConnection;
            int responseCode;
            try {
                url = new URL(serverUrl);
                urlConnection = (HttpURLConnection)url.openConnection();
                if(auth != null){
                    urlConnection.setRequestProperty("Authorization", auth);
                }
                urlConnection.setConnectTimeout(URL_CONNECTION_TIMEOUT);
                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestMethod("POST");

                OutputStreamWriter writer = new OutputStreamWriter(urlConnection.getOutputStream());
                writer.write(record);
                writer.flush();
                writer.close();

                responseCode = urlConnection.getResponseCode();
                if(responseCode == HttpURLConnection.HTTP_OK){
                    inputStream = urlConnection.getInputStream();
                    result = convertInputStreamToString(inputStream);
                    Log.d(TAG, "Cloud Access Response: "+ result);
                }else
                    result = SERVER_ERROR;

            } catch (Exception e) {
                Log.d(TAG, "exception: "+ e.getLocalizedMessage());
                result =  CONNECTION_ERROR;
            }
        }else{
            result = NO_NETWORK_CONNECTION;
        }
        return result;
    }
}
