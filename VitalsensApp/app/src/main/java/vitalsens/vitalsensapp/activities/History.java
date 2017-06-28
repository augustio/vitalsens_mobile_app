package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.models.Record;
import vitalsens.vitalsensapp.utils.IOOperations;

public class History extends Activity {

    private static final String TAG = History.class.getSimpleName();
    private static final String SERVER_URL = "http://83.136.249.208:5000/api/record";

    private TextView accessStatus;
    private ArrayAdapter<String> mListAdapter;
    private String mDirName;
    private int mPosition;
    private Handler mHandler;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mHandler = new Handler();

        accessStatus = (TextView)findViewById(R.id.server_access_status);
        ListView mHistView = (ListView) findViewById(R.id.historyListView);
        mListAdapter = new ArrayAdapter<>(this, R.layout.message_detail);
        mHistView.setAdapter(mListAdapter);
        mHistView.setOnItemClickListener(mFileClickListener);
        registerForContextMenu(mHistView);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }else {
            mDirName = extras.getString(Intent.EXTRA_TEXT);
            ArrayList files = IOOperations.readDirectory(mDirName, true);
            if(files != null){
                for(Object obj : files){
                    mListAdapter.add((String)obj);
                }
            }
        }
    }

    private AdapterView.OnItemClickListener mFileClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_history, menu);
        menu.setHeaderTitle("Edit");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.delete:
                deleteRecord(info);
                return true;
            case R.id.send_email:
                if(IOOperations.hasNetworkConnection(getApplicationContext()))
                    sendAttachment(info.position);
                else
                    showMessage(IOOperations.NO_NETWORK_CONNECTION);
                return true;
            case R.id.send_cloud:
                if(IOOperations.hasNetworkConnection(getApplicationContext())) {
                    mPosition = info.position;
                    new HttpAsyncTask().execute(SERVER_URL);
                }
                else
                    showMessage(IOOperations.NO_NETWORK_CONNECTION);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private String getFilePath(int pos){
        String item = mListAdapter.getItem(pos);
        if(item == null)
            return null;
        String fn = item.substring(0, item.indexOf('\n'));
        return (android.os.Environment.getExternalStorageDirectory()+
                mDirName+"/"+fn);
    }

    private Record getRecord(int pos){
        String path = getFilePath(pos);
        if(path == null){
            showMessage("No record Available");
            return null;
        }
        if(!path.endsWith(("txt"))) {
            showMessage("Invalid file format");
            return null;
        }
        File file = new File(path);
        String recordStr = IOOperations.readFileExternal(file);
        if(recordStr == null){
            return null;
        }
        return Record.fromJson(recordStr);
    }

    private void deleteRecord(AdapterView.AdapterContextMenuInfo info){
        String filePath = getFilePath(info.position);
        if(filePath == null){
            showMessage("No record to delete");
            return;
        }
        File f = new File(filePath);
        if(f.delete()) {
            mListAdapter.remove(mListAdapter.getItem(info.position));
            showMessage("Record deleted");
        }else
            showMessage("Problem deleting record");
    }

    private class HttpAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... urls) {
            Record record = getRecord(mPosition);
            publishProgress("Sending Data to Server ...");
            return IOOperations.POST(urls[0], Record.toJson(record), getApplicationContext());
        }

        protected void onProgressUpdate(String... value) {
            accessStatus.setText(value[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            accessStatus.setText("");
            switch(result){
                case IOOperations.SERVER_ERROR:
                    showMessage("Error Connecting to Server");
                    break;
                case IOOperations.CONNECTION_ERROR:
                    showMessage(IOOperations.CONNECTION_ERROR);
                    break;
                default:
                    showMessage("Data Sent");
                    break;
            }
        }

    }

    private void sendAttachment(int pos){
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Vitalsens Record");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attached is a copy of Vitalsens record");
        emailIntent.setData(Uri.parse("mailto:electria.metropolia@gmail.com"));
        String filePath = getFilePath(pos);
        if(filePath == null){
            showMessage("No file to send");
            return;
        }
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://"+ filePath));

        try {
            startActivity(Intent.createChooser(emailIntent, "Sending Email...."));
        }catch (android.content.ActivityNotFoundException ex) {
            showMessage("No email clients installed.");
        }
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);
    }
}

