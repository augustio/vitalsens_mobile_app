package vitalsens.vitalsensapp.utils;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.graphics.Point;

/**
 * This class uses external library AChartEngine to show dynamic real time line graph for ECG values
 */
public class LineGraphView {
    //TimeSeries will hold the data in x,y format for single chart
    private TimeSeries mSeries;
    //XYSeriesRenderer is used to set the properties like chart color, style of each point, etc. of single chart
    private XYSeriesRenderer mRenderer = new XYSeriesRenderer();
    //XYMultipleSeriesDataset will contain all the TimeSeries
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    //XYMultipleSeriesRenderer will contain all XYSeriesRenderer and it can be used to set the properties of whole Graph
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();

    /**
     * This constructor will set some properties of single chart and some properties of whole graph
     */
    public LineGraphView(String title) {
        mSeries = new TimeSeries(title);
        //add single line chart mSeries
        mDataset.addSeries(mSeries);
        //set line chart color to blue
        mRenderer.setColor(Color.BLUE);
        //set line chart style to square points
        //mRenderer.setPointStyle(PointStyle.SQUARE);
        mRenderer.setFillPoints(true);

        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
        //set whole graph background color to transparent color
        renderer.setBackgroundColor(Color.TRANSPARENT);
        renderer.setMargins(new int[] { 50, 65, 40, 5 }); // top, left, bottom, right
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
        renderer.setAxesColor(Color.BLACK);
        renderer.setAxisTitleTextSize(24);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.LTGRAY);
        renderer.setLabelsColor(Color.BLACK);
        renderer.setYLabelsColor(0, Color.DKGRAY);
        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setYLabelsPadding(4.0f);
        renderer.setXLabelsColor(Color.DKGRAY);
        renderer.setLabelsTextSize(20);
        renderer.setLegendTextSize(20);
        renderer.setInScroll(true);
        renderer.setPanEnabled(true, true);
        renderer.setZoomEnabled(false, false);
        //set title to x-axis and y-axis
        renderer.setXTitle("    Time (10mS)");
        renderer.setYTitle("    Voltage (mV)");
        renderer.addSeriesRenderer(mRenderer);
    }

    /**
     * return graph view to activity
     */
    public GraphicalView getView(Context context) {
        return ChartFactory.getLineChartView(context, mDataset, mMultiRenderer);
    }

    /**
     * add new x,y value to chart
     */
    public void addValue(Point p) {
        mSeries.add(p.x, p.y);
    }

    /**
     * clear all previous values of chart
     */
    public void clearGraph() {
        mSeries.clear();
    }

    public void setXRange(double minX, double maxX){
        mMultiRenderer.setXAxisMin(minX);
        mMultiRenderer.setXAxisMax(maxX);
    }

    public void setYRange(double minY, double maxY){
        mMultiRenderer.setXAxisMin(minY);
        mMultiRenderer.setXAxisMax(maxY);
    }

    public void setPanLimits(double minX, double maxX, double minY, double maxY){
        mMultiRenderer.setPanLimits(new double[]{minX, maxX, minY, maxY});
    }

    public void enableZoom(){
        mMultiRenderer.setZoomEnabled(true, true);
    }

    public void setXYTitle(String x, String y){
        mMultiRenderer.setXTitle(x);
        mMultiRenderer.setYTitle(y);
    }

}
