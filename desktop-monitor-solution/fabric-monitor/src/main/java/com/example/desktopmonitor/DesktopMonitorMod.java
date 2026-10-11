package com.example.desktopmonitor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesktopMonitorMod implements ModInitializer {
    public static final String MOD_ID = "desktopmonitor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MonitorConfig CONFIG = MonitorConfig.load();

    public static final Block MONITOR_BLOCK = new MonitorBlock(AbstractBlock.Settings.create().strength(1.5F).nonOpaque());
    public static final Item MONITOR_ITEM = new BlockItem(MONITOR_BLOCK, new Item.Settings());
    public static final BlockEntityType<MonitorBlockEntity> MONITOR_BLOCK_ENTITY = FabricBlockEntityTypeBuilder.create(
        MonitorBlockEntity::new,
        MONITOR_BLOCK
    ).build();

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("monitor"), MONITOR_BLOCK);
        Registry.register(Registries.ITEM, id("monitor"), MONITOR_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("monitor_block_entity"), MONITOR_BLOCK_ENTITY);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(MONITOR_ITEM));

        LOGGER.info("Monitor block initialized with target window '{}'", CONFIG.getSelectedWindow());
    }

    public static Identifier id(String value) {
        return Identifier.of(MOD_ID, value);
    }
}
