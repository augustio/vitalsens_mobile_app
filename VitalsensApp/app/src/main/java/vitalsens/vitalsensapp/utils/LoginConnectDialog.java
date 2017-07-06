package vitalsens.vitalsensapp.utils;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.models.Patient;

public class LoginConnectDialog extends Activity {

    private static final String PATIENT_DIRECTORY = "/VITALSENS_PATIENT";
    private static final String PATIENT_FILE = "patient_auth_connection_credentials.txt";

    public static final String ACTION_LOGIN = "vitalsens.vitalsensapp.ACTION_LOGIN";
    public static final String ACTION_GET_SENSOR_ID = "vitalsens.vitalsensapp.ACTION_GET_SENSOR_ID";
    private static final String SERVER_URL = "http://83.136.249.208:5000/auth/login";

    private Patient mPatient;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_dialog);

        final EditText sensorIdInputText = (EditText) findViewById(R.id.sensor_id);
        final EditText patientIdInputText = (EditText) findViewById(R.id.patient_id);
        final EditText passwordInputText = (EditText) findViewById(R.id.pword);
        Button okButton = (Button) findViewById(R.id.ok_btn);
        Button cancelButton = (Button) findViewById(R.id.cancel_btn);

        mPatient = getPatient();

        if(mPatient == null){
            mPatient = new Patient();
        }

        mHandler = new Handler();

        final String action = getIntent().getType();
        switch(action){
            case ACTION_GET_SENSOR_ID:
                sensorIdInputText.setVisibility(View.VISIBLE);
                patientIdInputText.setVisibility(View.GONE);
                passwordInputText.setVisibility(View.GONE);
                okButton.setText(R.string.popup_ok);
                sensorIdInputText.setText(mPatient.getSensorId());
                break;
            case ACTION_LOGIN:
                sensorIdInputText.setVisibility(View.GONE);
                patientIdInputText.setVisibility(View.VISIBLE);
                passwordInputText.setVisibility(View.VISIBLE);
                okButton.setText(R.string.login);
                patientIdInputText.setText(mPatient.getPatientId());
                break;
        }

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Intent resultIntent = new Intent();
                            String result = "";
                            if (action.equals(ACTION_LOGIN)) {
                                mPatient.setPatientId(patientIdInputText.getText().toString());
                                mPatient.setPassword(passwordInputText.getText().toString());
                                String user = Patient.toJson(mPatient);
                                String loginRes = IOOperations.POST(SERVER_URL, user, getApplicationContext(), null);
                                Log.e("LoginConnectDialog", loginRes);
                                if (loginRes.contains("token")) {
                                    JSONObject obj = new JSONObject(loginRes);
                                    String token = obj.getString("token");
                                    mPatient.setAuthKey(token);
                                    result = Patient.toJson(mPatient);
                                } else {
                                    showMessage(loginRes);
                                }
                            } else {
                                mPatient.setSensorId(sensorIdInputText.getText().toString());
                                result = mPatient.getSensorId();
                            }
                            Log.e("LoginConnectDialog", Patient.toJson(mPatient));
                            if (!result.isEmpty()) {
                                resultIntent.putExtra(Intent.EXTRA_TEXT, result);
                                setResult(Activity.RESULT_OK, resultIntent);
                                finish();
                            }
                        } catch (Exception e) {
                            Log.e("LoginConnectDialog", e.getLocalizedMessage());
                        }
                    }
                }.start();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }

    private Patient getPatient(){
        String pStr = IOOperations.readFileExternal(PATIENT_DIRECTORY, PATIENT_FILE);
        return Patient.fromJson(pStr);
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
