package com.example.desktopmonitor;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class WindowsServiceLauncher {
    private static final System.Logger LOGGER = System.getLogger(DesktopMonitorMod.MOD_ID);
    private static final int SERVICE_PORT = 43122;
    private static final int WINDOW_DISCOVERY_PORT = 43123;
    private static final byte[] WINDOW_DISCOVERY_REQUEST = "desktop-monitor:list-windows".getBytes(StandardCharsets.UTF_8);

    private WindowsServiceLauncher() {
    }

    public static void start() {
        Thread launcher = new Thread(WindowsServiceLauncher::startService, "desktop-monitor-service-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    private static void startService() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            LOGGER.log(System.Logger.Level.INFO, "Embedded Windows service will not start on this operating system");
            return;
        }
        if (!System.getProperty("os.arch", "").equalsIgnoreCase("amd64")) {
            LOGGER.log(System.Logger.Level.ERROR, "The bundled Windows service requires a 64-bit x86 Windows system");
            return;
        }

        if (isServiceAlreadyListening()) {
            if (isWindowDiscoveryAvailable()) {
                LOGGER.log(System.Logger.Level.INFO, "Desktop Monitor Windows service is already listening on port {0}", SERVICE_PORT);
                return;
            }

            LOGGER.log(
                System.Logger.Level.WARNING,
                "A Windows service is listening on port {0} without window discovery; starting the bundled discovery listener alongside it",
                SERVICE_PORT
            );
        }

        try {
            Path runtimeDirectory = FabricLoader.getInstance().getConfigDir().resolve("desktopmonitor");
            Files.createDirectories(runtimeDirectory);

            Path executable = runtimeDirectory.resolve("DesktopMonitorWindowsService-" + UUID.randomUUID() + ".exe");
            try (InputStream bundledExecutable = WindowsServiceLauncher.class.getResourceAsStream(
                "/META-INF/desktopmonitor/DesktopMonitorWindowsService.exe"
            )) {
                if (bundledExecutable == null) {
                    throw new IOException("The bundled Windows service executable is missing from the mod JAR");
                }
                Files.copy(bundledExecutable, executable);
            }

            Path logFile = runtimeDirectory.resolve("windows-service.log");
            Process service = new ProcessBuilder(executable.toAbsolutePath().toString())
                .directory(runtimeDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();

            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(service, executable), "desktop-monitor-service-shutdown"));
            } catch (IllegalStateException exception) {
                stop(service, executable);
                return;
            }
            LOGGER.log(System.Logger.Level.INFO, "Started bundled Windows service (PID {0})", service.pid());
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not extract or start the bundled Windows service", exception);
        }
    }

    private static boolean isServiceAlreadyListening() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", SERVICE_PORT), 300);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isWindowDiscoveryAvailable() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(300);
            InetAddress loopback = InetAddress.getLoopbackAddress();
            socket.send(new DatagramPacket(
                WINDOW_DISCOVERY_REQUEST,
                WINDOW_DISCOVERY_REQUEST.length,
                new InetSocketAddress(loopback, WINDOW_DISCOVERY_PORT)
            ));
            byte[] response = new byte[1];
            socket.receive(new DatagramPacket(response, response.length));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void stop(Process service, Path executable) {
        service.destroy();
        try {
            if (!service.waitFor(2, TimeUnit.SECONDS)) {
                service.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(executable);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "Interrupted while stopping the bundled Windows service", exception);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not remove the extracted Windows service executable", exception);
        }
    }
}
