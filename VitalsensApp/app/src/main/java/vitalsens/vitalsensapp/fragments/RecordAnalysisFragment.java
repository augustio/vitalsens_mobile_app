package vitalsens.vitalsensapp.fragments;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;


import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.models.AnalysisResult;

public class RecordAnalysisFragment extends Fragment {

    private TextView mPatientIdView;
    private TextView mDataTypeView;
    private TextView mRecordTimeView;
    private TextView mDurationView;
    private TextView mHeartRateView;
    private TextView mPVCCountView;
    private TextView mMinRPeakView;
    private TextView mMaxRPeakView;

    private AnalysisResult mAnalysisResult;

    private boolean mViewCreated;

    public RecordAnalysisFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAnalysisResult = new AnalysisResult();
        mViewCreated = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout l = (LinearLayout) inflater.
                inflate(R.layout.fragment_record_analysis, container, false);
        mPatientIdView = (TextView) l.findViewById(R.id.p_id);
        mPatientIdView.setText(mAnalysisResult.getpId());
        mDataTypeView = (TextView) l.findViewById(R.id.d_type);
        mDataTypeView.setText(mAnalysisResult.getdType());
        mRecordTimeView = (TextView) l.findViewById(R.id.r_time);
        mRecordTimeView.setText(mAnalysisResult.getRecordTime());
        mDurationView = (TextView) l.findViewById(R.id.duration);
        mDurationView.setText(String.valueOf(mAnalysisResult.getDuration()));
        mHeartRateView = (TextView) l.findViewById(R.id.heart_rate);
        mHeartRateView.setText(String.valueOf(mAnalysisResult.getHeartRate()));
        mPVCCountView = (TextView) l.findViewById(R.id.pvc_count);
        mPVCCountView.setText(String.valueOf(mAnalysisResult.getPvcCount()));
        mMinRPeakView = (TextView) l.findViewById(R.id.min_r_peak);
        mMinRPeakView.setText(String.valueOf(mAnalysisResult.getMinRPeak()));
        mMaxRPeakView = (TextView) l.findViewById(R.id.max_r_peak);
        mMaxRPeakView.setText(String.valueOf(mAnalysisResult.getMaxRPeak()));

        mViewCreated = true;

        return l;
    }

    public void updateView(String analysisStr){
        if(!mViewCreated)
            return;
        if(analysisStr != null){
            mAnalysisResult = AnalysisResult.fromJson(analysisStr);
            if(mAnalysisResult.getdType().isEmpty())
                return;
            mPatientIdView.setText(mAnalysisResult.getpId());
            mDataTypeView.setText(mAnalysisResult.getdType());
            mRecordTimeView.setText(mAnalysisResult.getRecordTime());
            mDurationView.setText(String.valueOf(mAnalysisResult.getDuration()));
            mHeartRateView.setText(String.valueOf(mAnalysisResult.getHeartRate()));
            mPVCCountView.setText(String.valueOf(mAnalysisResult.getPvcCount()));
            mMinRPeakView.setText(String.valueOf(mAnalysisResult.getMinRPeak()));
            mMaxRPeakView.setText(String.valueOf(mAnalysisResult.getMaxRPeak()));
        }
    }

    public void clearView(){
        if(!mViewCreated)
            return;
        mViewCreated = false;
        mPatientIdView.setText("");
        mDataTypeView.setText("");
        mRecordTimeView.setText("");
        mDurationView.setText("");
        mHeartRateView.setText("");
        mPVCCountView.setText("");
        mMinRPeakView.setText("");
        mMaxRPeakView.setText("");
    }
}
