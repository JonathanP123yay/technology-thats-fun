package com.example.desktopmonitor;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MonitorConfig {
    public static final String DEFAULT_WINDOW = "Notepad";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("desktop-monitor.properties");

    private String selectedWindow = DEFAULT_WINDOW;
    private String host = "127.0.0.1";
    private int port = 43122;
    private boolean enabled = false;

    public static MonitorConfig load() {
        MonitorConfig config = new MonitorConfig();
        if (!Files.exists(CONFIG_PATH)) {
            return config;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException ignored) {
            return config;
        }

        config.selectedWindow = properties.getProperty("selectedWindow", DEFAULT_WINDOW);
        config.host = properties.getProperty("host", "127.0.0.1");
        config.port = Integer.parseInt(properties.getProperty("port", "43122"));
        config.enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
        return config;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("selectedWindow", selectedWindow);
        properties.setProperty("host", host);
        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("enabled", Boolean.toString(enabled));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, "Monitor settings");
            }
        } catch (IOException e) {
            DesktopMonitorMod.LOGGER.warn("Could not persist monitor settings: {}", e.getMessage());
        }
    }

    public String getSelectedWindow() {
        return selectedWindow == null || selectedWindow.isBlank() ? DEFAULT_WINDOW : selectedWindow;
    }

    public void setSelectedWindow(String selectedWindow) {
        this.selectedWindow = selectedWindow == null || selectedWindow.isBlank() ? DEFAULT_WINDOW : selectedWindow;
    }

    public String getHost() {
        return host == null || host.isBlank() ? "127.0.0.1" : host;
    }

    public void setHost(String host) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
    }

    public int getPort() {
        return port <= 0 ? 43122 : port;
    }

    public void setPort(int port) {
        this.port = port <= 0 ? 43122 : port;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
