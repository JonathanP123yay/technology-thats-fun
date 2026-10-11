package com.example.desktopmonitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MonitorBlockEntity extends BlockEntity {
    private String selectedWindow = MonitorConfig.DEFAULT_WINDOW;
    private String host = "127.0.0.1";
    private int port = 43122;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(DesktopMonitorMod.MONITOR_BLOCK_ENTITY, pos, state);
    }

    public String getSelectedWindow() {
        return selectedWindow == null || selectedWindow.isBlank() ? MonitorConfig.DEFAULT_WINDOW : selectedWindow;
    }

    public void setSelectedWindow(String selectedWindow) {
        this.selectedWindow = selectedWindow == null || selectedWindow.isBlank() ? MonitorConfig.DEFAULT_WINDOW : selectedWindow;
        setChanged();
    }

    public String getHost() {
        return host == null || host.isBlank() ? "127.0.0.1" : host;
    }

    public void setHost(String host) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        setChanged();
    }

    public int getPort() {
        return port <= 0 ? 43122 : port;
    }

    public void setPort(int port) {
        this.port = port <= 0 ? 43122 : port;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("selectedWindow", getSelectedWindow());
        output.putString("host", getHost());
        output.putInt("port", getPort());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        selectedWindow = input.getStringOr("selectedWindow", MonitorConfig.DEFAULT_WINDOW);
        host = input.getStringOr("host", "127.0.0.1");
        port = input.getIntOr("port", 43122);
    }
}
