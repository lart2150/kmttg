package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.util.LinkedHashMap;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class AdvSearch {
   private JFrame dialog = null;
   private JPanel content = null;
   private JComboBox savedEntries = null;
   private JComboBox creditKeywordRole = null;
   private JComboBox collectionType = null;
   private JComboBox category = null;
   private JTextField title = null;
   private JTextField titleKeyword = null;
   private JTextField subtitleKeyword = null;
   private JTextField keywords = null;
   private JTextField subtitle = null;
   private JTextField descriptionKeyword = null;
   private JTextField channels = null;
   private JTextField originalAirYear = null;
   private JTextField creditKeyword = null;
   private JComboBox hdtv = null;
   private JCheckBox receivedChannelsOnly = null;
   private JCheckBox favoriteChannelsOnly = null;
   private String tivoName = null;
   private int max_search = 100;
   private String saveFile = "wishlists.ini";
   private LinkedHashMap<String,JSONObject> entries = new LinkedHashMap<String,JSONObject>();

   public void display(JFrame frame, String tivoName, int max_search) {
      // Create dialog if not already created
      if (dialog == null) {
         create(frame);
         
         // Parse saveFile to define current configuration
         readFile(config.programDir + File.separator + saveFile);
      }
      this.tivoName = tivoName;
      this.max_search = max_search;
      
      // Display the dialog
      if (config.getTivoUsername() == null)
         category.setEnabled(false);
      else
         category.setEnabled(true);
      dialog.setVisible(true);
      title.grabFocus();
   }
  
   private void create(JFrame frame) {      
      // Create all the components of the dialog
      JLabel savedEntries_label = new JLabel("Saved entries");
      savedEntries = new JComboBox(new Object[] {"Default"});
      savedEntries.setToolTipText(getToolTip("savedEntries"));
      savedEntries.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED)
                setValues((String)savedEntries.getSelectedItem());
         }
      });

      creditKeywordRole = new JComboBox(new Object[] {
         "actor", "director", "producer", "executiveProducer", "writer"
      });
      creditKeywordRole.setToolTipText(getToolTip("creditKeywordRole"));
      
      JLabel collectionType_label = new JLabel("Genre");
      collectionType = new JComboBox(new Object[] {
         "ALL", "movie", "series", "special"
      });
      collectionType.setToolTipText(getToolTip("collectionType"));
      
      JLabel category_label = new JLabel("Category");
      category = new JComboBox(new Object[] {
         "ALL",
         "Action Adventure",
         "Arts",
         "Comedy",
         "Daytime",
         "Documentary",
         "Drama",
         "Educational",
         "Interests",
         "Kids",
         "Lifestyle",
         "Movies",
         "Mystery and Suspense",
         "News and Business",
         "Reality",
         "Sci-Fi and Fantasy",
         "Science and Nature",
         "Shorts",
         "Sport",
         "Sports",
         "Talk Shows",
         "TV Shows"
      });
      category.setToolTipText(getToolTip("category"));
                        
      JLabel title_label = new JLabel("Title");
      title = new JTextField(30);
      title.setToolTipText(getToolTip("title"));
      
      JLabel titleKeyword_label = new JLabel("Title keyword");
      titleKeyword = new JTextField(30);
      titleKeyword.setToolTipText(getToolTip("titleKeyword"));
      
      JLabel subtitleKeyword_label = new JLabel("Subtitle keyword");
      subtitleKeyword = new JTextField(30);
      subtitleKeyword.setToolTipText(getToolTip("subtitleKeyword"));

      JLabel keywords_label = new JLabel("Keywords");
      keywords = new JTextField(30);
      keywords.setToolTipText(getToolTip("keywords"));

      JLabel subtitle_label = new JLabel("Subtitle");
      subtitle = new JTextField(30);
      subtitle.setToolTipText(getToolTip("subtitle"));

      JLabel descriptionKeyword_label = new JLabel("Description keyword");
      descriptionKeyword = new JTextField(30);
      descriptionKeyword.setToolTipText(getToolTip("descriptionKeyword"));

      JLabel channels_label = new JLabel("Restrict channels");
      channels = new JTextField(30);
      channels.setToolTipText(getToolTip("channels"));

      JLabel originalAirYear_label = new JLabel("Year");
      originalAirYear = new JTextField(30);
      originalAirYear.setToolTipText(getToolTip("originalAirYear"));

      creditKeyword = new JTextField(30);
      creditKeyword.setToolTipText(getToolTip("creditKeyword"));
      
      JLabel hdtv_label = new JLabel("Recording types");
      hdtv = new JComboBox(new Object[] {"both", "HD", "SD"});
      hdtv.setToolTipText(getToolTip("hdtv"));
      hdtv.setSelectedItem("HD");
      
      receivedChannelsOnly = new JCheckBox("Received channels only");
      receivedChannelsOnly.setToolTipText(getToolTip("receivedChannelsOnly"));
      receivedChannelsOnly.setSelected(true);
      
      favoriteChannelsOnly = new JCheckBox("Favorite channels only");
      favoriteChannelsOnly.setToolTipText(getToolTip("favoriteChannelsOnly"));
      favoriteChannelsOnly.setSelected(false);
            
      JButton search = new JButton("Search");
      search.setToolTipText(getToolTip("search"));
      search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            SearchCB();
         }
      });
      
      JButton save = new JButton("Save...");
      save.setToolTipText(getToolTip("save"));
      save.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String text = (String)savedEntries.getSelectedItem();
            if (text.equals("Default"))
               text = "";
            String entry = JOptionPane.showInputDialog(null, "Enter wishlist name", text);
            if (entry != null && entry.length() > 0)
               addEntry(entry);
         }
      });
      
      JButton delete = new JButton("Delete");
      delete.setToolTipText(getToolTip("delete"));
      delete.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String entry = (String)savedEntries.getSelectedItem();
            if (! entry.equals("Default"))
               deleteEntry(entry);
         }
      });
      
      JButton close = new JButton("Close");
      close.setToolTipText(getToolTip("close"));
      close.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            dialog.setVisible(false);
         }
      });
      
      // layout manager start
      content = new JPanel(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      
      Dimension label_size = creditKeywordRole.getPreferredSize();
      
      int gy = 0;
      c.insets = new Insets(4, 2, 4, 2);
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.NONE;

      JPanel row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      Dimension space_5 = new Dimension(5,0);
      row.add(savedEntries_label);
      row.add(Box.createRigidArea(space_5));
      row.add(savedEntries);
      row.add(Box.createRigidArea(space_5));
      row.add(save);
      row.add(Box.createRigidArea(space_5));
      row.add(delete);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      title_label.setPreferredSize(label_size);
      row.add(title_label);
      row.add(Box.createRigidArea(space_5));
      row.add(title);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      titleKeyword_label.setPreferredSize(label_size);
      row.add(titleKeyword_label);
      row.add(Box.createRigidArea(space_5));
      row.add(titleKeyword);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      subtitle_label.setPreferredSize(label_size);
      row.add(subtitle_label);
      row.add(Box.createRigidArea(space_5));
      row.add(subtitle);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      subtitleKeyword_label.setPreferredSize(label_size);
      row.add(subtitleKeyword_label);
      row.add(Box.createRigidArea(space_5));
      row.add(subtitleKeyword);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      keywords_label.setPreferredSize(label_size);
      row.add(keywords_label);
      row.add(Box.createRigidArea(space_5));
      row.add(keywords);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      descriptionKeyword_label.setPreferredSize(label_size);
      row.add(descriptionKeyword_label);
      row.add(Box.createRigidArea(space_5));
      row.add(descriptionKeyword);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      channels_label.setPreferredSize(label_size);
      row.add(channels_label);
      row.add(Box.createRigidArea(space_5));
      row.add(channels);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      originalAirYear_label.setPreferredSize(label_size);
      row.add(originalAirYear_label);
      row.add(Box.createRigidArea(space_5));
      row.add(originalAirYear);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      row.add(creditKeywordRole);
      row.add(Box.createRigidArea(space_5));
      row.add(creditKeyword);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      collectionType_label.setPreferredSize(label_size);
      row.add(collectionType_label);
      row.add(Box.createRigidArea(space_5));
      row.add(collectionType);
      row.add(Box.createRigidArea(space_5));
      row.add(hdtv_label);
      row.add(Box.createRigidArea(space_5));
      row.add(hdtv);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      category_label.setPreferredSize(label_size);
      row.add(category_label);
      row.add(Box.createRigidArea(space_5));
      row.add(category);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      row.add(receivedChannelsOnly);
      row.add(Box.createRigidArea(space_5));
      row.add(favoriteChannelsOnly);
      content.add(row, c);
      
      gy++;
      c.gridy = gy;
      row = new JPanel();
      row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
      row.add(search);
      row.add(Box.createRigidArea(space_5));
      row.add(close);
      content.add(row, c);
   
      // create dialog window
      dialog = new JFrame();
      dialog.setTitle("Advanced Search");
      dialog.setContentPane(content);
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.pack();
   }
   
   private void deleteEntry(String entry) {
      entries.remove(entry);
      savedEntries.removeItemAt(savedEntries.getSelectedIndex());
      saveEntries();
   }
   
   private void readFile(String inputFile) {
      if (! file.isFile(inputFile))
         return;
      try {
         BufferedReader ini = new BufferedReader(new FileReader(inputFile));
         String line = null;
         String key = null;
         JSONObject json = new JSONObject();         
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^<.+>")) {
               if (key != null && json.length() > 0) {
                  entries.put(key, json);
                  uniqueEntry(key);
               }
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               json = new JSONObject();
               continue;
            }
            
            String name = line.replaceFirst("=.+", "");
            String value = line.replaceFirst("^.+=", "");
            json.put(name, value);
         } // while
         // Last entry
         if (key != null && json.length() > 0) {
            entries.put(key, json);
            uniqueEntry(key);
         }
         ini.close();
         if (savedEntries.getItemCount() > 1)
            savedEntries.setSelectedIndex(1);
      } catch (Exception e) {
         log.error("AdvSearch readFile error - " + e.getMessage());
      }
   }
   
   private void saveEntries() {
      String wFile = config.programDir + File.separator + saveFile;
      if (entries.isEmpty()) {
         log.warn("No entries to save - removing file: " + wFile);
         file.delete(wFile);
         return;
      }
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(wFile, false));
         String eol = "\r\n";
         for (String entry : entries.keySet()) {
            JSONObject json = entries.get(entry);
            ofp.write("<" + entry + ">" + eol);
            String[] items = {
               "title", "titleKeyword", "subtitleKeyword", "keywords", "subtitle",
               "descriptionKeyword", "channels", "originalAirYear",
               "creditKeywordRole", "creditKeyword", "collectionType", "category", "hdtv",
               "receivedChannelsOnly", "favoriteChannelsOnly"
            };
            for (String item : items) {
               if (json.has(item))
                  ofp.write(item + "=" + json.getString(item) + eol);
            }
         }
         ofp.close();
         log.warn("Saved to file: " + wFile);
      } catch (Exception e) {
         log.error("AdvSearch saveEntries error - " + e.getMessage());
      }
   }
   
   private void addEntry(String entry) {
      try {
         log.warn("Saving wishlist entry: " + entry);
         JSONObject json = new JSONObject();
         json.put("title", string.removeLeadingTrailingSpaces(title.getText()));
         json.put("titleKeyword", string.removeLeadingTrailingSpaces(titleKeyword.getText()));
         json.put("subtitleKeyword", string.removeLeadingTrailingSpaces(subtitleKeyword.getText()));
         json.put("subtitle", string.removeLeadingTrailingSpaces(subtitle.getText()));
         json.put("descriptionKeyword", string.removeLeadingTrailingSpaces(descriptionKeyword.getText()));
         json.put("originalAirYear", string.removeLeadingTrailingSpaces(originalAirYear.getText()));
         json.put("creditKeyword", string.removeLeadingTrailingSpaces(creditKeyword.getText()));
         json.put("creditKeywordRole", creditKeywordRole.getSelectedItem());
         json.put("collectionType", collectionType.getSelectedItem());
         json.put("category", category.getSelectedItem());
         json.put("keywords", string.removeLeadingTrailingSpaces(keywords.getText()));
         json.put("channels", string.removeLeadingTrailingSpaces(channels.getText()));
         json.put("hdtv", hdtv.getSelectedItem());
         if (receivedChannelsOnly.isSelected())
            json.put("receivedChannelsOnly", "on");
         else
            json.put("receivedChannelsOnly", "off");
         if (favoriteChannelsOnly.isSelected())
            json.put("favoriteChannelsOnly", "on");
         else
            json.put("favoriteChannelsOnly", "off");
         entries.put(entry, json);
         saveEntries();
         uniqueEntry(entry);
      } catch (JSONException e) {
         log.error("AdvSearch addEntry error - " + e.getMessage());
      }
   }
   
   private void uniqueEntry(String entry) {
      Boolean add = true;
      for (int i=0; i<savedEntries.getItemCount(); ++i) {
         if (savedEntries.getItemAt(i).equals(entry))
            add = false;
      }
      if (add)
         savedEntries.addItem(entry);
      savedEntries.setSelectedItem(entry);
   }
   
   private void setValues(String entry) {
      if (entry.equals("Default"))
         resetToDefaults();
      else {
         try {
            JSONObject json = entries.get(entry);
            String text;
            text = "";
            if (json.has("title"))
               text = json.getString("title");
            title.setText(text);
            
            text = "";
            if (json.has("titleKeyword"))
               text = json.getString("titleKeyword");
            titleKeyword.setText(text);
            
            text = "";
            if (json.has("subtitleKeyword"))
               text = json.getString("subtitleKeyword");
            subtitleKeyword.setText(text);
            
            text = "";
            if (json.has("subtitle"))
               text = json.getString("subtitle");
            subtitle.setText(text);
            
            text = "";
            if (json.has("descriptionKeyword"))
               text = json.getString("descriptionKeyword");
            descriptionKeyword.setText(text);
            
            text = "";
            if (json.has("originalAirYear"))
               text = json.getString("originalAirYear");
            originalAirYear.setText(text);
            
            text = "";
            if (json.has("creditKeyword"))
               text = json.getString("creditKeyword");
            creditKeyword.setText(text);
           
            text = "actor";
            if (json.has("creditKeywordRole"))
               text = json.getString("creditKeywordRole");
            creditKeywordRole.setSelectedItem(text);
            
            text = "ALL";
            if (json.has("collectionType"))
               text = json.getString("collectionType");
            collectionType.setSelectedItem(text);
            
            text = "ALL";
            if (json.has("category"))
               text = json.getString("category");
            category.setSelectedItem(text);
            
            text = "";
            if (json.has("keywords"))
               text = json.getString("keywords");
            keywords.setText(text);
            
            text = "";
            if (json.has("channels"))
               text = json.getString("channels");
            channels.setText(text);
            
            text = "HD";
            if (json.has("hdtv"))
               text = json.getString("hdtv");
            hdtv.setSelectedItem(text);
            
            receivedChannelsOnly.setSelected(json.getString("receivedChannelsOnly").equals("on"));
            favoriteChannelsOnly.setSelected(json.getString("favoriteChannelsOnly").equals("on"));
         } catch (JSONException e) {
            log.error("AdvSearch setValues error - " + e.getMessage());
         }
      }
   }
   
   private void resetToDefaults() {
      title.setText("");
      titleKeyword.setText("");
      subtitleKeyword.setText("");
      subtitle.setText("");
      descriptionKeyword.setText("");
      originalAirYear.setText("");
      creditKeyword.setText("");
      creditKeywordRole.setSelectedItem("actor");
      collectionType.setSelectedItem("ALL");
      category.setSelectedItem("ALL");
      keywords.setText("");
      channels.setText("");
      hdtv.setSelectedItem("HD");
      receivedChannelsOnly.setSelected(true);
      favoriteChannelsOnly.setSelected(false);
   }
   
   private void SearchCB() {
      try {
         String text;
         JSONObject json = new JSONObject();
         json.put("format", "idSet");
         json.put("namespace", "refserver");
         json.put("searchable", true);
         Date now = new Date();
         json.put("minStartTime", rnpl.getStringFromLongDate(now.getTime()));
         
         text = string.removeLeadingTrailingSpaces(title.getText());
         if (text != null && text.length() > 0) {
            json.put("title", text);
         }
         text = string.removeLeadingTrailingSpaces(titleKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("titleKeyword", text);
         }
         text = string.removeLeadingTrailingSpaces(subtitleKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("subtitleKeyword", text);
         }
         text = string.removeLeadingTrailingSpaces(subtitle.getText());
         if (text != null && text.length() > 0) {
            json.put("subtitle", text);
         }
         text = string.removeLeadingTrailingSpaces(descriptionKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("descriptionKeyword", text);
         }
         String cat = (String)category.getSelectedItem();
         if (cat.equals("ALL"))
            cat = null;
         if (config.getTivoUsername() == null) {
            cat = null;
            category.setSelectedItem("ALL");
         }
         text = string.removeLeadingTrailingSpaces(originalAirYear.getText());
         if (text != null && text.length() > 0) {
            String type = (String)(collectionType.getSelectedItem());
            if (type.equals("movie") || (cat != null && cat.equals("Movies")))
               json.put("movieYear", text);
            else
               json.put("originalAirYear", text);
         }
         text = string.removeLeadingTrailingSpaces(creditKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("creditKeyword", text);
            json.put("creditKeywordRole", (String)creditKeywordRole.getSelectedItem());
         }
         text = (String)collectionType.getSelectedItem();
         if (! text.equals("ALL")) {
            json.put("collectionType", text);
         }
         text = string.removeLeadingTrailingSpaces(keywords.getText());
         if (text != null && text.length() > 0) {
            if (text.contains("(") || text.contains("-") || text.contains("+") || text.contains("*"))
               json.put("advancedKeyword", true);
            json.put("keyword", text);
         }
         String[] chans = null;
         text = string.removeLeadingTrailingSpaces(channels.getText());
         if (text != null && text.length() > 0) {
            chans = text.split("\\s+");
         }
         text = (String)hdtv.getSelectedItem();
         if (text.equals("HD"))
            json.put("hdtv", true);
         if (text.equals("SD"))
            json.put("hdtv", false);
         if (favoriteChannelsOnly.isSelected())
            json.put("favoriteChannelsOnly", true);
         if (! receivedChannelsOnly.isSelected())
            json.put("receivedChannelsOnly", false);

         //log.print(json.toString(3)); // debugging
         jobData job = new jobData();
         job.source                 = tivoName;
         job.tivoName               = tivoName;
         job.type                   = "remote";
         job.name                   = "Remote";
         job.search                 = config.gui.remote_gui.tab_search;
         job.remote_search_max      = max_search;
         job.remote_adv_search      = true;
         job.remote_adv_search_json = json;
         if (chans != null)
            job.remote_adv_search_chans = chans;
         if (cat != null)
            job.remote_adv_search_cat = cat;
         jobMonitor.submitNewJob(job);
      } catch (JSONException e) {
         log.error("AdvSearch SearchCB error - " + e.getMessage());
      }
   }
      
   private String getToolTip(String component) {
      String text = "";
      if (component.equals("title")) {
         text =  "<b>Title</b><br>";
         text += "Match this show title exactly.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("savedEntries")) {
         text =  "<b>Saved Entries</b><br>";
         text += "Contains previously saved search configurations. Select a named configuration<br>";
         text += "to set dialog entry settings to that saved configuration.<br>";
         text += "Select <b>Default</b> to reset all dialog entries to default/empty config.";
      }
      else if (component.equals("titleKeyword")) {
         text =  "<b>Title Keyword</b><br>";
         text += "Match this keyword in show main title text.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("subtitleKeyword")) {
         text =  "<b>Subtitle Keyword</b><br>";
         text += "Match this keyword in show subtitle text.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("keywords")) {
         text =  "<b>Keywords</b><br>";
         text += "This can be of two forms:<br>";
         text += "1. A simple keyword or phrase to match titles, subtitles, or descriptions of shows.<br>";
         text += "2. A complex list of space separated keywords with boolean operators:<br>";
         text += "<b>+keyword</b>: + prefix indicates required keyword (AND).<br>";
         text += "<b>-keyword</b>: - prefix indicates required missing keyword (NOT).<br>";
         text += "<b>(keyword)</b>: keyword inside parentheses indicate optional (OR).<br>";
         text += "<b>keyword*</b>: * char is wildcard but requires 1 alphanumeric char with it.<br>";
         text += "You can have multiple keyword operators each separated by a space.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("subtitle")) {
         text =  "<b>Subtitle</b><br>";
         text += "Match this show subtitle exactly.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("descriptionKeyword")) {
         text =  "<b>Description keyword</b><br>";
         text += "Match this keyword or phrase in show description text.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Subtitle text is also considered as part of description.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("channels")) {
         text =  "<b>Restrict channels</b><br>";
         text += "A list of one or more channel numbers to restrict matches to.<br>";
         text += "For more than 1 channel separate each channel number by a space.";
      }
      else if (component.equals("originalAirYear")) {
         text =  "<b>Year</b><br>";
         text += "Match only shows whose original air date happened in given year.<br>";
         text += "If movie genre is selected, it matches the year the movie was released.<br>";
         text += "NOTE: You can use a single year here (multiple years not supported).";
      }
      else if (component.equals("creditKeywordRole")) {
         text =  "<b>Other</b><br>";
         text += "Set auxilary matching criteria to this selected role.";
      }
      else if (component.equals("creditKeyword")) {
         text =  "<b>Other keyword</b><br>";
         text += "Match this keyword or phrase in role selected to the left of this field.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("collectionType")) {
         text =  "<b>Genre</b><br>";
         text += "Limit matches to shows in this genre.<br>";
         text += "Default is <b>ALL</b> which means show can be in any genre, else<br>";
         text += "match the specific genre selected in this list.";
      }
      else if (component.equals("category")) {
         text =  "<b>Category</b><br>";
         text += "Limit matches to shows in this category.<br>";
         text += "Default is <b>ALL</b> which means show can be in any category, else<br>";
         text += "match the specific category selected in this list.<br>";
         text += "<b>NOTE: This field is only available if kmttg has access to your tivo.com<br>";
         text += "username & password (located under config->Tivos tab)</b>.";
      }
      else if (component.equals("hdtv")) {
         text =  "<b>Recording types</b><br>";
         text += "both = match both HD and SD recordings.<br>";
         text += "HD = match only HD recordings.<br>";
         text += "SD = match only SD recordings.";
      }
      else if (component.equals("receivedChannelsOnly")) {
         text =  "<b>Received channels only</b><br>";
         text += "When disabled also include channels turned off in <b>Channel List</b>";
      }
      else if (component.equals("favoriteChannelsOnly")) {
         text =  "<b>Favorite channels only</b><br>";
         text += "When enabled match only channels set in <b>Favorites List</b>";
      }
      else if (component.equals("search")) {
         text =  "<b>Search</b><br>";
         text += "Initiate search using given search criteria.<br>";
         text += "Matches will be listed in Remote <b>Search</b> table.";
      }
      else if (component.equals("save")) {
         text =  "<b>Save...</b><br>";
         text += "Save the current search criteria with a name of your choosing.<br>";
         text += "The name you use will be added to <b>Saved entries</b> pulldown so<br>";
         text += "that you can restore the associated search criteria settings at any time.<br>";
         text += "By saving search criteria you are effectively creating portable wishlist<br>";
         text += "entries that won't be lost when replacing TiVo units.";
      }
      else if (component.equals("delete")) {
         text =  "<b>Delete</b><br>";
         text += "Remove a previously saved wishlist entry.<br>";
         text += "NOTE: The <b>Default</b> entry cannot be deleted.";
      }
      else if (component.equals("close")) {
         text =  "<b>Close</b><br>";
         text += "Close the <b>Advanced Search</b> dialog window.";
      }
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }
}
