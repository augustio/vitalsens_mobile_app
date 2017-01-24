package vitalsens.vitalsensapp.fragments;


import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.achartengine.GraphicalView;

import java.util.ArrayList;

import vitalsens.vitalsensapp.R;
import vitalsens.vitalsensapp.services.BLEService;
import vitalsens.vitalsensapp.utils.LineGraphView;

public class GraphviewFragment extends Fragment {

    private static final String ARG_CHANNELS = "chanels";
    private static final String ARG_RANGE = "range";
    private static String[] CH_TYPES = {"One", "Two", "Three"};

    private int mXRange;
    private int mChannels;

    private ArrayList<LineGraphView> mLineGraph;
    private ArrayList<GraphicalView> mGraphView;

    private int xValueCounter;

    public GraphviewFragment() {
    }

    public static GraphviewFragment createInstance(int channels, int range) {
        GraphviewFragment fragment = new GraphviewFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CHANNELS, channels);
        args.putInt(ARG_RANGE, range);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() != null){
            mXRange = getArguments().getInt(ARG_RANGE);
            mChannels = getArguments().getInt(ARG_CHANNELS);
        }
        else {
            mChannels = 3;
            mXRange = 0;
        }

        xValueCounter = 0;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mLineGraph = new ArrayList<>(mChannels);
        mGraphView = new ArrayList<>(mChannels);

        LinearLayout graphLayout = (LinearLayout)inflater.inflate(R.layout.graphview_layout, container, false);


        for(int i = 0; i < mChannels; i++){
            LineGraphView lg = new LineGraphView("channel " + CH_TYPES[i]);
            mLineGraph.add(i, lg);
            mGraphView.add(i, lg.getView(getActivity()));
            LinearLayout l = (LinearLayout) graphLayout.getChildAt(i);
            l.addView(mGraphView.get(i));
        }

        return graphLayout;
    }

    public void updateGraph(double[] value) {
        if(mGraphView == null || mLineGraph == null)
            return;
        for(int i = 0; i < value.length && i < mGraphView.size(); i++){
            if(mGraphView.get(i) != null){
                if(xValueCounter > mXRange){
                    xValueCounter = 0;
                }
                if(mLineGraph.get(i).getItemCount() > xValueCounter){
                    mLineGraph.get(i).removeValue(xValueCounter);
                }
                if(Double.isNaN(value[i])) {
                    mLineGraph.get(i).addValue(xValueCounter, xValueCounter,
                            mLineGraph.get(i).getValue(xValueCounter - 1));
                }
                else {
                    mLineGraph.get(i).addValue(xValueCounter, xValueCounter, value[i]);
                }
                mGraphView.get(i).repaint();

            }
        }
        xValueCounter++;
    }

    public void clearGraph(){
        xValueCounter = 0;
        if(mGraphView == null || mLineGraph == null)
            return;
        for(int i = 0; i < mGraphView.size(); i++){
            if(mGraphView.get(i) != null) {
                mGraphView.get(i).repaint();
                mLineGraph.get(i).clearGraph();
            }
        }
    }

    public void setxRange(int xRange){
        this.mXRange = xRange;
    }

    public int getXRange(){
        return mXRange;
    }

    public void setColor(int color){
        if(mLineGraph == null)
            return;
        mLineGraph.get(0).setColor(color);
    }
}

