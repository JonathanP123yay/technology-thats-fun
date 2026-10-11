package com.example.desktopmonitor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

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
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);

            MonitorConfig config = MonitorConfig.load();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(token);
            out.writeUTF(config.getSelectedWindow());
            out.flush();

            connected.set(true);
            DesktopMonitorMod.LOGGER.info("Connected to Windows desktop service at {}:{}", host, port);

            Thread reader = new Thread(() -> {
                try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
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
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("Received desktop frame: " + payload.length + " bytes"), true);
                            }
                        });
                    }
                } catch (IOException e) {
                    DesktopMonitorMod.LOGGER.warn("Desktop connection interrupted: {}", e.getMessage());
                } finally {
                    disconnect();
                }
            }, "desktop-monitor-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            DesktopMonitorMod.LOGGER.warn("Could not connect to Windows service: {}", e.getMessage());
            disconnect();
        }
    }

    public void disconnect() {
        connected.set(false);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
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
