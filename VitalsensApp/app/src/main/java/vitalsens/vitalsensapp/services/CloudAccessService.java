package vitalsens.vitalsensapp.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import vitalsens.vitalsensapp.models.Record;

public class CloudAccessService extends IntentService {

    private static final String ACTION_CLOUD_ACCESS = "vitalsens.vitalsensapp.services.cloudaccessservice.action.CLOUD_ACCESS";
    public static final String ACTION_CLOUD_ACCESS_RESULT = "vitalsens.vitalsensapp.services.cloudaccessservice.action.CLOUD_ACCESS_RESULT";

    private static final String EXTRA_RECORD = "vitalsens.vitalsensapp.services.cloudaccessservice.extra.RECORD";
    public static final String EXTRA_RESULT = "vitalsens.vitalsensapp.services.cloudaccessservice.extra.RESULT";

    public static final String SERVER_ERROR = "No Response From Server!";
    public static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    public static final String CONNECTION_ERROR = "NO Internet/Server Not Found";
    public static final String DATA_SENT = "Record sent to cloud";
    private static final String SERVER_URL = "http://83.136.249.208:5000/api/records";

    private final static int URL_CONNECTION_TIMEOUT = 10000;

    public CloudAccessService() {
        super("CloudAccessService");
    }

    public static void startActionCloudAccess(Context context, Record record) {
        Intent intent = new Intent(context, CloudAccessService.class);
        intent.setAction(ACTION_CLOUD_ACCESS);
        intent.putExtra(EXTRA_RECORD, Record.toJson(record));
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CLOUD_ACCESS.equals(action)) {
                final String record = intent.getStringExtra(EXTRA_RECORD);
                String result = sendRecordToCloud(record);
                Intent localIntent = new Intent(ACTION_CLOUD_ACCESS_RESULT);
                localIntent.putExtra(EXTRA_RESULT, result);
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        }
    }

    public String sendRecordToCloud(String record){
        if(!hasNetworkConnection())
            return NO_NETWORK_CONNECTION;
        String result;
        InputStream inputStream;
        URL url;
        HttpURLConnection urlConnection = null;
        int responseCode = HttpURLConnection.HTTP_NOT_FOUND;
        try {
            url = new URL(SERVER_URL);
            urlConnection = (HttpURLConnection)url.openConnection();
            if(urlConnection != null) {
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
            }
            if(responseCode == HttpURLConnection.HTTP_OK){
                inputStream = urlConnection.getInputStream();
                result = convertInputStreamToString(inputStream);
            }else {
                result = SERVER_ERROR;
            }

        } catch (Exception e) {
            Log.d("OutputStream", " " + e.getLocalizedMessage());
            result = CONNECTION_ERROR;
        }finally{
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public boolean hasNetworkConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();

        try {
            JSONObject obj = new JSONObject(result);
            result = obj.getJSONObject("data").toString();
        }catch (JSONException e){
            Log.e("CloudAccessService", e.getLocalizedMessage());
        }

        return result;
    }
}
