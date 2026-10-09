package com.thenathe.blocktools.compat;

import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;

/** No Polymer types in this bridge: it can load on a plain Fabric server. */
public final class PolymerTools {
    private static Item mortarTrowel;
    private static Item gardenTrowel;
    private static Item prospectingPick;

    private PolymerTools() {}

    public static void initialize() {
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("polymer-core") && loader.isModLoaded("polymer-resource-pack")) {
            // Register even when disabled, so saved custom stacks survive config toggles.
            Item[] items = PolymerToolsImpl.register();
            mortarTrowel = items[0];
            gardenTrowel = items[1];
            prospectingPick = items[2];
        }
    }

    public static boolean available() { return mortarTrowel != null; }
    public static boolean active() { return available() && BlockToolsMod.CONFIG.config().enablePolymerTools; }
    public static Item selectionItem(Item legacy) { return active() ? mortarTrowel : legacy; }
    public static Item pathItem(Item legacy) { return active() ? gardenTrowel : legacy; }
    public static Item tunnelItem(Item legacy) { return active() ? prospectingPick : legacy; }

    public static void refreshSprites(net.minecraft.server.level.ServerPlayer player) {
        if (available()) PolymerToolsImpl.refreshSprites(player);
    }

    public static String status() {
        if (active()) return "Polymer tools enabled. All three recipes unlock automatically in the crafting book. Tools are in Creative Tools & Utilities (vanilla clients: /polymer creative minecraft:tools_and_utilities). Admins can use /blocktools tools.";
        return "Legacy tool assignments active (" + (available() ? "enablePolymerTools is false" : "Polymer Core and Resource Pack are not both installed") + ").";
    }
}
