package com.thenathe.blocktools.compat;

import com.thenathe.blocktools.BlockToolsMod;
import eu.pb4.polymer.core.api.item.PolymerItem;
import com.thenathe.blocktools.sprite.SpritePreferences;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncUtils;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

/** Loaded only after both optional Polymer modules are detected. */
final class PolymerToolsImpl {
    static Item[] register() {
        Item[] items = {
            register("mortar_trowel", "Block Toning Tool", "Left/right-click: select Blocktone corners"),
            register("garden_trowel", "Path Tool", "Hold and walk after configuring /pathtool"),
            register("prospecting_pick", "Tunnel Tool", "Hold and walk after configuring /tunneltool")
        };
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.withDefaultNamespace("tools_and_utilities"))).register(output -> {
                if (PolymerTools.active()) for (Item item : items) output.accept(item);
            });
        // Polymer caches its server-side Creative catalog between resource reloads.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) PolymerCreativeModeTabUtils.invalidateCache();
        });
        PolymerResourcePackUtils.addModAssets(BlockToolsMod.MOD_ID);
        return items;
    }

    static void refreshSprites(ServerPlayer player) {
        PolymerUtils.reloadInventory(player);
        PolymerSyncUtils.synchronizeCreativeTabs(player.connection);
    }

    private static Item register(String name, String label, String help) {
        Identifier id = Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.ITEM, id, new ToolItem(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)
            .component(DataComponents.ITEM_NAME, Component.translatableWithFallback("item." + BlockToolsMod.MOD_ID + "." + name, label)), id, help));
    }

    private static final class ToolItem extends Item implements PolymerItem {
        private final Identifier model;
        private final String help;
        ToolItem(Properties properties, Identifier model, String help) {
            super(properties);
            this.model = model;
            this.help = help;
        }
        @Override public Item getPolymerItem(ItemStack stack, PacketContext context) { return Items.IRON_NUGGET; }
        @Override public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
            var profile = context.get(PacketContext.GAME_PROFILE);
            boolean localAssets = profile != null && SpritePreferences.hasClient(profile.id());
            if (!localAssets && !PolymerResourcePackUtils.hasMainPack(context)) return Identifier.withDefaultNamespace("iron_nugget");
            return profile != null && SpritePreferences.high(profile.id())
                ? Identifier.fromNamespaceAndPath(model.getNamespace(), model.getPath() + "_32") : model;
        }
        @Override public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
            tooltip.add(Component.literal(PolymerTools.active() ? help : "Polymer tools disabled; use the configured legacy item."));
        }
        @Override public boolean handleMiningOnServer(ItemStack stack, BlockState state, BlockPos pos, ServerPlayer player) {
            return PolymerTools.active() && this == BlockToolsMod.CONFIG.selectionItem();
        }
        @Override public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player, InteractionHand hand,
                ItemStack stack, ServerLevel level, BlockHitResult hit, InteractionResult result) {
            // A vanilla client sees a nugget. Suppress duplicate offhand use after selecting a corner.
            return PolymerTools.active() && hand == InteractionHand.MAIN_HAND && this == BlockToolsMod.CONFIG.selectionItem();
        }
        @Override public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
            return !(PolymerTools.active() && this == BlockToolsMod.CONFIG.selectionItem());
        }
    }
}
