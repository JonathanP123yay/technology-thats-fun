package com.example.desktopmonitor;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public class DesktopMonitorMod implements ModInitializer {
    public static final String MOD_ID = "desktopmonitor";
    private static final System.Logger LOGGER = System.getLogger(MOD_ID);
    public static final MonitorConfig CONFIG = MonitorConfig.load();

    private static final ResourceKey<Block> MONITOR_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("monitor"));
    private static final ResourceKey<Item> MONITOR_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("monitor"));
    private static final ResourceKey<Block> KEYBOARD_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("keyboard"));
    private static final ResourceKey<Item> KEYBOARD_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("keyboard"));
    private static final ResourceKey<Block> MOUSE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, id("mouse"));
    private static final ResourceKey<Item> MOUSE_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("mouse"));
    private static final ResourceKey<BlockEntityType<?>> MONITOR_BLOCK_ENTITY_KEY =
        ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("monitor_block_entity"));
    public static final Block MONITOR_BLOCK = new MonitorBlock(
        BlockBehaviour.Properties.of().strength(1.5F).noOcclusion().setId(MONITOR_BLOCK_KEY)
    );
    public static final Item MONITOR_ITEM = new BlockItem(MONITOR_BLOCK, new Item.Properties().setId(MONITOR_ITEM_KEY));
    public static final Block KEYBOARD_BLOCK = new InputDeviceBlock(BlockBehaviour.Properties.of().strength(1.0F).noOcclusion().setId(KEYBOARD_BLOCK_KEY));
    public static final Item KEYBOARD_ITEM = new BlockItem(KEYBOARD_BLOCK, new Item.Properties().setId(KEYBOARD_ITEM_KEY));
    public static final Block MOUSE_BLOCK = new InputDeviceBlock(BlockBehaviour.Properties.of().strength(1.0F).noOcclusion().setId(MOUSE_BLOCK_KEY));
    public static final Item MOUSE_ITEM = new BlockItem(MOUSE_BLOCK, new Item.Properties().setId(MOUSE_ITEM_KEY));
    public static final BlockEntityType<MonitorBlockEntity> MONITOR_BLOCK_ENTITY =
        new BlockEntityType<>(MonitorBlockEntity::new, Set.of(MONITOR_BLOCK));
    public static final CreativeModeTab TECHNOLOGY_TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.translatable("itemGroup.desktopmonitor.technology"))
        .icon(() -> new ItemStack(MONITOR_ITEM))
        .displayItems((parameters, output) -> {
            output.accept(MONITOR_ITEM);
            output.accept(KEYBOARD_ITEM);
            output.accept(MOUSE_ITEM);
        })
        .build();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, id("monitor"), MONITOR_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, id("monitor"), MONITOR_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, id("keyboard"), KEYBOARD_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, id("keyboard"), KEYBOARD_ITEM);
        Registry.register(BuiltInRegistries.BLOCK, id("mouse"), MOUSE_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, id("mouse"), MOUSE_ITEM);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("monitor_block_entity"), MONITOR_BLOCK_ENTITY);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("technology"), TECHNOLOGY_TAB);

        LOGGER.log(System.Logger.Level.INFO, "Monitor block initialized with target window ''{0}''", CONFIG.getSelectedWindow());
    }

    public static Identifier id(String value) {
        return Identifier.fromNamespaceAndPath(MOD_ID, value);
    }
}
