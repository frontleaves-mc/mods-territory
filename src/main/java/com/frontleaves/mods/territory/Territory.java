package com.frontleaves.mods.territory;

import com.frontleaves.mods.territory.block.AdminTerritoryTableBlock;
import com.frontleaves.mods.territory.block.TerritoryTableBlock;
import com.frontleaves.mods.territory.block.entity.TerritoryTableBlockEntity;
import com.frontleaves.mods.territory.command.TerritoryCommand;
import com.frontleaves.mods.territory.data.ModDataGen;
import com.frontleaves.mods.territory.item.AdminTerritoryWandItem;
import com.frontleaves.mods.territory.item.TerritoryTableBlockItem;
import com.frontleaves.mods.territory.item.TerritoryBookItem;
import com.frontleaves.mods.territory.item.TerritoryWandItem;
import com.frontleaves.mods.territory.network.TerritoryPayloads;
import com.frontleaves.mods.territory.config.TerritoryConfig;
import com.frontleaves.mods.territory.storage.ServerSelectionCache;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Mod(Territory.MODID)
public class Territory {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LevelResource TERRITORY_DATA = new LevelResource("territory");

    public static final String MODID = "territory";

    // -- Registers --
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // -- Items --
    public static final DeferredItem<TerritoryWandItem> TERRITORY_WAND = ITEMS.register(
            "territory_wand",
            () -> new TerritoryWandItem(new net.minecraft.world.item.Item.Properties())
    );

    public static final DeferredItem<AdminTerritoryWandItem> ADMIN_TERRITORY_WAND = ITEMS.register(
            "admin_territory_wand",
            () -> new AdminTerritoryWandItem(new net.minecraft.world.item.Item.Properties())
    );

    public static final DeferredItem<TerritoryBookItem> TERRITORY_BOOK = ITEMS.register(
            "territory_book",
            () -> new TerritoryBookItem(new net.minecraft.world.item.Item.Properties())
    );

    // -- Blocks --
    public static final DeferredBlock<TerritoryTableBlock> TERRITORY_TABLE = BLOCKS.register(
            "territory_table",
            () -> new TerritoryTableBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .pushReaction(PushReaction.BLOCK))
    );

    public static final DeferredBlock<AdminTerritoryTableBlock> ADMIN_TERRITORY_TABLE = BLOCKS.register(
            "admin_territory_table",
            () -> new AdminTerritoryTableBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .pushReaction(PushReaction.BLOCK))
    );

    // -- Block Items (required for crafting recipes) --
    public static final DeferredItem<net.minecraft.world.item.BlockItem> TERRITORY_TABLE_ITEM = ITEMS.register(
            "territory_table",
            () -> new TerritoryTableBlockItem(TERRITORY_TABLE.get(), new net.minecraft.world.item.Item.Properties())
    );

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ADMIN_TERRITORY_TABLE_ITEM = ITEMS.register(
            "admin_territory_table",
            () -> new TerritoryTableBlockItem(ADMIN_TERRITORY_TABLE.get(), new net.minecraft.world.item.Item.Properties())
    );

    // -- Block Entities --
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerritoryTableBlockEntity>> TERRITORY_TABLE_ENTITY = BLOCK_ENTITIES.register(
            "territory_table",
            () -> BlockEntityType.Builder.of(TerritoryTableBlockEntity::new, TERRITORY_TABLE.get(), ADMIN_TERRITORY_TABLE.get()).build(null)
    );

    // -- Creative Tab --
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TERRITORY_TAB = CREATIVE_MODE_TABS.register(
            "territory_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.territory"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> TERRITORY_WAND.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TERRITORY_WAND.get());
                        output.accept(ADMIN_TERRITORY_WAND.get());
                        output.accept(TERRITORY_BOOK.get());
                        output.accept(TERRITORY_TABLE.get());
                        output.accept(ADMIN_TERRITORY_TABLE.get());
                    })
                    .build()
    );

    public Territory(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, TerritoryConfig.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, TerritoryConfig.COMMON_SPEC);

        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(TerritoryPayloads::register);
        modEventBus.addListener(ModDataGen::generate);

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Path dataDir = event.getServer().getWorldPath(TERRITORY_DATA);
        try {
            TerritoryDataManager.getInstance().initialize(dataDir);
            LOGGER.info("Territory data manager initialized");
        } catch (IOException e) {
            LOGGER.error("Failed to initialize territory data manager", e);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TerritoryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TerritoryDataManager.getInstance().shutdown();
        ServerSelectionCache.clear();
        LOGGER.info("Territory system stopped");
    }
}
