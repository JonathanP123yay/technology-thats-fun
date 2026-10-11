package com.example.desktopmonitor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class MonitorSettingsScreen extends Screen {
    private final BlockPos monitorPos;
    private final MonitorConfig config;
    private TextFieldWidget windowField;
    private TextFieldWidget hostField;
    private TextFieldWidget portField;

    public MonitorSettingsScreen(BlockPos monitorPos) {
        super(Text.literal("Monitor Settings"));
        this.monitorPos = monitorPos == null ? BlockPos.ORIGIN : monitorPos;
        this.config = MonitorConfig.load();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = 40;

        windowField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, centerX - 120, startY, 240, 20, Text.literal("Selected window"));
        windowField.setText(config.getSelectedWindow());
        addDrawableChild(windowField);

        hostField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, centerX - 120, startY + 40, 240, 20, Text.literal("Host"));
        hostField.setText(config.getHost());
        addDrawableChild(hostField);

        portField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, centerX - 120, startY + 80, 240, 20, Text.literal("Port"));
        portField.setText(Integer.toString(config.getPort()));
        addDrawableChild(portField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> applySettings()).dimensions(centerX - 120, height - 40, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close()).dimensions(centerX + 10, height - 40, 110, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Selected window"), centerX - 120, 22, 0xFFFFFF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Host"), centerX - 120, 62, 0xFFFFFF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Port"), centerX - 120, 102, 0xFFFFFF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Active target: " + config.getSelectedWindow()), centerX - 120, height - 80, 0x7FFF7F);
    }

    private void applySettings() {
        config.setSelectedWindow(windowField.getText());
        config.setHost(hostField.getText());
        try {
            config.setPort(Integer.parseInt(portField.getText()));
        } catch (NumberFormatException ignored) {
            config.setPort(43122);
        }
        config.save();

        if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().world.getBlockEntity(monitorPos) instanceof MonitorBlockEntity blockEntity) {
            blockEntity.setSelectedWindow(config.getSelectedWindow());
            blockEntity.setHost(config.getHost());
            blockEntity.setPort(config.getPort());
        }

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("Monitor target set to: " + config.getSelectedWindow()), false);
        }
        close();
    }
}
