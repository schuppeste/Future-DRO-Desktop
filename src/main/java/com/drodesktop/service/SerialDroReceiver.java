package com.drodesktop.service;

import com.drodesktop.model.Vector3;
import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class SerialDroReceiver implements AutoCloseable {
    private SerialPort port;
    private Thread receiverThread;
    private volatile boolean receiving;

    // Parser state carried across read() chunks: which axis letter we're currently reading digits for.
    private char pendingAxis;
    private final StringBuilder pendingNumber = new StringBuilder();
    private double lastX;
    private double lastY;
    private double lastZ;

    public static String[] availablePortNames() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int index = 0; index < ports.length; index++) {
            names[index] = ports[index].getSystemPortName();
        }
        return names;
    }

    public synchronized void connect(String portName, Consumer<Vector3> telemetryHandler) throws IOException {
        close();
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(115200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);
        if (!port.openPort()) {
            throw new IOException("Serial port could not be opened: " + portName);
        }

        pendingAxis = 0;
        pendingNumber.setLength(0);
        receiving = true;
        receiverThread = new Thread(() -> receiveLoop(telemetryHandler), "dro-serial-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public synchronized boolean isConnected() {
        return port != null && port.isOpen();
    }

    @Override
    public synchronized void close() {
        receiving = false;
        if (port != null) {
            port.closePort();
            port = null;
        }
        receiverThread = null;
    }

    private void receiveLoop(Consumer<Vector3> telemetryHandler) {
        try (InputStream input = port.getInputStream()) {
            byte[] buffer = new byte[256];
            while (receiving) {
                int count;
                try {
                    count = input.read(buffer);
                } catch (IOException readTimeout) {
                    // Semi-blocking reads throw when no data arrives within the timeout window instead of
                    // returning 0; that's expected during idle gaps, so just retry instead of killing the thread.
                    continue;
                }
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    String chunk = new String(buffer, 0, count, StandardCharsets.US_ASCII);
                    System.out.print("[serial-raw] ");
                    System.out.println(chunk);
                    parseChunk(chunk, telemetryHandler);
                }
            }
        } catch (IOException ignored) {
            // Closing the serial port interrupts the blocking read.
        }
    }

    // Scans char by char for "x123;" / "y-45;" / "z0;" fields, skipping anything else (e.g. "v:TouchDRO...;" banners)
    // without needing a regex. The display is only refreshed when a value actually changed.
    private void parseChunk(String chunk, Consumer<Vector3> telemetryHandler) {
        boolean changed = false;
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (pendingAxis == 0) {
                char lower = Character.toLowerCase(c);
                if (lower == 'x' || lower == 'y' || lower == 'z' || lower == 'w') {
                    pendingAxis = lower;
                    pendingNumber.setLength(0);
                }
                continue;
            }
            if (c == '-' && pendingNumber.length() == 0) {
                pendingNumber.append(c);
            } else if (c >= '0' && c <= '9') {
                pendingNumber.append(c);
            } else if (c == ';') {
                if (pendingNumber.length() > 0 && !(pendingNumber.length() == 1 && pendingNumber.charAt(0) == '-')) {
                    double value = Long.parseLong(pendingNumber.toString()) / 1000.0;
                    switch (pendingAxis) {
                        case 'x' -> {
                            if (value != lastX) {
                                lastX = value;
                                changed = true;
                            }
                        }
                        case 'y' -> {
                            if (value != lastY) {
                                lastY = value;
                                changed = true;
                            }
                        }
                        case 'z' -> {
                            if (value != lastZ) {
                                lastZ = value;
                                changed = true;
                            }
                        }
                        default -> { }
                    }
                }
                pendingAxis = 0;
                pendingNumber.setLength(0);
            } else {
                // Not a valid digit for the field we thought we were reading (e.g. stray banner text) - abandon it
                // and re-examine this same character as a possible new axis start.
                pendingAxis = 0;
                pendingNumber.setLength(0);
                i--;
            }
        }
        if (changed) {
            telemetryHandler.accept(new Vector3(lastX, lastY, lastZ));
        }
    }
}
