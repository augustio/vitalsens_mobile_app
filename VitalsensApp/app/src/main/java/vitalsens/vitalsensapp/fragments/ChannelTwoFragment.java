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

public class ChannelTwoFragment extends Fragment {

    private static final int MIN_Y = 0;//Minimum ECG data value
    private static final int MAX_Y = 4095;//Maximum ECG data value
    private static final int X_RANGE = 200;

    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private Handler mHandler;

    private int xValueCounter;
    private boolean graphStarted;
    private ArrayList<Integer> mCollection;

    public ChannelTwoFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        xValueCounter = 0;
        mHandler = new Handler();
        graphStarted = false;
        mCollection = new ArrayList<>();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mLineGraph = new LineGraphView("Channel Two");
        mLineGraph.setXYTitle("    Time", "");
        mLineGraph.setYRange(MIN_Y, MAX_Y);
        mGraphView = mLineGraph.getView(getActivity());

        return mGraphView;
    }

    public void updateGraph(int value) {
        if(!graphStarted) {
            graphStarted = true;
            mStartGraph.run();
        }
        mCollection.add(value);
    }

    private final Runnable mStartGraph = new Runnable() {
        @Override
        public void run() {
            if(!mCollection.isEmpty()) {
                double maxX = xValueCounter;
                double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
                mLineGraph.setXRange(minX, maxX);
                mLineGraph.addValue(new Point(xValueCounter, mCollection.get(xValueCounter)));
                xValueCounter++;
                mGraphView.repaint();
            }
            mHandler.postDelayed(mStartGraph, 1);
        }
    };

    public void clearGraph(){
        if(mGraphView != null) {
            mGraphView.repaint();
            mLineGraph.clearGraph();
            graphStarted = false;
            mCollection.clear();
            mHandler.removeCallbacks(mStartGraph);
            xValueCounter = 0;
        }
    }

}
