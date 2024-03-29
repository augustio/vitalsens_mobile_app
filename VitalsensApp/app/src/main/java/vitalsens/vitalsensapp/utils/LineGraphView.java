package vitalsens.vitalsensapp.utils;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.content.Context;
import android.graphics.Color;

public class LineGraphView {
    private TimeSeries mSeries;
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();
    private XYSeriesRenderer mRenderer = new XYSeriesRenderer();

    public LineGraphView(String title) {
        mSeries = new TimeSeries(title);
        mDataset.addSeries(mSeries);
        mRenderer.setColor(Color.WHITE);
        mRenderer.setLineWidth(3.0f);
        mRenderer.setFillPoints(true);

        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
        renderer.setBackgroundColor(Color.TRANSPARENT);
        renderer.setShowAxes(false);
        renderer.setShowLabels(false);
        renderer.setMargins(new int[]{50, 65, 40, 5});
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));

        renderer.setShowLegend(false);
        renderer.setInScroll(true);
        renderer.setPanEnabled(false, false);
        renderer.setZoomEnabled(false, false);
        renderer.addSeriesRenderer(mRenderer);
    }

    public GraphicalView getView(Context context) {
        return ChartFactory.getLineChartView(context, mDataset, mMultiRenderer);
    }

    public void addValue(int index, int x, double y){
        mSeries.add(index, x, y);
    }

    public int getValue(int index){
        if(mSeries.getItemCount() > 0){
            if(index < 0)
                index = 0;
            return (int)mSeries.getY(index);
        }
        else
            return 0;
    }

    public int getItemCount(){
        return mSeries.getItemCount();
    }

    public void removeValue(int index){
        mSeries.remove(index);
    }

    public void clearGraph() {
        mSeries.clear();
    }

    public void setColor(int color){
        mRenderer.setColor(color);
    }

}
