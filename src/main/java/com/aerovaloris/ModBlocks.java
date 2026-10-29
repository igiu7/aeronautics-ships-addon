package com.aerovaloris;

import com.aerovaloris.anchor.AnchorEntity;
import com.aerovaloris.anchor.AnchorWinchBlock;
import com.aerovaloris.rudder.RudderBlock;
import com.aerovaloris.rudder.RudderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(aeronauticsvaloris.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(aeronauticsvaloris.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, aeronauticsvaloris.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, aeronauticsvaloris.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, aeronauticsvaloris.MODID);

    public static final DeferredBlock<RudderBlock> RUDDER = BLOCKS.register("rudder", () -> new RudderBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.NORMAL)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RudderBlockEntity>> RUDDER_BE =
            BLOCK_ENTITIES.register("rudder", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new RudderBlockEntity(ModBlocks.RUDDER_BE.get(), pos, state),
                    RUDDER.get()).build(null));

    public static final DeferredItem<BlockItem> RUDDER_ITEM = ITEMS.registerSimpleBlockItem("rudder", RUDDER);

    public static final DeferredBlock<AnchorWinchBlock> ANCHOR_WINCH = BLOCKS.register("anchor_winch", () -> new AnchorWinchBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 4.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> ANCHOR_WINCH_ITEM = ITEMS.registerSimpleBlockItem("anchor_winch", ANCHOR_WINCH);

    public static final DeferredHolder<EntityType<?>, EntityType<AnchorEntity>> ANCHOR_ENTITY =
            ENTITIES.register("anchor", () -> EntityType.Builder.<AnchorEntity>of(AnchorEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.5f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSummon()
                    .build("anchor"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aeronauticsvaloris"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> RUDDER_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(RUDDER_ITEM.get());
                output.accept(ANCHOR_WINCH_ITEM.get());
            })
            .build());

    public static void register(final IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
