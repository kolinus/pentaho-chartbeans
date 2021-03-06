/*!
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
* Foundation.
*
* You should have received a copy of the GNU Lesser General Public License along with this
* program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
* or from the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU Lesser General Public License for more details.
*
* Copyright (c) 2002-2013 Pentaho Corporation..  All rights reserved.
*/

package org.pentaho.chart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.pentaho.chart.data.BasicDataModel;
import org.pentaho.chart.data.IChartDataModel;
import org.pentaho.chart.data.IScalableDataModel;
import org.pentaho.chart.data.MultiSeriesDataModel;
import org.pentaho.chart.data.MultiSeriesXYDataModel;
import org.pentaho.chart.data.NamedValue;
import org.pentaho.chart.data.NamedValuesDataModel;
import org.pentaho.chart.data.XYDataModel;
import org.pentaho.chart.data.XYDataPoint;
import org.pentaho.chart.data.MultiSeriesDataModel.DomainData;
import org.pentaho.chart.data.MultiSeriesXYDataModel.Series;
import org.pentaho.chart.model.ChartModel;
import org.pentaho.chart.model.DialPlot;
import org.pentaho.chart.model.PiePlot;
import org.pentaho.chart.model.Plot;
import org.pentaho.chart.model.ScatterPlot;
import org.pentaho.chart.plugin.ChartDataOverflowException;
import org.pentaho.chart.plugin.ChartProcessingException;
import org.pentaho.chart.plugin.IChartPlugin;
import org.pentaho.chart.plugin.NoChartDataException;
import org.pentaho.chart.plugin.api.IOutput;
import org.pentaho.chart.plugin.api.PersistenceException;
import org.pentaho.chart.plugin.api.IOutput.OutputTypes;
import org.pentaho.chart.plugin.jfreechart.JFreeChartPlugin;
import org.pentaho.chart.plugin.openflashchart.OpenFlashChartPlugin;
import org.pentaho.reporting.libraries.resourceloader.ResourceKeyCreationException;

public class ChartBeanFactory {
  private static int MAX_ALLOWED_DATA_POINTS = 100;
  private static List<IChartPlugin> chartPlugins = new ArrayList<IChartPlugin>();

  private ChartBeanFactory() {
  }

  public static IChartPlugin getPlugin(String pluginId) {
    IChartPlugin plugin = null;
    for (IChartPlugin tmpPlugin : chartPlugins) {
      if (tmpPlugin.getPluginId().equals(pluginId)) {
        plugin = tmpPlugin;
      }
    }
    if ((plugin == null) && (chartPlugins.size() == 0)) {
      if (JFreeChartPlugin.PLUGIN_ID.equals(pluginId)) {
        plugin = new JFreeChartPlugin();
      } else if (OpenFlashChartPlugin.PLUGIN_ID.equals(pluginId)){
        plugin = new OpenFlashChartPlugin();
      }
    }
    return plugin;
  }

  public static int getMaxDataPointsPerChart() {
    return MAX_ALLOWED_DATA_POINTS;
  }
  
  public static void setMaxDataPointsPerChart(int max) {
    if (max > 0) {
      MAX_ALLOWED_DATA_POINTS = max;
    }
  }
  
  /**
   *  This method is called from a platform system listener on startup,
   *  to initialize the available plugins from the chartbeans configuration file. 
   */
  public static void loadDefaultChartPlugins(List <IChartPlugin> plugins) {
    chartPlugins = plugins;
  }
  
