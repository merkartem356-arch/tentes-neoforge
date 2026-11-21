package com.tentnets;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TentNets.MODID)
public class TentNets {
    public static final String MODID = "tentnets";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final Supplier<Block> ANTI_DRONE_NET = BLOCKS.register("anti_drone_net",
            () -> new NetBlock(NetBlock.props(MapColor.COLOR_LIGHT_GRAY), NetBlock.Kind.ANTI_DRONE));
    public static final Supplier<Block> CAMO_NET = BLOCKS.register("camo_net",
            () -> new NetBlock(NetBlock.props(MapColor.COLOR_GREEN), NetBlock.Kind.CAMO));

    public static final Supplier<Item> ANTI_DRONE_NET_ITEM = ITEMS.register("anti_drone_net",
            () -> new BlockItem(ANTI_DRONE_NET.get(), new Item.Properties()));
    public static final Supplier<Item> CAMO_NET_ITEM = ITEMS.register("camo_net",
            () -> new BlockItem(CAMO_NET.get(), new Item.Properties()));
    public static final Supplier<Item> THERMAL_VISION = ITEMS.register("thermal_vision", ThermalItem::new);

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tentnets"))
                    .icon(() -> new ItemStack(ANTI_DRONE_NET_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(ANTI_DRONE_NET_ITEM.get());
                        out.accept(CAMO_NET_ITEM.get());
                        out.accept(THERMAL_VISION.get());
                    })
                    .build());

    public TentNets(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
        }
    }
}
