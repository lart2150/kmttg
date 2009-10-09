package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
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
      object2DProps.setObjectTitleText(tivoName + " Disk Space Usage (GB)");

      //Configure chart properties
      Chart2DProperties chart2DProps = new Chart2DProperties();
      chart2DProps.setChartDataLabelsPrecision(-1);

      //Configure graph component colors
      MultiColorsProperties multiColorsProps = new MultiColorsProperties();

      //Configure pie area
      PieChart2DProperties pieChart2DProps = new PieChart2DProperties();

      //Configure chart
      chart2D = new PieChart2D();
      chart2D.setObject2DProperties(object2DProps);
      chart2D.setChart2DProperties(chart2DProps);
      chart2D.setMultiColorsProperties(multiColorsProps);
      chart2D.setPieChart2DProperties(pieChart2DProps);
      
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
      c.fill = GridBagConstraints.HORIZONTAL;
            
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
      
      // bitrate label
      JLabel bitrate_label = new JLabel("Channel Bit Rates");
      gy++;
      c.gridy = gy;
      c.weighty = 0.0;
      c.fill = GridBagConstraints.CENTER;
      content.add(bitrate_label, c);
      
      // bitrateTable
      tab = new bitrateTable();
      JScrollPane tabScroll = new JScrollPane(tab.TABLE);
      tab.AddRows(chanData);
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
      float size;
      totalsData.put("recordings", entries.size());
      for (int i=0; i<entries.size(); i++) {         
         // Bit rate & totals data
         if (entries.get(i).containsKey("channel") && entries.get(i).containsKey("size") && entries.get(i).containsKey("duration")) {
            String channel = entries.get(i).get("channel");
            Double bytes = Double.parseDouble(entries.get(i).get("size"));
            Double duration  = Double.parseDouble(entries.get(i).get("duration"))/1000.0;
            if ( ! chanData.containsKey(channel) ) {
               chanData.put(channel, new Hashtable<String,Double>());
               chanData.get(channel).put("bytes", 0.0);
               chanData.get(channel).put("duration", 0.0);
            }
            chanData.get(channel).put("bytes",    chanData.get(channel).get("bytes")+bytes);
            chanData.get(channel).put("duration", chanData.get(channel).get("duration")+duration);
            
            // Want to store total time of all recordings
            totalsData.put("duration", (Double)totalsData.get("duration")+duration);
         }
         

         // Disk space allocation data
         size = 0;
         if (entries.get(i).containsKey("size")) {
            size = (float) (Float.parseFloat(entries.get(i).get("size"))/Math.pow(2,30));
         }
         if (entries.get(i).containsKey("suggestion")) {
            data.put("suggestions", data.get("suggestions") + size);
            continue;
         }
         if (entries.get(i).containsKey("kuid")) {
            data.put("kuid", data.get("kuid") + size);
            continue;
         }
         data.put("kusn", data.get("kusn") + size);
      }
            
      // Compute free space
      float available = getDiskSpace();
      float used = data.get("suggestions") + data.get("kuid") + data.get("kusn");
      totalsData.put("bytes", used);
      float free = available - used;
      if (free < 0) {
         // Set disk space available to used space if used > available
         disk_space = used;
         free = 0;
      }
      data.put("free", free);
      totalsData.put("free", free);
      
      // Don't include any items set to zero
      if (data.get("suggestions") == 0) data.remove("suggestions");
      if (data.get("kuid") == 0)        data.remove("kuid");
      if (data.get("kusn") == 0)        data.remove("kusn");
      
      int numSets = data.size(), numCats = 1, numItems = 1;
      String[] keys = new String[numSets];
      Dataset dataset = new Dataset (numSets, numCats, numItems);
      int i=0;
      for (Enumeration<String> e=data.keys(); e.hasMoreElements();) {
         String name = e.nextElement();
         dataset.set(i, 0, 0, data.get(name));
         keys[i] = name;
         i++;
      }
      setLegends(keys);
      chart2D.setDataset(dataset);
      
      // Complete Totals data (recordings, bytes, duration set so far)
      totalsData.put("rate", (Double)8.0e3*(Float)totalsData.get("bytes")/(Double)totalsData.get("duration"));
      totalsData.put("rate", String.format("%.2f", (Double)totalsData.get("rate")));
      totalsData.put("remaining", (Double)totalsData.get("duration")*(Float)totalsData.get("free")/(Float)totalsData.get("bytes"));
      totalsData.put("remaining", secsToHoursMins((Double)totalsData.get("remaining")));
      
      totalsData.put("recordings", "" + totalsData.get("recordings"));
      totalsData.put("bytes", String.format("%.2f", (Float)totalsData.get("bytes")));
      totalsData.put("duration", secsToHoursMins((Double)totalsData.get("duration")));
            
      return true;
   }

   private void setLegends(String[] keys) {
      //Configure legend properties
      LegendProperties legendProps = new LegendProperties();
      Hashtable<String,String> map = new Hashtable<String,String>();
      map.put("free", "Free Space");
      map.put("suggestions", "TiVo Suggestions");
      map.put("kuid", "Keep Until I Delete");
      map.put("kusn", "Keep Until Space Needed");
      String[] legendLabels = new String[keys.length];
      for (int i=0; i<keys.length; i++) {
         legendLabels[i] = map.get(keys[i]);
      }
      legendProps.setLegendLabelsTexts(legendLabels);
      chart2D.setLegendProperties(legendProps);
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
   
   private String secsToHoursMins(Double secs) {
      debug.print("secs=" + secs);
      Integer hours = (int) (secs/3600);
      Integer mins  = (int) (secs/60 - hours*60);
      return String.format("%02dh : %02dm", hours, mins);
   }  
   
   public void destroy() {
      if (dialog != null) dialog.dispose();
   }
}