  public static IChartDataModel createChartDataModel(Object[][] queryResults, Number scalingFactor, boolean convertNullsToZero, int rangeColumnIndex,
      int seriesColumnIdx, int domainColumnIdx, ChartModel chartModel) throws ChartDataOverflowException, NoChartDataException{ 
    IChartDataModel chartDataModel = null;
    int numberOfDataPoints = 0;
    
    Plot plot = chartModel.getPlot();
    if ((plot instanceof PiePlot) && (seriesColumnIdx >= 0) && (rangeColumnIndex >= 0)) {
      NamedValuesDataModel namedValueDataModel = createNamedValueDataModel(queryResults, seriesColumnIdx, rangeColumnIndex, convertNullsToZero, true);
      numberOfDataPoints = namedValueDataModel.size();
      chartDataModel = namedValueDataModel;
    } else if ((plot instanceof DialPlot) && (rangeColumnIndex >= 0)) {
      BasicDataModel basicDataModel = createBasicDataModel(queryResults, rangeColumnIndex, true, true);
      numberOfDataPoints = basicDataModel.getData().size();
      chartDataModel = basicDataModel;
    } else if (plot instanceof ScatterPlot) {
      if ((seriesColumnIdx >= 0) && (domainColumnIdx >= 0)) {
        MultiSeriesXYDataModel multiSeriesXYDataModel = createMultiSeriesXYDataModel(queryResults, seriesColumnIdx,  domainColumnIdx,rangeColumnIndex, convertNullsToZero);
        for (Series series : multiSeriesXYDataModel.getSeries()) {
          numberOfDataPoints += series.size();
        }
        chartDataModel = multiSeriesXYDataModel;
      } else if (domainColumnIdx >= 0) {
        XYDataModel xyDataModel = createXYDataModel(queryResults, domainColumnIdx, rangeColumnIndex, convertNullsToZero);
        numberOfDataPoints = xyDataModel.size();
        chartDataModel = xyDataModel;
      }
    } else {
      if ((seriesColumnIdx >= 0) ){
        MultiSeriesDataModel multiSeriesDataModel = createMultiSeriesDataModel(queryResults, seriesColumnIdx,  domainColumnIdx, rangeColumnIndex, convertNullsToZero);
        List<DomainData> domainData = multiSeriesDataModel.getDomainData();
        if (domainData.size() > 0) {
          for (DomainData domain : domainData) {
            numberOfDataPoints += domain.size();
          }
        }
        chartDataModel = multiSeriesDataModel;
      } else {
        NamedValuesDataModel namedValueDataModel = createNamedValueDataModel(queryResults, domainColumnIdx, rangeColumnIndex, convertNullsToZero, true);
        numberOfDataPoints = namedValueDataModel.size();
        chartDataModel = namedValueDataModel;
      }
    }
    if (numberOfDataPoints == 0) {
      throw new NoChartDataException();
    } else if (numberOfDataPoints > MAX_ALLOWED_DATA_POINTS) {
      throw new ChartDataOverflowException(numberOfDataPoints, MAX_ALLOWED_DATA_POINTS);
    } else {
      if (chartDataModel instanceof IScalableDataModel) {
        ((IScalableDataModel)chartDataModel).setScalingFactor(scalingFactor);
      }
    }
    return chartDataModel;
  }
  
  public static IOutput createChart(ChartModel chartModel, IChartDataModel chartDataModel, IChartLinkGenerator contentLinkGenerator) throws ChartProcessingException {
    IChartPlugin chartPlugin = getPlugin(chartModel.getChartEngineId());
    IOutput output = null;
    if (chartPlugin != null) {
      output = chartPlugin.renderChartDocument(chartModel, chartDataModel, contentLinkGenerator);
    } else {
      throw new ChartProcessingException("Unknown chart engine.");
    }
    return output;
  }
  
  public static InputStream createChart(Object[][] queryResults, Number scalingFactor, boolean convertNullsToZero, int rangeColumnIndex,
      int seriesColumnIdx, int domainColumnIdx, ChartModel chartModel, IChartLinkGenerator contentLinkGenerator, int width, int height, OutputTypes outputType)
      throws NoChartDataException, ChartDataOverflowException, ChartProcessingException, PersistenceException {

    IChartDataModel chartDataModel = createChartDataModel(queryResults, scalingFactor, convertNullsToZero, rangeColumnIndex, seriesColumnIdx, domainColumnIdx, chartModel);
    IOutput output = createChart(chartModel, chartDataModel, contentLinkGenerator);
       
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    output.persistChart(outputStream, outputType, width, height);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    
    return inputStream;
  }

  private static MultiSeriesDataModel createMultiSeriesDataModel(Object[][] queryResults, int seriesColumn,  int domainColumn,int rangeColumn, boolean convertNullValuesToZero) {
    MultiSeriesDataModel multiSeriesDataModel = new MultiSeriesDataModel();
    
    for (int i = 0; i < queryResults.length; i++) {
      String domainValue = domainColumn >= 0 && queryResults[i][domainColumn] != null ? queryResults[i][domainColumn].toString() : "";
      Object seriesValue = queryResults[i][seriesColumn] != null ? queryResults[i][seriesColumn].toString() : "null";
      Object rangeValue = queryResults[i][rangeColumn];
      if (rangeValue == null) {
        if (convertNullValuesToZero) {
          rangeValue = new Integer(0);
        }
      } else if (!(rangeValue instanceof Number)) {
        rangeValue = null;
      }
      
      multiSeriesDataModel.addValue(domainValue, seriesValue.toString(), (Number)rangeValue);
    }
    
    return multiSeriesDataModel;
  }
  
