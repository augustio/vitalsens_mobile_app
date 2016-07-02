package vitalsens.vitalsensapp.fragments;


import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import vitalsens.vitalsensapp.utils.LineGraphView;

public class ChannelOneFragment extends Fragment {

    private int xRange;

    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private int xValueCounter;

    public ChannelOneFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        xValueCounter = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mLineGraph = new LineGraphView("Channel One");
        mGraphView = mLineGraph.getView(getActivity());

        return mGraphView;
    }

    public void updateGraph(int value) {
        if(mGraphView != null) {
            if(xValueCounter > xRange){
                xValueCounter = 0;
            }
            if(mLineGraph.getItemCount() > xValueCounter){
                mLineGraph.removeValue(xValueCounter);
            }
            mLineGraph.addValue(xValueCounter, xValueCounter, value);
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

    public void setxRange(int xRange){
        this.xRange = xRange;
    }

    public int getXRange(){
        return xRange;
    }

}

