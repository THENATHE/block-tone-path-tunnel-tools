package com.thenathe.blocktools.qa;

import com.google.gson.JsonParser;
import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.PolymerTools;
import com.thenathe.blocktools.path.PathService;
import com.thenathe.blocktools.tunnel.TunnelService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.nio.file.Files;

/** Exercises the real server callbacks and JSON reload in both optional-dependency modes. */
public final class LiveOptionalToolsQa {
    private static int checks;
    public static int run(ServerPlayer player) throws Exception {
        checks = 0;
        commandPermissions(player);
        checks += LiveSpriteQa.run(player);
        boolean installed = FabricLoader.getInstance().isModLoaded("polymer-core")
            && FabricLoader.getInstance().isModLoaded("polymer-resource-pack");
        check(PolymerTools.available() == installed, "optional modules detected together");
        checks += LiveToolRecipesQa.availability(player, PolymerTools.active());
        var file = BlockToolsMod.CONFIG.path();
        String original = Files.readString(file);
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        try {
            Files.writeString(file, "{\"configVersion\":3,\"selectionItem\":\"minecraft:feather\",\"defaultPathWidth\":1}");
            check(command(player, "blocktools reload") > 0, "schema3 reload succeeds for admin");
            var migrated = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            check(migrated.get("configVersion").getAsInt() == 4, "schema3 saved as schema4");
            check(migrated.get("enablePolymerTools").getAsBoolean(), "missing optional setting defaults on");
            check(BlockToolsMod.CONFIG.config().defaultPathWidth == 1 && BlockToolsMod.CONFIG.legacySelectionItem() == Items.FEATHER,
                "migration preserves customized width and legacy item");
            check(PolymerTools.active() == installed, "default on activates only with Polymer");
            checks += LiveToolRecipesQa.availability(player, installed);
            if (installed) checks += LiveToolRecipesQa.crafting(player);
            Item mortar = BlockToolsMod.CONFIG.selectionItem();
            Item garden = BlockToolsMod.CONFIG.pathItem();
            Item pick = BlockToolsMod.TUNNEL_CONFIG.toolItem();
            if (installed) {
                check(mortar != garden && mortar != pick && garden != pick, "three separate registered tools");
                check(id(mortar).equals("blocktonepathtools:mortar_trowel") && id(garden).equals("blocktonepathtools:garden_trowel")
                    && id(pick).equals("blocktonepathtools:prospecting_pick"), "stable registry IDs");
                assertRoles(player, mortar, true, false, false);
                assertRoles(player, garden, false, true, false);
                assertRoles(player, pick, false, false, true);
                assertRoles(player, Items.FEATHER, false, false, false);
                assertRoles(player, Items.BREEZE_ROD, false, false, false);
                assertRoles(player, Items.IRON_NUGGET, false, false, false);
                player.getInventory().clearContent();
                check(command(player, "blocktools tools") > 0, "admin can get the custom tools");
                check(count(player, mortar) == 1 && count(player, garden) == 1 && count(player, pick) == 1, "grant adds exactly one of each role");
                checks += LivePolymerToolsQa.run(player, new Item[] {mortar, garden, pick});
            } else {
                check(mortar == Items.FEATHER && garden == mortar && pick == mortar, "absent Polymer uses exact legacy assignment");
                check(BuiltInRegistries.ITEM.getOptional(Identifier.parse("blocktonepathtools:mortar_trowel")).isEmpty(), "no custom registry items when Polymer absent");
                assertRoles(player, Items.FEATHER, true, true, true);
            }
            var path = BlockToolsMod.STATES.path(player.getUUID());
            var tunnel = BlockToolsMod.TUNNELS.state(player.getUUID());
            command(player, "pathtool to:bricks[100]");
            command(player, "tunneltool width:2 height:3 to:stone[100]");
            var pathPalette = path.palette;
            var tunnelSpec = tunnel.configured();
            var corners = BlockToolsMod.STATES.selection(player.getUUID()).points();
            migrated.addProperty("enablePolymerTools", false);
            Files.writeString(file, migrated.toString());
            check(command(player, "blocktools reload") > 0 && !PolymerTools.active(), "reload disables custom tools without restart");
            checks += LiveToolRecipesQa.availability(player, false);
            check(path.palette == pathPalette && tunnel.configured() == tunnelSpec && tunnel.enabled && !path.enabled,
                "config toggle retains palettes and mutually exclusive mode state");
            check(BlockToolsMod.STATES.selection(player.getUUID()).points().equals(corners), "reload retains selected corners");
            check(BlockToolsMod.CONFIG.selectionItem() == Items.FEATHER && BlockToolsMod.CONFIG.pathItem() == Items.FEATHER
                && BlockToolsMod.TUNNEL_CONFIG.toolItem() == Items.FEATHER, "disabled uses configured legacy item for all roles");
            assertRoles(player, Items.FEATHER, true, true, true);
            if (installed) {
                check(BuiltInRegistries.ITEM.getOptional(Identifier.parse("blocktonepathtools:mortar_trowel")).orElseThrow() == mortar,
                    "disabled integration keeps registered custom item identity");
                assertRoles(player, mortar, false, false, false);
                assertRoles(player, garden, false, false, false);
                assertRoles(player, pick, false, false, false);
            }
            player.getInventory().clearContent();
            check(command(player, "blocktools tools") > 0 && player.getInventory().isEmpty(), "fallback tools command explains legacy without giving custom items");
            migrated.addProperty("enablePolymerTools", true);
            Files.writeString(file, migrated.toString());
            command(player, "blocktools reload");
            check(PolymerTools.active() == installed && BlockToolsMod.CONFIG.selectionItem() == mortar,
                "reload re-enables the same saved item identity when supported");
            checks += LiveToolRecipesQa.availability(player, installed);
            if (installed) {
                for (boolean enabled : new boolean[] {false, true, false}) {
                    migrated.addProperty("enablePolymerTools", enabled);
                    Files.writeString(file, migrated.toString());
                    player.level().getServer().getCommands().getDispatcher().execute("blocktools reload", adminSource(player));
                    check(!com.thenathe.blocktools.compat.ToolRecipes.pendingReload().isDone(), "reload returns before its resource work completes");
                }
                LiveToolRecipesQa.awaitReload(player);
                checks += LiveToolRecipesQa.availability(player, false);
                check(!PolymerTools.active(), "rapid queued toggles finish in requested disabled state");
                migrated.addProperty("enablePolymerTools", true);
                Files.writeString(file, migrated.toString());
                command(player, "blocktools reload");
                checks += LiveToolRecipesQa.availability(player, true);
            }
        } finally {
            Files.writeString(file, original);
            command(player, "blocktools reload");
            BlockToolsMod.STATES.remove(player.getUUID());
            BlockToolsMod.TUNNELS.remove(player.getUUID());
            player.getInventory().clearContent();
        }
        return checks;
    }
    private static void assertRoles(ServerPlayer player, Item item, boolean selection, boolean path, boolean tunnel) throws Exception {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var state = BlockToolsMod.STATES.selection(player.getUUID());
        state.clear();
        BlockPos pos = new BlockPos(256, 99, 256);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult left = AttackBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, pos, Direction.UP);
        check(selection ? pos.equals(state.pointOne()) : state.pointOne() == null && left == InteractionResult.PASS,
            id(item) + " selects left corner only for correct role");
        UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
        check(selection ? pos.equals(state.pointTwo()) : state.pointTwo() == null, id(item) + " right corner role");
        Method holdsPath = PathService.class.getDeclaredMethod("holdsPathItem", ServerPlayer.class);
        Method holdsTunnel = TunnelService.class.getDeclaredMethod("holdsTool", ServerPlayer.class);
        holdsPath.setAccessible(true);
        holdsTunnel.setAccessible(true);
        check((boolean) holdsPath.invoke(null, player) == path, id(item) + " main hand path role");
        check((boolean) holdsTunnel.invoke(null, player) == tunnel, id(item) + " main hand tunnel role");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
        check((boolean) holdsPath.invoke(null, player) == path, id(item) + " offhand path role");
        check((boolean) holdsTunnel.invoke(null, player) == tunnel, id(item) + " offhand tunnel role");
        state.clear();
        UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, hit);
        check(state.points().isEmpty(), "offhand never selects corners");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }
    private static net.minecraft.commands.CommandSourceStack adminSource(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.ADMIN);
    }
    private static void commandPermissions(ServerPlayer player) throws Exception {
        var dispatcher = player.level().getServer().getCommands().getDispatcher();
        var root = dispatcher.getRoot().getChild("blocktools");
        var originalMode = player.gameMode.getGameModeForPlayer();
        try {
            for (var mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
                player.setGameMode(mode);
                for (var permission : new net.minecraft.server.permissions.LevelBasedPermissionSet[] {
                        net.minecraft.server.permissions.LevelBasedPermissionSet.ALL,
                        net.minecraft.server.permissions.LevelBasedPermissionSet.MODERATOR,
                        net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER}) {
                    var source = player.createCommandSourceStack().withPermission(permission);
                    check(root.canUse(source), "non-admin can access personal sprite subcommands in " + mode);
                    for (String choice : new String[] {"32x32", "16x16", "auto"})
                        check(dispatcher.execute("blocktools " + choice, source) > 0, "non-admin cosmetic preference accepted: " + choice);
                    for (String command : new String[] {"blocktools", "blocktools help", "blocktools help verbose", "blocktools tools", "blocktools reload"}) {
                        boolean refused = false;
                        try { dispatcher.execute(command, source); }
                        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { refused = true; }
                        check(refused, "non-admin command refused: " + command);
                    }
                    for (String command : new String[] {"blocktone help", "pathtool help", "tunneltool help", "tunnel help"}) {
                        check(dispatcher.execute(command, source) > 0, "public tool help remains available: " + command);
                    }
                }
            }
            check(dispatcher.execute("blocktools help", adminSource(player)) > 0, "admin can access blocktools help");
            check(root.canUse(player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER)), "owner can access blocktools");
            check(dispatcher.execute("blocktools help", player.level().getServer().createCommandSourceStack()) > 0, "console can access blocktools");
        } finally {
            player.setGameMode(originalMode);
        }
    }
    private static int command(ServerPlayer player, String text) throws Exception {
        int result = player.level().getServer().getCommands().getDispatcher().execute(text, text.startsWith("blocktools") ? adminSource(player) : player.createCommandSourceStack());
        LiveToolRecipesQa.awaitReload(player);
        return result;
    }
    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
    private static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
