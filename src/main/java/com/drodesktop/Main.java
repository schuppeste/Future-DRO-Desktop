package com.drodesktop;

import com.drodesktop.i18n.Messages;
import com.drodesktop.ui.DROFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Messages.setLanguage(findLanguageArgument(args));
        int integerDigitCount = findIntegerDigitCountArgument(args);
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            ex.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, ex.toString(), "Unerwarteter Fehler",
                javax.swing.JOptionPane.ERROR_MESSAGE);
        });
        SwingUtilities.invokeLater(() -> {
            DROFrame frame = new DROFrame(integerDigitCount);
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

    private static int findIntegerDigitCountArgument(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            String value = null;
            if (arg.regionMatches(true, 0, "--integer-digits=", 0, 17)) {
                value = arg.substring(17);
            } else if (("--integer-digits".equalsIgnoreCase(arg) || "-integer-digits".equalsIgnoreCase(arg))
                    && index + 1 < args.length) {
                value = args[index + 1];
            }
            if ("3".equals(value) || "4".equals(value)) {
                return Integer.parseInt(value);
            }
        }
        return 3;
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
