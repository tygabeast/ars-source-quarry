package com.tygabeast.arssourcequarry;

import com.tygabeast.arssourcequarry.block.LeyMarkerBlock;
import com.tygabeast.arssourcequarry.block.MarkerBlockEntity;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlock;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import com.tygabeast.arssourcequarry.menu.SourceQuarryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ArsSourceQuarry.MODID)
public class ArsSourceQuarry {
    public static final String MODID = "ars_source_quarry";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    /** Blocks the quarry never mines (datapack-editable). */
    public static final TagKey<Block> QUARRY_IMMUNE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "quarry_immune"));

    public static final DeferredBlock<LeyMarkerBlock> LEY_MARKER =
            BLOCKS.register("ley_marker", () -> new LeyMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(0.5f).sound(SoundType.AMETHYST).noOcclusion()
                    // counts as solid so flowing water cannot wash a marker away
                    .forceSolidOn().lightLevel(s -> 11).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<SourceQuarryBlock> SOURCE_QUARRY =
            BLOCKS.register("source_quarry", () -> new SourceQuarryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.5f).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).lightLevel(s -> 4).noOcclusion()));

    public static final DeferredItem<BlockItem> LEY_MARKER_ITEM = ITEMS.registerSimpleBlockItem(LEY_MARKER);
    public static final DeferredItem<BlockItem> SOURCE_QUARRY_ITEM = ITEMS.registerSimpleBlockItem(SOURCE_QUARRY);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER_BE =
            BLOCK_ENTITIES.register("ley_marker", () -> BlockEntityType.Builder
                    .of(MarkerBlockEntity::new, LEY_MARKER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SourceQuarryBlockEntity>> QUARRY_BE =
            BLOCK_ENTITIES.register("source_quarry", () -> BlockEntityType.Builder
                    .of(SourceQuarryBlockEntity::new, SOURCE_QUARRY.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<SourceQuarryMenu>> QUARRY_MENU =
            MENUS.register("source_quarry", () -> IMenuTypeExtension.create(SourceQuarryMenu::new));

    /**
     * Chunk tickets for quarries (only used when chunk loading is enabled in the config). On world load,
     * tickets whose quarry block is gone are dropped.
     */
    public static final TicketController TICKETS = new TicketController(id("quarry"), (level, helper) -> {
        for (BlockPos owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            if (!level.getBlockState(owner).is(SOURCE_QUARRY.get())) helper.removeAllTickets(owner);
        }
    });

    public ArsSourceQuarry(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, QuarryConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, QuarryClientConfig.SPEC);
        modBus.addListener(ArsSourceQuarry::addToTabs);
        modBus.addListener(ArsSourceQuarry::registerCapabilities);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SOURCE_QUARRY_ITEM);
            event.accept(LEY_MARKER_ITEM);
        }
    }

    /** Relays (and anything else using Ars Nouveau's Source capability) can push Source into a quarry. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY, QUARRY_BE.get(), (be, side) -> be.getSourceCap());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
