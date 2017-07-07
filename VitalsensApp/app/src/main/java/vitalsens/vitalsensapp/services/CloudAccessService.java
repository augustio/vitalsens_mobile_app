package vitalsens.vitalsensapp.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;

import vitalsens.vitalsensapp.utils.IOOperations;

public class CloudAccessService extends IntentService {

    private static final String ACTION_CLOUD_ACCESS = "vitalsens.vitalsensapp.services.cloudaccessservice.action.CLOUD_ACCESS";
    public static final String ACTION_CLOUD_ACCESS_RESULT = "vitalsens.vitalsensapp.services.cloudaccessservice.action.CLOUD_ACCESS_RESULT";
    public static final String EXTRA_RESULT = "vitalsens.vitalsensapp.services.cloudaccessservice.extra.RESULT";
    private static final String SERVER_URL = "http://83.136.249.208:5000/api/records";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final String TAG = "CloudAccessService";


    public CloudAccessService() {
        super("CloudAccessService");
    }

    public static void startActionCloudAccess(Context context, String authKey) {
        Intent intent = new Intent(context, CloudAccessService.class);
        intent.putExtra("Authentication", "Basic " + authKey);
        intent.setAction(ACTION_CLOUD_ACCESS);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            final String auth = intent.getStringExtra("Authentication");
            if (ACTION_CLOUD_ACCESS.equals(action)) {
                String result = "Records Directory Empty";
                File oldest = null;
                if(IOOperations.isExternalStorageReadable()){
                    oldest = IOOperations.getOldestFile(IOOperations.readDirectory(DIRECTORY_NAME, false));
                }
                if(oldest != null) {
                    final String record = IOOperations.readFileExternal(oldest);
                    if(record != null){
                        result = IOOperations.POST(SERVER_URL, record, getApplicationContext(), auth);
                        Log.d(TAG, "Cloud-bound File: "+ oldest.getName()+"_"+oldest.lastModified());
                        try{
                            JSONObject obj = new JSONObject(result);
                            String msg  = obj.getString("message");
                            if(msg.equals(IOOperations.DATA_SENT)){
                                Log.d(TAG, "File deleted: "+ oldest.delete());
                            }
                        }catch (Exception e){
                            Log.d(TAG, "JSON Parser Error: "+ e.getLocalizedMessage());
                        }
                    }
                }
                Intent localIntent = new Intent(ACTION_CLOUD_ACCESS_RESULT);
                localIntent.putExtra(EXTRA_RESULT, result);
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        }
    }
}
