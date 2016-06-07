package vitalsens.vitalsensapp.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import vitalsens.vitalsensapp.R;

/**
 * Vitalsens Application splash activity
 */
public class SplashActivity extends Activity {

    private int delay;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        int delay = 2000;
        new Handler().postDelayed(new Runnable() {
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, delay);

    }
}
