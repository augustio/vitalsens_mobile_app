package vitalsens.vitalsensapp.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vitalsens.vitalsensapp.models.Record;

public class SaveRecordService extends IntentService {
    public static final String ACTION_SAVE_RECORD = "vitalsens.vitalsensapp.services.action.SAVE_RECORD";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final String EXTRA_RECORD = "vitalsens.vitalsensapp.services.extra.RECORD";

    public static final String SAVE_RECORD_STATUS = "vitalsens.vitalsensapp.services.SAVE_RECORD_STATUS";

    public SaveRecordService() {
        super("SaveRecordService");
    }

    public static void startActionSaveRecord(Context context, String record) {
        Intent intent = new Intent(context, SaveRecordService.class);
        intent.setAction(ACTION_SAVE_RECORD);
        intent.putExtra(EXTRA_RECORD, record);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SAVE_RECORD.equals(action)) {
                final Record record = Record.fromJson(intent.getStringExtra(EXTRA_RECORD));
                handleActionSaveRecord(record);
            }
        }
    }

    private void handleActionSaveRecord(final Record record){
        Intent localIntent = new Intent(ACTION_SAVE_RECORD);
        String status = "Saving record ...";
        if(isExternalStorageWritable()){
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
            if(!dir.isDirectory())
                dir.mkdirs();
            File file;
            Date date = new Date(record.getStart());
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss", Locale.US);
            String dataType = record.getType();
            String fileName = dataType + "_" + sdf.format(date) + ".txt";
            file = new File(dir, fileName);
            if(!record.isEmpty()) {
                try {
                    FileWriter fw = new FileWriter(file, true);
                    fw.append(Record.toJson(record));
                    fw.flush();
                    fw.close();
                    status = dataType + " record saved";
                } catch (IOException e) {
                    status = "Problem writing to Storage";
                }
            }else{
                status = "Empty Record";
            }
        }
        else {
            status = "Cannot write to storage";
        }
        localIntent.putExtra(SAVE_RECORD_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }
}
