package com.example.desktopmonitor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class WindowsWindowDiscovery {
    private static final int DISCOVERY_PORT = 43123;
    private static final String REQUEST = "desktop-monitor:list-windows";

    private WindowsWindowDiscovery() {
    }

    public static void loadWindows(Consumer<List<String>> onSuccess, Consumer<IOException> onFailure) {
        Thread worker = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(2000);

                byte[] request = REQUEST.getBytes(StandardCharsets.UTF_8);
                InetAddress loopback = InetAddress.getLoopbackAddress();
                socket.send(new DatagramPacket(request, request.length, new InetSocketAddress(loopback, DISCOVERY_PORT)));

                byte[] response = new byte[65_507];
                DatagramPacket packet = new DatagramPacket(response, response.length);
                socket.receive(packet);

                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                List<String> windows = Arrays.stream(payload.split("\\R"))
                    .map(String::strip)
                    .filter(title -> !title.isEmpty())
                    .distinct()
                    .toList();
                onSuccess.accept(windows);
            } catch (IOException exception) {
                onFailure.accept(exception);
            }
        }, "desktop-monitor-window-discovery");
        worker.setDaemon(true);
        worker.start();
    }
}
