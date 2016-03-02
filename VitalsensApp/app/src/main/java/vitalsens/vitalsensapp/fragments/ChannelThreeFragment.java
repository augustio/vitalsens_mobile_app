package vitalsens.vitalsensapp.fragments;


import android.app.Fragment;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import java.util.ArrayList;

import vitalsens.vitalsensapp.utils.LineGraphView;

public class ChannelThreeFragment extends Fragment {

    private static final int MIN_Y = 0;//Minimum ECG data value
    private static final int MAX_Y = 4095;//Maximum ECG data value
    private static final int X_RANGE = 500;

    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private int xValueCounter;

    public ChannelThreeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        xValueCounter = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mLineGraph = new LineGraphView("Channel Three");
        mLineGraph.setXYTitle("    Time", "");
        mLineGraph.setYRange(MIN_Y, MAX_Y);
        mGraphView = mLineGraph.getView(getActivity());

        return mGraphView;
    }

    public void updateGraph(int value) {
        if(mGraphView != null) {
            double maxX = xValueCounter;
            double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
            mLineGraph.setXRange(minX, maxX);
            mLineGraph.addValue(new Point(xValueCounter, value));
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