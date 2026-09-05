package com.drodesktop;

import com.drodesktop.i18n.Messages;
import com.drodesktop.ui.DROFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Messages.setLanguage(findLanguageArgument(args));
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            ex.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, ex.toString(), "Unerwarteter Fehler",
                javax.swing.JOptionPane.ERROR_MESSAGE);
        });
        SwingUtilities.invokeLater(() -> {
            DROFrame frame = new DROFrame();
            boolean fullscreen = hasFullscreenArgument(args);
            if (fullscreen) {
                frame.prepareFullscreen();
            }
            frame.setVisible(true);
            if (fullscreen) {
                frame.startFullscreen();
            }
            String portArgument = findPortArgument(args);
            if (portArgument != null) {
                frame.connectToSerialPort(portArgument);
            } else {
                frame.connectFirstAvailableSerialPort();
            }
        });
    }

    private static String findLanguageArgument(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.regionMatches(true, 0, "--lang=", 0, 7)) {
                return arg.substring(7);
            }
            if (("--lang".equalsIgnoreCase(arg) || "-lang".equalsIgnoreCase(arg)) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return "de";
    }

    private static String findPortArgument(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.regionMatches(true, 0, "--port=", 0, 7)) {
                return arg.substring(7);
            }
            if (("--port".equalsIgnoreCase(arg) || "-port".equalsIgnoreCase(arg)) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return null;
    }

    private static boolean hasFullscreenArgument(String[] args) {
        for (String arg : args) {
            if ("--fullscreen".equalsIgnoreCase(arg) || "fullscreen".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
