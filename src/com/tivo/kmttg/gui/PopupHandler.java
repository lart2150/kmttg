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
      if (tabName.equals("FILES"))
         return;
      JPopupMenu popup = new JPopupMenu();
      Stack<PopupPair> items = new Stack<PopupPair>();
      if (tabName.equals("Remote")) {
         // This is a Remote table
         String subTabName = config.gui.remote_gui.getCurrentTabName();
         String tivoName = config.gui.remote_gui.getTivoName(subTabName);
         if (config.rpcEnabled(tivoName))
            items.add(new PopupPair("Show Information", KeyEvent.VK_I));
         items.add(new PopupPair("Display data", KeyEvent.VK_J));
         items.add(new PopupPair("Web query", KeyEvent.VK_Q));
      } else {
         // This is a NPL table
         items.add(new PopupPair("Get extended metadata", KeyEvent.VK_M));
         items.add(new PopupPair("Delete", KeyEvent.VK_DELETE));
         items.add(new PopupPair("Play", KeyEvent.VK_SPACE));
         if (config.rpcEnabled(tabName))
            items.add(new PopupPair("Show Information", KeyEvent.VK_I));
         items.add(new PopupPair("Display data", KeyEvent.VK_J));
         items.add(new PopupPair("Web query", KeyEvent.VK_Q));
      }
      
      for (int i=0; i<items.size(); ++i) {
         final int key = items.get(i).key;
         JMenuItem item = new JMenuItem(items.get(i).name);
         item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               // Dispatch key event
               TABLE.dispatchEvent(
                  new KeyEvent(
                     TABLE, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                     key, KeyEvent.CHAR_UNDEFINED
                  )
               );                
            }
         });  
         popup.add(item);
      }
      int row = TABLE.rowAtPoint(e.getPoint());
      TABLE.setRowSelectionInterval(row, row);
      popup.show(e.getComponent(), e.getX(), e.getY());
   }
}
