package vitalsens.vitalsensapp.fragments;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import vitalsens.vitalsensapp.R;

public class MainFragment extends Fragment {

    public MainFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceStateView) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }
}
