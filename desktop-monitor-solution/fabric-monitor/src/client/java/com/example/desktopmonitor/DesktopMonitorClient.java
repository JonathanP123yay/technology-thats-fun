package com.example.desktopmonitor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.InteractionResult;

public class DesktopMonitorClient implements ClientModInitializer {
    private final DesktopMonitorConnection connection = new DesktopMonitorConnection();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DesktopMonitorMod.MOD_ID, "desktop_monitor"));

    private final KeyMapping toggleDesktop = KeyMappingHelper.registerKeyMapping(
        new KeyMapping(
            "key.desktopmonitor.toggle",
            InputConstants.Type.KEYBOARD,
            0,
            CATEGORY
        )
    );

    private final KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
        new KeyMapping(
            "key.desktopmonitor.settings",
            InputConstants.Type.KEYBOARD,
            79,
            CATEGORY
        )
    );

    @Override
    public void onInitializeClient() {
        WindowsServiceLauncher.start();

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()
                && !player.isShiftKeyDown()
                && world.getBlockState(hit.getBlockPos()).is(DesktopMonitorMod.MONITOR_BLOCK)) {
                Minecraft.getInstance().setScreenAndShow(new MonitorSettingsScreen(hit.getBlockPos()));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleDesktop.consumeClick()) {
                if (connection.isConnected()) {
                    connection.disconnect();
                    if (client.player != null) {
                        client.player.sendOverlayMessage(Component.literal("Desktop control disabled."));
                    }
                } else {
                    MonitorConfig config = MonitorConfig.load();
                    connection.connect(config.getHost(), config.getPort(), "desktop-monitor-demo");
                    if (client.player != null) {
                        client.player.sendOverlayMessage(Component.literal("Desktop control enabled for window: " + config.getSelectedWindow()));
                    }
                }
            }

            while (openSettings.consumeClick()) {
                client.setScreenAndShow(new MonitorSettingsScreen(null));
            }
        });
    }
}
