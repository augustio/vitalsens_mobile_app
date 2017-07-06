package vitalsens.vitalsensapp.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.utils.IOOperations;

public class SaveRecordService extends IntentService {

    public static final String ACTION_SAVE_RECORD = "vitalsens.vitalsensapp.services.saverecordservice.action.SAVE_RECORD";
    public static final String ACTION_SAVE_RECORD_STATUS = "vitalsens.vitalsensapp.services.saverecordservice.action.SAVE_RECORD_STATUS";
    private static final String DIRECTORY_NAME = "/VITALSENSE_RECORDS";
    private static final String EXTRA_RECORD = "vitalsens.vitalsensapp.services.saverecordservice.extra.RECORD";

    public static final String EXTRA_STATUS = "vitalsens.vitalsensapp.services.saverecordservice.EXTRA_STATUS";

    public SaveRecordService() {
        super("SaveRecordService");
    }

    public static void startActionSaveRecord(Context context, Record record) {
        Intent intent = new Intent(context, SaveRecordService.class);
        intent.setAction(ACTION_SAVE_RECORD);
        intent.putExtra(EXTRA_RECORD, Record.toJson(record));
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SAVE_RECORD.equals(action)) {
                final Record record = Record.fromJson(intent.getStringExtra(EXTRA_RECORD));
                String status = handleActionSaveRecord(record);
                Intent localIntent = new Intent(ACTION_SAVE_RECORD_STATUS);
                localIntent.putExtra(EXTRA_STATUS, status);
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        }
    }

    private String handleActionSaveRecord(final Record record){
        String status;
        if(IOOperations.isExternalStorageWritable()){
            Date date = new Date(record.getStart());
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss", Locale.US);
            String dataType = record.getType();
            String fileName = dataType + "_" + sdf.format(date) + ".txt";
            if(!record.isEmpty()) {
                status = IOOperations.writeFileExternal(DIRECTORY_NAME, fileName, Record.toJson(record), false);
            }else{
                status = "Empty Record";
            }
        }
        else {
            status = "Cannot write to storage";
        }
        return status;
    }
}
