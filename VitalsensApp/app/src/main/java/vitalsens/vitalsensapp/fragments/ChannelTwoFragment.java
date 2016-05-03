package vitalsens.vitalsensapp.fragments;


import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import vitalsens.vitalsensapp.utils.LineGraphView;

public class ChannelTwoFragment extends Fragment {

    private static final int X_RANGE = 300;

    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private long xValueCounter;

    public ChannelTwoFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        xValueCounter = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mLineGraph = new LineGraphView("Channel Two");
        mGraphView = mLineGraph.getView(getActivity());

        return mGraphView;
    }

    public void updateGraph(int value) {
        if(mGraphView != null) {
            if(xValueCounter > X_RANGE){
                mLineGraph.removeValue(0);
                xValueCounter--;
            }
            mLineGraph.addValue(xValueCounter, value);
            xValueCounter++;
            mGraphView.repaint();
        }
    }

    public void clearGraph(){
        if(mGraphView != null) {
            mGraphView.repaint();
            mLineGraph.clearGraph();
            xValueCounter = 0;
        }
    }

}
