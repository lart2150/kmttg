package com.tivo.kmttg.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Stack;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.main.config;

public class PopupHandler {   
   public static void display(final JXTable TABLE, MouseEvent e) {
      String tabName = config.gui.getCurrentTabName();
      String tivoName;
      if (tabName.equals("FILES"))
         return;
      JPopupMenu popup = new JPopupMenu();
      Stack<PopupPair> items = new Stack<PopupPair>();
      if (tabName.equals("Remote")) {
         // This is a Remote table
         String subTabName = config.gui.remote_gui.getCurrentTabName();
         tivoName = config.gui.remote_gui.getTivoName(subTabName);
         if (subTabName.equals("Season Passes")) {
            items.add(new PopupPair("Delete [delete]", KeyEvent.VK_DELETE, subTabName));
            items.add(new PopupPair("Copy [c]", KeyEvent.VK_C, subTabName));
            items.add(new PopupPair("Modify [m]", KeyEvent.VK_M, subTabName));
            items.add(new PopupPair("Upcoming [u]", KeyEvent.VK_U, subTabName));
            items.add(new PopupPair("Conflicts [o]", KeyEvent.VK_O, subTabName));
         }
         if (config.rpcEnabled(tivoName) && !subTabName.equals("Season Passes"))
            items.add(new PopupPair("Show Information [i]", KeyEvent.VK_I, subTabName));
         items.add(new PopupPair("Display data [j]", KeyEvent.VK_J, subTabName));
         items.add(new PopupPair("Web query [q]", KeyEvent.VK_Q, subTabName));
      } else {
         // This is a NPL table
         tivoName = tabName;
         if (!config.rpcEnabled(tivoName) && !config.mindEnabled(tivoName))
            items.add(new PopupPair("Get extended metadata [m]", KeyEvent.VK_M, tivoName));
         if (config.rpcEnabled(tivoName) || config.twpDeleteEnabled())
            items.add(new PopupPair("Delete [delete]", KeyEvent.VK_DELETE, tivoName));
         if (config.rpcEnabled(tivoName)) {
            items.add(new PopupPair("Play [space]", KeyEvent.VK_SPACE, tivoName));
            items.add(new PopupPair("Show Information [i]", KeyEvent.VK_I, tivoName));
         }
         items.add(new PopupPair("Display data [j]", KeyEvent.VK_J, tivoName));
         items.add(new PopupPair("Web query [q]", KeyEvent.VK_Q, tivoName));
      }
      
      for (int i=0; i<items.size(); ++i) {
         final int key = items.get(i).key;
         JMenuItem item = new JMenuItem(items.get(i).name);
         final String tableName = items.get(i).tableName;
         item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               // Dispatch key event
               if (tableName.equals("Season Passes")) {
                  TABLE.dispatchEvent(
                        new KeyEvent(
                           TABLE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                           key, KeyEvent.CHAR_UNDEFINED
                        )
                     );                  
               } else {
                  TABLE.dispatchEvent(
                     new KeyEvent(
                        TABLE, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                        key, KeyEvent.CHAR_UNDEFINED
                     )
                  );
               }
            }
         });  
         popup.add(item);
      }
      int row = TABLE.rowAtPoint(e.getPoint());
      TABLE.setRowSelectionInterval(row, row);
      popup.show(e.getComponent(), e.getX(), e.getY());
   }
}