  private static MultiSeriesXYDataModel createMultiSeriesXYDataModel(Object[][] queryResults, int seriesColumn,  int domainColumn,int rangeColumn, boolean convertNullValuesToZero) {
    MultiSeriesXYDataModel multiSeriesDataModel = new MultiSeriesXYDataModel();
    
    for (int i = 0; i < queryResults.length; i++) {
      Object domainValue = queryResults[i][domainColumn];      
      String seriesName = queryResults[i][seriesColumn] != null ? queryResults[i][seriesColumn].toString() : "null"; 
      
      if (domainValue == null) {
        if (convertNullValuesToZero) {
          domainValue = new Integer(0);
        }
      } else if (!(domainValue instanceof Number)) {
        domainValue = null;
      }
      
      Object rangeValue = queryResults[i][rangeColumn];
      if (rangeValue == null) {
        if (convertNullValuesToZero) {
          rangeValue = new Integer(0);
        }
      } else if (!(rangeValue instanceof Number)) {
        rangeValue = null;
      }
      
      multiSeriesDataModel.addDataPoint(seriesName, (Number)domainValue, (Number)rangeValue);
    }
    
    return multiSeriesDataModel;
  }
  
  private static NamedValuesDataModel createNamedValueDataModel(Object[][] queryResults, int domainColumn, int rangeColumn, boolean convertNullsToZero, boolean autoSum) {
    NamedValuesDataModel basicChartDataModel = new NamedValuesDataModel();
    
    for (int i = 0; i < queryResults.length; i++) {
      Object domainValue = null;
      if(domainColumn > -1){
        domainValue = queryResults[i][domainColumn];
      }
      if (domainValue == null) {
        domainValue = "null";
      }
      
      String name = domainValue.toString();
      
      Object rangeValue = queryResults[i][rangeColumn];
      if (rangeValue == null) {
        if (convertNullsToZero) {
          rangeValue = new Integer(0);
        }
      } else if (!(rangeValue instanceof Number)) {
        rangeValue = null;
      }
      
      if (autoSum) {
        NamedValue existingDataPoint = basicChartDataModel.getNamedValue(name);
        if (existingDataPoint == null) {
          basicChartDataModel.add(new NamedValue(name, (Number)rangeValue));
        } else if (existingDataPoint.getValue() == null) {
          existingDataPoint.setValue((Number)rangeValue);
        } else if (rangeValue != null) {
          existingDataPoint.setValue(((Number)existingDataPoint.getValue()).doubleValue() + ((Number)rangeValue).doubleValue());
        }
      } else {
        basicChartDataModel.add(new NamedValue(name, (Number)rangeValue));
      }
    }      
    
    return basicChartDataModel;
  }
  
  private static BasicDataModel createBasicDataModel(Object[][] queryResults, int rangeColumn, boolean convertNullsToZero, boolean autoSum) {
    BasicDataModel oneDimensionalDataModel = new BasicDataModel(autoSum);
    
    for (int i = 0; i < queryResults.length; i++) {
      Object rangeValue = queryResults[i][rangeColumn];
      if (rangeValue == null) {
        if (convertNullsToZero) {
          rangeValue = new Integer(0);
        }
      } else if (!(rangeValue instanceof Number)) {
        rangeValue = null;
      }
      
      oneDimensionalDataModel.addDataPoint((Number)rangeValue);
    }      
    
    return oneDimensionalDataModel;
  }
  
  private static XYDataModel createXYDataModel(Object[][] queryResults, int seriesColumn, int rangeColumn, boolean convertNullsToZero) {
    XYDataModel basicChartDataModel = new XYDataModel();
    
    for (int i = 0; i < queryResults.length; i++) {
      Object domainValue = queryResults[i][rangeColumn];
      if (domainValue == null) {
        if (convertNullsToZero) {
          domainValue = new Integer(0);
        }
      } else if (!(domainValue instanceof Number)) {
        domainValue = null;
      }
      
      Object rangeValue = queryResults[i][rangeColumn];
      if (rangeValue == null) {
        if (convertNullsToZero) {
          rangeValue = new Integer(0);
        }
      } else if (!(rangeValue instanceof Number)) {
        rangeValue = null;
      }
      
      if ((domainValue != null) && (rangeValue != null)) {
        basicChartDataModel.add(new XYDataPoint((Number)domainValue, (Number)rangeValue));
      }
    }      
    
    return basicChartDataModel;
  }
  
}
