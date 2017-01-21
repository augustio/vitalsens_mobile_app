package vitalsens.vitalsensapp.fragments;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import vitalsens.vitalsensapp.R;

public class RecordFragment extends Fragment {

    private TextView mRecordTimerView;

    public RecordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        RelativeLayout l = (RelativeLayout) inflater.inflate(R.layout.fragment_record, container, false);
        mRecordTimerView = (TextView) l.findViewById(R.id.timer);

        return l;
    }

    public void setRecordTimer(String timerStr){
        if(mRecordTimerView == null)
            return;
        mRecordTimerView.setText(timerStr);
    }

    public void clearRecordTimer(){
        if(mRecordTimerView == null)
            return;
        mRecordTimerView.setText("");
    }

}
