package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.models.Record;

public class History extends Activity {

    private static final String TAG = History.class.getSimpleName();

    private ArrayAdapter<String> mListAdapter;
    private String mDirName;

    private int mPosition;

    private static final String SERVER_ERROR = "No Response From Server!";
    private static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    private static final String CONNECTION_ERROR= "Server Not Reachable, Check Internet Connection";
    private static final String SERVER_URL = "http://52.18.112.240:3000/records";
    private TextView accessStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

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
            readDirectory(mDirName);
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
                if(hasNetworkConnection())
                    sendAttachment(info.position);
                else
                    showMessage(NO_NETWORK_CONNECTION);
                return true;
            case R.id.send_cloud:
                if(hasNetworkConnection()) {
                    mPosition = info.position;
                    new HttpAsyncTask().execute(SERVER_URL);
                }
                else
                    showMessage(NO_NETWORK_CONNECTION);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void readDirectory(String dirName){
        if(isExternalStorageReadable()){
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File (root.getAbsolutePath() + dirName);
            if(!dir.exists())
                showMessage("No Record found");
            else{
                for (File f : dir.listFiles()) {
                    if (f.isFile())
                        mListAdapter.add(f.getName()+"\n"+getFileSize(f.length()));
                }
            }
        }
        else {
            showMessage("Cannot read from storage");
        }
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private String getFileSize(double len){
        String size;
        if(len <1000){
            size = len+"B";
        }
        else if(len < 1e+6){
            size = String.format("%.2f", (len/1024))+"KB";
        }
        else if(len < 1e+9){
            size = String.format("%.2f", (len/1.049e+6))+"MB";
        }
        else{
            size = String.format("%.2f", (len/1.074e+9))+"GB";
        }
        return size;
    }

    private String getFilePath(int pos){
        String item = mListAdapter.getItem(pos);
        String fn = item.substring(0, item.indexOf('\n'));
        return (android.os.Environment.getExternalStorageDirectory()+
                mDirName+"/"+fn);
    }

    private Record getRecord(int pos){
        if(isExternalStorageReadable()) {
            File file;
            Record record = new Record();
            if ((file = validateFile(getFilePath(pos))) != null) {
                try {
                    BufferedReader buf = new BufferedReader(new FileReader(file));
                    record.fromJson(buf.readLine());
                    buf.close();
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    showMessage("Problem accessing mFile");
                }
            }
            return record;
        }else
            return null;
    }

    private void deleteRecord(AdapterView.AdapterContextMenuInfo info){
        File f = new File(getFilePath(info.position));
        if(f.delete()) {
            mListAdapter.remove(mListAdapter.getItem(info.position));
            showMessage("Record deleted");
        }else
            showMessage("Problem deleting record");
    }

    private File validateFile(String path){
        File f = null;
        if(path.endsWith(("txt"))){
            f = new File(path);
            if(f.length() <= Character.SIZE) {
                showMessage("Empty File");
                return null;
            }
        }
        else
            showMessage("Invalid File Format");
        return f;
    }

    public static String POST(String serverUrl, Record record){
        InputStream inputStream;
        String result;
        URL url;
        HttpURLConnection urlConnection;
        int responseCode;
        try {
            url = new URL(serverUrl);
            urlConnection = (HttpURLConnection)url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestMethod("POST");

            String json = record.toJson();

            OutputStreamWriter writer = new OutputStreamWriter(urlConnection.getOutputStream());
            writer.write(json);
            writer.flush();
            writer.close();

            responseCode = urlConnection.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK){
                inputStream = urlConnection.getInputStream();
                result = convertInputStreamToString(inputStream);
            }else
                result = SERVER_ERROR;

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
            result =  CONNECTION_ERROR;
        }

        return result;
    }

    private class HttpAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... urls) {
            Record record = getRecord(mPosition);
            publishProgress("Sending Data to Server ...");
            return POST(urls[0], record);
        }

        protected void onProgressUpdate(String... value) {
            accessStatus.setText(value[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            accessStatus.setText("");
            switch(result){
                case SERVER_ERROR:
                    showMessage("Error Connecting to Server");
                    break;
                case CONNECTION_ERROR:
                    showMessage(CONNECTION_ERROR);
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
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://"+getFilePath(pos)));

        try {
            startActivity(Intent.createChooser(emailIntent, "Sending Email...."));
        }catch (android.content.ActivityNotFoundException ex) {
            showMessage("No email clients installed.");
        }
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

    public boolean hasNetworkConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

