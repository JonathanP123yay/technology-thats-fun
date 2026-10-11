package com.example.desktopmonitor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class DesktopMonitorConnection {
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile Socket socket;

    public boolean isConnected() {
        return connected.get();
    }

    public void connect(String host, int port, String token) {
        disconnect();

        try {
            Socket activeSocket = new Socket(host, port);
            socket = activeSocket;
            activeSocket.setTcpNoDelay(true);

            MonitorConfig config = MonitorConfig.load();
            DataOutputStream out = new DataOutputStream(activeSocket.getOutputStream());
            out.write((token + "\n").getBytes(StandardCharsets.UTF_8));
            out.write((config.getSelectedWindow() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            connected.set(true);
            System.getLogger(DesktopMonitorMod.MOD_ID).log(System.Logger.Level.INFO, "Connected to Windows desktop service at {0}:{1}", host, port);

            Thread reader = new Thread(() -> {
                try (DataInputStream in = new DataInputStream(activeSocket.getInputStream())) {
                    while (connected.get()) {
                        int length = in.readInt();
                        if (length <= 0 || length > 10_000_000) {
                            break;
                        }
                        byte[] payload = new byte[length];
                        int read = 0;
                        while (read < length) {
                            int count = in.read(payload, read, length - read);
                            if (count < 0) {
                                throw new IOException("stream ended");
                            }
                            read += count;
                        }
                        DesktopMonitorFrame.setJpeg(payload);
                    }
                } catch (IOException e) {
                    System.getLogger(DesktopMonitorMod.MOD_ID).log(System.Logger.Level.WARNING, "Desktop connection interrupted", e);
                } finally {
                    disconnect(activeSocket);
                }
            }, "desktop-monitor-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            System.getLogger(DesktopMonitorMod.MOD_ID).log(System.Logger.Level.WARNING, "Could not connect to Windows service", e);
            disconnect();
        }
    }

    public void disconnect() {
        connected.set(false);
        Socket current = socket;
        socket = null;
        close(current);
    }

    private void disconnect(Socket expected) {
        if (socket == expected) {
            connected.set(false);
            socket = null;
        }
        close(expected);
    }

    private static void close(Socket target) {
        if (target != null) {
            try {
                target.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void sendInput(String json) {
        if (!connected.get() || socket == null) {
            return;
        }

        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        } catch (IOException ignored) {
            disconnect();
        }
    }
}
