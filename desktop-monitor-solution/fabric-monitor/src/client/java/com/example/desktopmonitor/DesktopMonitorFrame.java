package com.example.desktopmonitor;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Holds the most recent desktop frame and uploads it to the client texture manager. */
public final class DesktopMonitorFrame {
    public static final Identifier TEXTURE = DesktopMonitorMod.id("dynamic/desktop_frame");
    private static final AtomicReference<byte[]> PENDING_FRAME = new AtomicReference<>();
    private static volatile boolean available;
    private static volatile int frameWidth = 16;
    private static volatile int frameHeight = 9;
    private static int tickCounter;

    private DesktopMonitorFrame() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter % 3 != 0) {
                return;
            }

            byte[] jpeg = PENDING_FRAME.getAndSet(null);
            if (jpeg == null) {
                return;
            }

            try {
                NativeImage image = NativeImage.read(jpeg);
                frameWidth = image.getWidth();
                frameHeight = image.getHeight();
                client.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Desktop Monitor frame", image));
                available = true;
            } catch (IOException | RuntimeException exception) {
                System.getLogger(DesktopMonitorMod.MOD_ID).log(System.Logger.Level.WARNING, "Could not decode desktop frame", exception);
            }
        });
    }

    public static void setJpeg(byte[] jpeg) {
        PENDING_FRAME.set(jpeg);
    }

    public static boolean isAvailable() {
        return available;
    }

    public static int getWidth() {
        return frameWidth;
    }

    public static int getHeight() {
        return frameHeight;
    }
}
