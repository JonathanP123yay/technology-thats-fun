package com.example.desktopmonitor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class DesktopMonitorClient implements ClientModInitializer {
    private final DesktopMonitorConnection connection = new DesktopMonitorConnection();

    private final KeyBinding toggleDesktop = KeyBindingHelper.registerKeyBinding(
        new KeyBinding(
            "key.desktopmonitor.toggle",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            "key.category.desktopmonitor"
        )
    );

    private final KeyBinding openSettings = KeyBindingHelper.registerKeyBinding(
        new KeyBinding(
            "key.desktopmonitor.settings",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            "key.category.desktopmonitor"
        )
    );

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleDesktop.wasPressed()) {
                if (connection.isConnected()) {
                    connection.disconnect();
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Desktop control disabled."), true);
                    }
                } else {
                    MonitorConfig config = MonitorConfig.load();
                    connection.connect(config.getHost(), config.getPort(), "desktop-monitor-demo");
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Desktop control enabled for window: " + config.getSelectedWindow()), true);
                    }
                }
            }

            while (openSettings.wasPressed()) {
                client.setScreen(new MonitorSettingsScreen(null));
            }
        });
    }
}
