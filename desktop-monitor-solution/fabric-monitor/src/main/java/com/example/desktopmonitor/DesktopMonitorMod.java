package com.example.desktopmonitor;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesktopMonitorMod implements ModInitializer {
    public static final String MOD_ID = "desktopmonitor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block DESKTOP_MONITOR = new Block(AbstractBlock.Settings.create().strength(1.5F).nonOpaque());
    public static final Item DESKTOP_MONITOR_ITEM = new BlockItem(DESKTOP_MONITOR, new Item.Settings());

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("desktop_monitor"), DESKTOP_MONITOR);
        Registry.register(Registries.ITEM, id("desktop_monitor"), DESKTOP_MONITOR_ITEM);

        ItemGroups.FUNCTIONAL.add(DESKTOP_MONITOR_ITEM);

        LOGGER.info("Desktop Monitor mod initialized");
    }

    public static Identifier id(String value) {
        return Identifier.of(MOD_ID, value);
    }
}
