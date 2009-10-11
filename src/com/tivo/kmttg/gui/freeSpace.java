package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import net.sourceforge.chart2d.Chart2DProperties;
import net.sourceforge.chart2d.Dataset;
import net.sourceforge.chart2d.LegendProperties;
import net.sourceforge.chart2d.MultiColorsProperties;
import net.sourceforge.chart2d.Object2DProperties;
import net.sourceforge.chart2d.PieChart2D;
import net.sourceforge.chart2d.PieChart2DProperties;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class freeSpace {
   private String tivoName = null;
   private PieChart2D chart2D = null;
   private LegendProperties legendProps = null;
   private JFrame frame = null;
   private JDialog dialog = null;
   private JTextField space = null;
   private bitrateTable tab = null;
   private JLabel totals1 = null;
   private JLabel totals2 = null;
   private float disk_space = 0;
   private Hashtable<String,Hashtable<String,Double>> chanData = new Hashtable<String,Hashtable<String,Double>>();
   private Hashtable<String,Object> totalsData = new Hashtable<String,Object>();
   
   freeSpace(String tivoName, JFrame frame) {
      this.tivoName = tivoName;
      this.frame = frame;
      init();
   }
   
   private void init() {
      
      //<-- Begin Chart2D configuration -->

      //Configure object properties
      Object2DProperties object2DProps = new Object2DProperties();
      object2DProps.setObjectTitleText(tivoName + " Disk Space Usage");

      //Configure chart properties
      Chart2DProperties chart2DProps = new Chart2DProperties();
      chart2DProps.setChartDataLabelsPrecision(-1);

      //Configure graph component colors
      MultiColorsProperties multiColorsProps = new MultiColorsProperties();
      Color[] colors = {Color.yellow, Color.green, Color.pink, Color.blue};
      multiColorsProps.setColorsCustom(colors);
      multiColorsProps.setColorsCustomize(true);
      
      // Legend properties
      legendProps = new LegendProperties();
      //legendProps.setLegendLabelsFontName(config.tableFont.getFamily());
      legendProps.setLegendLabelsFontPointModel(7);

      //Configure pie area
      PieChart2DProperties pieChart2DProps = new PieChart2DProperties();
      pieChart2DProps.setPieLabelsExistence(false);

      //Configure chart
      chart2D = new PieChart2D();
      chart2D.setObject2DProperties(object2DProps);
      chart2D.setChart2DProperties(chart2DProps);
      chart2D.setMultiColorsProperties(multiColorsProps);
      chart2D.setPieChart2DProperties(pieChart2DProps);
      chart2D.setPreferredSize(new Dimension((int)frame.getSize().width/2,100));
      
      // Populate dataset
      if ( ! setData() ) {
         log.error("Failed to obtain data for TiVo: " + tivoName);
         return;
      }
      
      // Dialog configuration
      JPanel content;      
      
      // Define content for dialog window
      int gy = 0;
      content = new JPanel();
      content.setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 1.0;  // default to horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.NONE;
            
      // Row 1
      JPanel row1 = new JPanel();
      row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
      // Free space
      JLabel space_label = new JLabel("Total Disk Space (GB):");
      space = new JTextField(8);
      space.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            setData();
            chart2D.repaint();
            updateLabels();
            config.diskSpace.put(tivoName, getDiskSpace());
            config.save(config.configIni);
         }
      });
      space.setText(String.format("%.1f",getDiskSpace()));
      row1.add(space_label);
      row1.add(space);
      
      // Build content layout
      content.add(row1, c);
      
      gy++;
      c.gridy = gy;
      c.weighty = 1.0;
      c.fill = GridBagConstraints.BOTH;
      content.add(chart2D, c);
      
      // totals
      totals1 = new JLabel("");
      gy++;
      c.gridy = gy;
      c.weighty = 0.0;
      c.fill = GridBagConstraints.CENTER;
      content.add(totals1, c);
      
      totals2 = new JLabel("");
      updateLabels();
      gy++;
      c.gridy = gy;
      c.weighty = 0.0;
      c.fill = GridBagConstraints.CENTER;
      content.add(totals2, c);
      
      // bitrateTable
      tab = new bitrateTable();
      tab.AddRows(chanData);
      tab.TABLE.setPreferredScrollableViewportSize(tab.TABLE.getPreferredSize());
      JScrollPane tabScroll = new JScrollPane(tab.TABLE);
      gy++;
      c.gridy = gy;
      c.weighty = 0.3;
      c.fill = GridBagConstraints.BOTH;
      content.add(tabScroll, c);
     
      // create and display dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
      dialog.setTitle(tivoName + " Disk Usage Analysis");
      dialog.setContentPane(content);
      dialog.pack();
      dialog.setSize((int)(frame.getSize().width), (int)(frame.getSize().height));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.setVisible(true);      
   }
   
   public Boolean setData() {
      // Init data to 0
      Hashtable<String,Float> data = new Hashtable<String,Float>();      
      data.put("suggestions", (float)0.0);
      data.put("kuid",        (float)0.0);
      data.put("kusn",        (float)0.0);
      
      // Init totalsData to 0
      totalsData.put("bytes", (double)0.0);
      totalsData.put("duration", (double)0.0);
      
      Stack<Hashtable<String,String>> entries = config.gui.getTab(tivoName).getTable().getEntries();
      if (entries == null) return false;
      Double duration, bytes;
      float sizeGB;
      totalsData.put("recordings", entries.size());
      for (int i=0; i<entries.size(); i++) {  
         duration = 0.0;
         bytes = 0.0;
         sizeGB = (float)0.0;
         if (entries.get(i).containsKey("duration")) {
            duration = Double.parseDouble(entries.get(i).get("duration"))/1000.0;
         }
         if (entries.get(i).containsKey("size")) {
            bytes = Double.parseDouble(entries.get(i).get("size"));
            sizeGB = (float) (bytes/Math.pow(2,30));
         }
         // Channel bit rates
         if (entries.get(i).containsKey("channel")) {
            String channel = entries.get(i).get("channel");            
            if ( ! chanData.containsKey(channel) ) {
               chanData.put(channel, new Hashtable<String,Double>());
               chanData.get(channel).put("bytes", 0.0);
               chanData.get(channel).put("duration", 0.0);
            }
            chanData.get(channel).put("bytes",    chanData.get(channel).get("bytes")+bytes);
            chanData.get(channel).put("duration", chanData.get(channel).get("duration")+duration);
         }
         
         // Duration totals
         totalsData.put("duration", (Double)totalsData.get("duration")+duration);
         totalsData.put("bytes", (Double)totalsData.get("bytes")+bytes);

         // Disk space allocation data
         if (entries.get(i).containsKey("suggestion")) {
            data.put("suggestions", data.get("suggestions") + sizeGB);
            continue;
         }
         if (entries.get(i).containsKey("kuid")) {
            data.put("kuid", data.get("kuid") + sizeGB);
            continue;
         }
         data.put("kusn", data.get("kusn") + sizeGB);
      }
            
      // Compute free space
      float available = getDiskSpace();
      float used = data.get("suggestions") + data.get("kuid") + data.get("kusn");
      float free = available - used;
      if (free < 0) {
         // Set disk space available to used space if used > available
         disk_space = used;
         free = 0;
      }
      data.put("free", free);
      totalsData.put("free", free);
      
      int numSets = data.size(), numCats = 1, numItems = 1;
      String[] legendLabels = new String[numSets];
      Dataset dataset = new Dataset (numSets, numCats, numItems);
      String[] keys = {"kusn", "kuid", "suggestions", "free"};
      String[] labels = {
         "Keep Until Space Needed",
         "Keep Until I Delete",
         "Suggestions",
         "Free Space"
      };
      for (int i=0; i<keys.length; ++i) {
         legendLabels[i] = String.format(
            "%s: %.2f GB (%.1f%%)",
            labels[i], data.get(keys[i]), data.get(keys[i])*100/available
         );
         dataset.set(i, 0, 0, data.get(keys[i]));
      }
      
      legendProps.setLegendLabelsTexts(legendLabels);
      chart2D.setLegendProperties(legendProps);
      chart2D.setDataset(dataset);
      
      // Complete Totals data (recordings, bytes, duration set so far)
      totalsData.put("rate", bitrateTable.bitRate((Double)totalsData.get("bytes"),(Double)totalsData.get("duration")));
      totalsData.put("rate", String.format("%.2f", (Double)totalsData.get("rate")));
      totalsData.put("remaining",
         timeRemaining(
            (Double)totalsData.get("bytes"),
            (Float)totalsData.get("free")*Math.pow(2,30),
            (Double)totalsData.get("duration")
         )
      );
      totalsData.put("remaining", secsToHoursMins((Double)totalsData.get("remaining")));
      
      totalsData.put("recordings", "" + totalsData.get("recordings"));
      totalsData.put("bytes", String.format("%.2f", (Double)totalsData.get("bytes")/Math.pow(2,30)));
      totalsData.put("duration", secsToHoursMins((Double)totalsData.get("duration")));
            
      return true;
   }
   
   private void updateLabels() {
      totals1.setText(
         "Recordings: " + totalsData.get("recordings") +
         ", Space used: " + totalsData.get("bytes") + " GB" +
         ", Total time: " + totalsData.get("duration")
      );
      totals2.setText(
         "Average Bit Rate (Mbps): " + totalsData.get("rate") +
         ", Recording Time Remaining: " + totalsData.get("remaining")
      );

   }
   
   private float getDiskSpace() {
      if (config.diskSpace.containsKey(tivoName)) {
         disk_space = config.diskSpace.get(tivoName);
      }
      float available = disk_space;
      if (space != null) {
         String free_space = string.removeLeadingTrailingSpaces(space.getText());
         if (free_space.length() > 0) {
            try {
               available = Float.parseFloat(free_space);
            } catch(NumberFormatException e) {
               log.error("Disk space specification does not evaluate to a number: " + free_space);
               available = 0;
            }
         }
      }
      return available;
   }
   
   private Double timeRemaining(Double totalBytes, Double freeBytes, Double totalSecs) {
      return totalSecs*freeBytes/totalBytes;
   }
   
   private String secsToHoursMins(Double secs) {
      debug.print("secs=" + secs);
      Integer hours = (int) (secs/3600);
      Integer mins  = (int) (secs/60 - hours*60);
      return String.format("%02dh : %02dm", hours, mins);
   }  
   
   public void destroy() {
      if (dialog != null) dialog.dispose();
      chanData.clear();
      totalsData.clear();
   }
}
