package com.tivo.kmttg.gui;

import javafx.scene.paint.Color;
import javax.swing.JOptionPane;


public class JavaFX {
	public static final String message = "KMTTG requires JavaFX.  Please use either the oracle jre or a opendjk with JavaFX like Zulu with ZuluFX";
	
	public static boolean checkForJavaFX()
	{
		try {
			Color c  = javafx.scene.paint.Color.BLUE;
		} catch (NoClassDefFoundError e) {
			JOptionPane.showMessageDialog(null, message, "Missing JavaFX", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}
}
