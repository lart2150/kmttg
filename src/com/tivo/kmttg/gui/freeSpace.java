package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.sourceforge.chart2d.Chart2DProperties;
import net.sourceforge.chart2d.Dataset;
import net.sourceforge.chart2d.LegendProperties;
import net.sourceforge.chart2d.MultiColorsProperties;
import net.sourceforge.chart2d.Object2DProperties;
import net.sourceforge.chart2d.PieChart2D;
import net.sourceforge.chart2d.PieChart2DProperties;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class freeSpace {
   private String tivoName = null;
   private PieChart2D chart2D = null;
   private JFrame frame = null;
   private JDialog dialog = null;
   private JTextField space = null;
   private float disk_space = 0;
   
   freeSpace(String tivoName, JFrame frame) {
      this.tivoName = tivoName;
      this.frame = frame;
      init();
   }
   
   private void init() {
      
      //<-- Begin Chart2D configuration -->

      //Configure object properties
      Object2DProperties object2DProps = new Object2DProperties();
      object2DProps.setObjectTitleText("Disk Space Usage (GB)");

      //Configure chart properties
      Chart2DProperties chart2DProps = new Chart2DProperties();
      chart2DProps.setChartDataLabelsPrecision(-1);

      //Configure legend properties
      LegendProperties legendProps = new LegendProperties();
      String[] legendLabels =
        {"Free Space", "TiVo Suggestions", "Keep Until I Delete", "Keep Until Space Needed"};
      legendProps.setLegendLabelsTexts(legendLabels);

      //Configure graph component colors
      MultiColorsProperties multiColorsProps = new MultiColorsProperties();

      //Configure pie area
      PieChart2DProperties pieChart2DProps = new PieChart2DProperties();

      //Configure chart
      chart2D = new PieChart2D();
      chart2D.setObject2DProperties(object2DProps);
      chart2D.setChart2DProperties(chart2DProps);
      chart2D.setLegendProperties(legendProps);
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
      content = new JPanel(new GridBagLayout());
      content.setLayout(new GridBagLayout());
      
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.ipady = 0;
      c.weighty = 1.0;  // default to vertical stretch
      c.weightx = 1.0;  // default to horizontal stretch
      c.gridx = 0;
      c.gridy = 0;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.NORTH;
      c.fill = GridBagConstraints.BOTH;
      
      // Free space
      JLabel space_label = new JLabel("Total Disk Space (GB):");
      space = new JTextField(20);
      space.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            setData();
            chart2D.repaint();
            config.diskSpace.put(tivoName, getDiskSpace());
            config.save(config.configIni);
         }
      });
      space.setText(String.format("%.1f",getDiskSpace()));
      content.add(space_label);
      c.gridx = 2;
      content.add(space);
      
      // Pie chart
      c.gridx = 0;
      c.gridy = 1;
      c.gridwidth = 2;
      content.add(chart2D, c);
     
      // create and display dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
      dialog.setTitle(tivoName + " Disk Usage Analysis");
      dialog.setContentPane(content);
      dialog.pack();
      //dialog.setSize(600,400);
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.setVisible(true);
   }
   
   public Boolean setData() {
      // Init data to 0
      Hashtable<String,Float> data = new Hashtable<String,Float>();      
      data.put("suggestions", (float)0.0);
      data.put("kuid",        (float)0.0);
      data.put("kusn",        (float)0.0);
      
      Stack<Hashtable<String,String>> entries = config.gui.getTab(tivoName).getTable().getEntries();
      if (entries == null) return false;
      float size;
      for (int i=0; i<entries.size(); ++i) {
         size = 0;
         if (entries.get(i).containsKey("size")) {
            size = (float) (Float.parseFloat(entries.get(i).get("size"))/Math.pow(2,30));
         }
         if (entries.get(i).containsKey("ExpirationImage")) {
            if (entries.get(i).get("ExpirationImage").equals("suggestion-recording")) {
               data.put("suggestions", data.get("suggestions") + size);
               continue;
            }
            if (entries.get(i).get("ExpirationImage").equals("save-until-i-delete-recording")) {
               data.put("kuid", data.get("kuid") + size);
               continue;
            }
         }
         data.put("kusn", data.get("kusn") + size);
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
      
      int numSets = 4, numCats = 1, numItems = 1;
      Dataset dataset = new Dataset (numSets, numCats, numItems);
      dataset.set(0, 0, 0, data.get("free"));
      dataset.set(1, 0, 0, data.get("suggestions"));
      dataset.set(2, 0, 0, data.get("kuid"));
      dataset.set(3, 0, 0, data.get("kusn"));      
      chart2D.setDataset(dataset);
      
      return true;
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
   
   public void destroy() {
      if (dialog != null) dialog.dispose();
   }
}
