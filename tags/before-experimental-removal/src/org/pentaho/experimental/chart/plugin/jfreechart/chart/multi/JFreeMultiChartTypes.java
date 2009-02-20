package org.pentaho.experimental.chart.plugin.jfreechart.chart.multi;

/**
 * These are the different high level bar chart types based on the dataset and the chart type used to create them.
 * </p> 
 * Author: Ravi Hasija
 * Date: May 15, 2008
 * Time: 1:07:41 PM 
 */
public class JFreeMultiChartTypes {
  public static final String AREALINE="areaLine";  //$NON-NLS-1$
  public static final String BARLINE="barLine";  //$NON-NLS-1$
  public static final String AREABAR="areaBar";  //$NON-NLS-1$
  public static final String MULTI = "multi";  //$NON-NLS-1$

  /**
   * To avoid instantiation from outside world.
   */
  private JFreeMultiChartTypes() {
  }
}