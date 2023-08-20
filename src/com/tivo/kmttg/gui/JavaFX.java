package com.tivo.kmttg.gui;

import javafx.scene.paint.Color;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import com.tivo.kmttg.install.update;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class JavaFX {
	public static final String message = "KMTTG requires JavaFX.  Please use either the oracle jre 8 or extract the javafx sdk in javafx-sdk folder";

	public static boolean checkForJavaFX() {
		try {
			@SuppressWarnings("unused")
			Color c = javafx.scene.paint.Color.BLUE;
		} catch (NoClassDefFoundError e) {
			try {
				final File currentJar = new File(
						JavaFX.class.getProtectionDomain().getCodeSource().getLocation().toURI());
				String javaFXPath = findJavaFXLib(currentJar);
				if (javaFXPath != null) {
					restartWithJavaFx(currentJar, javaFXPath);
				} else {
					int answer = JOptionPane.showConfirmDialog(null, "You are missing javfx.  Would you like to download it now?", "Missing JavaFX", JOptionPane.YES_NO_OPTION);
					if (answer == JOptionPane.YES_OPTION) {
						downloadJavaFX(currentJar);
					} else {
						return false;
					}
				}
			} catch (Exception e1) {
				log.error(e1.getMessage());
			}

			JOptionPane.showMessageDialog(null, message, "Missing JavaFX", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}
	
	private static boolean downloadJavaFX(File currentJar) throws IOException {
		String arch = System.getProperty("os.arch");
		String os = System.getProperty("os.name").toLowerCase();
		String downloadOs = "";
		if (os.contains("windows")) {
			downloadOs = "windows";
		} else if (os.contains("mac")) {
			downloadOs = "osx";
		} else if (os.contains("linux")) {
			downloadOs = "linux";
		} else {
			return false;
		}
		
		switch (arch) {
			case "amd64":
			case "x86_64":
				arch = "x64";
				break;
		}
		String zipName = currentJar.getParent() + "/openfx-sdk.zip";
		String downloadLink = "";
		if (getJreMajorVersion() < 17) {
		   downloadLink = "https://download2.gluonhq.com/openjfx/19.0.2.1/openjfx-19.0.2.1_" +downloadOs+ "-" + arch + "_bin-sdk.zip";
		} else {
		   //https://download2.gluonhq.com/openjfx/20.0.2/openjfx-20.0.2_windows-x64_bin-sdk.zip
		   downloadLink = "https://download2.gluonhq.com/openjfx/20.0.2/openjfx-20.0.2_" +downloadOs+ "-" + arch + "_bin-sdk.zip";
		}
		String zipFile = update.downloadUrl(zipName, downloadLink);
		if (zipFile != null) {
			File sdkDir = new File(currentJar.getParent() + "/javafx-sdk/");
			if (!sdkDir.exists()) {
				sdkDir.mkdir();
			}
            if ( update.unzip(sdkDir.toString(), zipFile) ) {
               log.print("Successfully download javafx.");
               file.delete(zipFile);
               String javaFXPath = findJavaFXLib(currentJar);
               restartWithJavaFx(currentJar, javaFXPath);
            } else {
               log.error("Trouble unzipping file: " + zipFile);
            }
         } else {
        	 JOptionPane.showMessageDialog(null, "We failed to download javafx.", "JavaFX Download Failed", JOptionPane.ERROR_MESSAGE);
         }
		
		return false;
	}
	
	private static String findJavaFXLib(File currentJar) {
		File javaFXpath = new File(currentJar.getParent() + "/javafx-sdk/");
		if (!javaFXpath.exists()) {
			System.out.println("Javafx folder is not there: " + javaFXpath.toString());
			return null;
		}
		
		File[] listOfFiles = javaFXpath.listFiles();
		for (File file : listOfFiles) {
			System.out.println(file.getName());
			if (file.getName().equals("lib")) {
				return file.toString();
			}
			if (file.getName().contains("javafx")) {
				File[] fxFiles = file.listFiles();
				for (File fxfile : fxFiles) {
					System.out.println(fxfile.getName());
					if (fxfile.getName().equals("lib")) {
						return fxfile.toString();
					}
				}
			}
		}
		return null;
	}
	
	private static void restartWithJavaFx(File currentJar, String javaFXPath) throws IOException {
		
		
		final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator
				+ "java";

		/* Build command: java -jar application.jar */
		final ArrayList<String> command = new ArrayList<String>();
		command.add(javaBin);
		command.add("--module-path");
		command.add(javaFXPath);
		command.add("--add-modules");
		command.add("javafx.web");
		command.add("-jar");
		command.add(currentJar.getPath());

		final ProcessBuilder builder = new ProcessBuilder(command);
		System.out.println("restarting with javafx");
		builder.start();
		System.exit(0);
	}
	
	private static int getJreMajorVersion() {
	   //Runtime.version().feature();//this would be ideal but it requires JRE 10 :(
	    String version = System.getProperty("java.version");
	    if(version.startsWith("1.")) {
	        version = version.substring(2, 3);
	    } else {
	        int dot = version.indexOf(".");
	        if(dot != -1) {
	           version = version.substring(0, dot);
           }
	    }
	    return Integer.parseInt(version);
	}
}
