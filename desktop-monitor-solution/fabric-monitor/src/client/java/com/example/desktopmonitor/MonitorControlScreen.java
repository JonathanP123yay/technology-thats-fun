package com.example.desktopmonitor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Full-screen interactive view that forwards Minecraft input to the selected desktop window. */
public final class MonitorControlScreen extends Screen {
    private final DesktopMonitorConnection connection;
    private final String windowTitle;
    private int imageLeft;
    private int imageTop;
    private int imageWidth;
    private int imageHeight;

    public MonitorControlScreen(DesktopMonitorConnection connection, String windowTitle) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("Desktop Monitor"));
        this.connection = connection;
        this.windowTitle = windowTitle;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF090B10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int availableWidth = Math.max(1, width - 48);
        int availableHeight = Math.max(1, height - 92);
        double aspect = (double) DesktopMonitorFrame.getWidth() / DesktopMonitorFrame.getHeight();
        imageWidth = availableWidth;
        imageHeight = (int) (imageWidth / aspect);
        if (imageHeight > availableHeight) {
            imageHeight = availableHeight;
            imageWidth = (int) (imageHeight * aspect);
        }
        imageLeft = (width - imageWidth) / 2;
        imageTop = 52 + Math.max(0, (availableHeight - imageHeight) / 2);
        graphics.fill(imageLeft - 3, imageTop - 3, imageLeft + imageWidth + 3, imageTop + imageHeight + 3, 0xFF313844);
        graphics.fill(imageLeft - 1, imageTop - 1, imageLeft + imageWidth + 1, imageTop + imageHeight + 1, 0xFF050608);
        if (DesktopMonitorFrame.isAvailable()) {
            graphics.blit(DesktopMonitorFrame.TEXTURE, imageLeft, imageTop, imageWidth, imageHeight, 0, 0, 1, 1);
        } else {
            graphics.fill(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight, 0xFF222222);
            graphics.text(font, "Waiting for desktop frames...", imageLeft + 12, imageTop + 12, 0xFFFFFFFF);
        }

        graphics.fill(0, 0, width, 44, 0xFF151922);
        graphics.fill(0, 43, width, 44, 0xFF303744);
        graphics.fill(14, 13, 22, 21, connection.isConnected() ? 0xFF63D297 : 0xFFE0A44B);
        graphics.text(font, "DESKTOP CONTROL", 30, 7, 0xFFF2F4F8);
        graphics.text(font, font.plainSubstrByWidth(windowTitle, Math.max(40, width - 190)), 30, 22, 0xFFADB6C4);
        String status = connection.isConnected() ? "LIVE" : "RECONNECTING";
        int badgeWidth = font.width(status) + 18;
        int badgeLeft = width - badgeWidth - 14;
        graphics.fill(badgeLeft, 11, width - 14, 31, connection.isConnected() ? 0xFF20382E : 0xFF403222);
        graphics.text(font, status, badgeLeft + 9, 17, connection.isConnected() ? 0xFF8FE2B1 : 0xFFFFCD83);

        graphics.fill(0, height - 32, width, height, 0xFF151922);
        graphics.fill(0, height - 33, width, height - 32, 0xFF303744);
        graphics.text(font, "Click to interact  ·  Type to send text", 14, height - 21, 0xFFD5DAE2);
        graphics.text(font, "ESC  Close", Math.max(14, width - 130), height - 21, 0xFFADB6C4);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (insideImage(event.x(), event.y())) {
            String button = switch (event.button()) {
                case 1 -> "right";
                case 2 -> "middle";
                default -> "left";
            };
            sendPositioned("click", normalizedX(event.x()), normalizedY(event.y()), ",\"button\":\"" + button + "\"");
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (insideImage(mouseX, mouseY) && deltaY != 0) {
            int delta = (int) Math.round(deltaY * 120);
            sendPositioned("scroll", normalizedX(mouseX), normalizedY(mouseY), ",\"delta\":" + delta);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String text = event.codepointAsString();
        if (!text.isEmpty() && text.codePoints().noneMatch(Character::isISOControl)) {
            connection.sendInput("{\"type\":\"text\",\"text\":\"" + escapeJson(text) + "\"}");
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.keycode() == 256) {
            onClose();
            return true;
        }

        String key = switch (event.keycode()) {
            case 259 -> "BACKSPACE";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 261 -> "DELETE";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 266 -> "PAGEUP";
            case 267 -> "PAGEDOWN";
            case 268 -> "HOME";
            case 269 -> "END";
            default -> null;
        };
        if (key != null) {
            connection.sendInput("{\"type\":\"key\",\"key\":\"" + key + "\"}");
        }
        return true;
    }

    @Override
    public void onClose() {
        connection.disconnect();
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private boolean insideImage(double x, double y) {
        return x >= imageLeft && x < imageLeft + imageWidth && y >= imageTop && y < imageTop + imageHeight;
    }

    private double normalizedX(double x) {
        return (x - imageLeft) / imageWidth;
    }

    private double normalizedY(double y) {
        return (y - imageTop) / imageHeight;
    }

    private void sendPositioned(String type, double x, double y, String extra) {
        connection.sendInput("{\"type\":\"" + type + "\",\"x\":" + x + ",\"y\":" + y + extra + "}");
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format("\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.toString();
    }
}
