package com.example.desktopmonitor;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

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
        markDirty();
    }

    public String getHost() {
        return host == null || host.isBlank() ? "127.0.0.1" : host;
    }

    public void setHost(String host) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        markDirty();
    }

    public int getPort() {
        return port <= 0 ? 43122 : port;
    }

    public void setPort(int port) {
        this.port = port <= 0 ? 43122 : port;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("selectedWindow", getSelectedWindow());
        nbt.putString("host", getHost());
        nbt.putInt("port", getPort());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        selectedWindow = nbt.getString("selectedWindow");
        host = nbt.getString("host");
        port = nbt.getInt("port");
    }
}
