package com.example.desktopmonitor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MonitorSettingsScreen extends Screen {
    private static final int WINDOW_CHOICES_PER_PAGE = 4;
    private final BlockPos monitorPos;
    private final MonitorConfig config;
    private final List<String> windowChoices = new ArrayList<>();
    private final List<Button> windowChoiceButtons = new ArrayList<>();
    private EditBox windowField;
    private EditBox hostField;
    private EditBox portField;
    private Button dropdownButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private String discoveryStatus = "Loading open windows...";
    private boolean dropdownOpen;
    private int windowPage;

    public MonitorSettingsScreen(BlockPos monitorPos) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("Monitor Settings"));
        this.monitorPos = monitorPos;
        this.config = MonitorConfig.load();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = 40;

        windowField = new EditBox(font, centerX - 120, startY, 240, 20, Component.literal("Window title"));
        windowField.setValue(config.getSelectedWindow());
        addRenderableWidget(windowField);

        dropdownButton = Button.builder(Component.literal("Choose an open window"), button -> {
            dropdownOpen = !dropdownOpen;
            updateWindowChoiceWidgets();
        }).bounds(centerX - 120, startY + 25, 180, 20).build();
        addRenderableWidget(dropdownButton);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refreshWindowChoices())
            .bounds(centerX + 65, startY + 25, 55, 20)
            .build());

        for (int index = 0; index < WINDOW_CHOICES_PER_PAGE; index++) {
            int choiceIndex = index;
            Button choiceButton = Button.builder(Component.empty(), button -> selectWindowChoice(choiceIndex))
                .bounds(centerX - 120, startY + 50 + index * 20, 240, 20)
                .build();
            choiceButton.visible = false;
            addRenderableWidget(choiceButton);
            windowChoiceButtons.add(choiceButton);
        }

        previousPageButton = Button.builder(Component.literal("<"), button -> changeWindowPage(-1))
            .bounds(centerX - 120, startY + 132, 40, 20)
            .build();
        nextPageButton = Button.builder(Component.literal(">"), button -> changeWindowPage(1))
            .bounds(centerX + 80, startY + 132, 40, 20)
            .build();
        previousPageButton.visible = false;
        nextPageButton.visible = false;
        addRenderableWidget(previousPageButton);
        addRenderableWidget(nextPageButton);

        hostField = new EditBox(font, centerX - 120, startY + 165, 240, 20, Component.literal("Host"));
        hostField.setValue(config.getHost());
        addRenderableWidget(hostField);

        portField = new EditBox(font, centerX - 120, startY + 205, 240, 20, Component.literal("Port"));
        portField.setValue(Integer.toString(config.getPort()));
        addRenderableWidget(portField);

        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applySettings()).bounds(centerX - 120, height - 30, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose()).bounds(centerX + 10, height - 30, 110, 20).build());

        updateWindowChoiceWidgets();
        refreshWindowChoices();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int centerX = width / 2;
        graphics.text(font, "Window title (or select below)", centerX - 120, 22, 0xFFFFFF);
        graphics.text(font, "Host", centerX - 120, 188, 0xFFFFFF);
        graphics.text(font, "Port", centerX - 120, 228, 0xFFFFFF);
        graphics.text(font, discoveryStatus, centerX - 120, 270, 0xAAAAAA);
        graphics.text(font, "Active target: " + windowField.getValue(), centerX - 120, height - 55, 0x7FFF7F);
    }

    private void refreshWindowChoices() {
        discoveryStatus = "Looking for open windows...";
        updateWindowChoiceWidgets();

        WindowsWindowDiscovery.loadWindows(this::setWindowChoices, this::setDiscoveryFailure);
    }

    private void setWindowChoices(List<String> choices) {
        Minecraft.getInstance().execute(() -> {
            windowChoices.clear();
            windowChoices.addAll(choices);
            windowPage = 0;
            discoveryStatus = choices.isEmpty()
                ? "No visible windows found; enter a title above."
                : choices.size() + " open window(s) found.";
            updateWindowChoiceWidgets();
        });
    }

    private void setDiscoveryFailure(IOException exception) {
        Minecraft.getInstance().execute(() -> {
            discoveryStatus = "Window list unavailable; start or refresh the service.";
            System.getLogger(DesktopMonitorMod.MOD_ID).log(
                System.Logger.Level.WARNING,
                "Could not retrieve open windows from the companion service",
                exception
            );
            updateWindowChoiceWidgets();
        });
    }

    private void updateWindowChoiceWidgets() {
        if (dropdownButton == null) {
            return;
        }

        dropdownButton.setMessage(Component.literal(dropdownOpen ? "Hide open windows" : "Choose an open window"));
        int firstChoice = windowPage * WINDOW_CHOICES_PER_PAGE;
        for (int index = 0; index < windowChoiceButtons.size(); index++) {
            int choiceIndex = firstChoice + index;
            Button button = windowChoiceButtons.get(index);
            boolean hasChoice = choiceIndex < windowChoices.size();
            button.visible = dropdownOpen && hasChoice;
            button.active = hasChoice;
            if (hasChoice) {
                button.setMessage(Component.literal(windowChoices.get(choiceIndex)));
            }
        }

        int pageCount = (windowChoices.size() + WINDOW_CHOICES_PER_PAGE - 1) / WINDOW_CHOICES_PER_PAGE;
        previousPageButton.visible = dropdownOpen && pageCount > 1;
        nextPageButton.visible = dropdownOpen && pageCount > 1;
        previousPageButton.active = windowPage > 0;
        nextPageButton.active = windowPage + 1 < pageCount;
    }

    private void selectWindowChoice(int indexOnPage) {
        int choiceIndex = windowPage * WINDOW_CHOICES_PER_PAGE + indexOnPage;
        if (choiceIndex >= 0 && choiceIndex < windowChoices.size()) {
            windowField.setValue(windowChoices.get(choiceIndex));
            dropdownOpen = false;
            updateWindowChoiceWidgets();
        }
    }

    private void changeWindowPage(int direction) {
        int pageCount = (windowChoices.size() + WINDOW_CHOICES_PER_PAGE - 1) / WINDOW_CHOICES_PER_PAGE;
        windowPage = Math.max(0, Math.min(pageCount - 1, windowPage + direction));
        updateWindowChoiceWidgets();
    }

    private void applySettings() {
        config.setSelectedWindow(windowField.getValue());
        config.setHost(hostField.getValue());
        try {
            int port = Integer.parseInt(portField.getValue());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Port outside valid range");
            }
            config.setPort(port);
        } catch (NumberFormatException exception) {
            portField.setFocused(true);
            return;
        }
        config.save();

        if (monitorPos != null
            && Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity blockEntity) {
            blockEntity.setSelectedWindow(config.getSelectedWindow());
            blockEntity.setHost(config.getHost());
            blockEntity.setPort(config.getPort());
        }

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Monitor target set to: " + config.getSelectedWindow()));
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
