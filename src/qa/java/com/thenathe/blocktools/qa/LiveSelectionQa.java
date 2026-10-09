package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.selection.SelectionService;
import com.thenathe.blocktools.selection.SelectionVisualization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual callbacks and commands in the disposable server; never packaged with the mod. */
public final class LiveSelectionQa {
    private static int assertions;
    private LiveSelectionQa() {}

    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        assertions = 0;
        SelectionVisualization.clear();
        BlockToolsMod.STATES.remove(player.getUUID());
        BlockToolsMod.TUNNELS.remove(player.getUUID());
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.setPos(256.5, 100, 256.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockToolsMod.CONFIG.selectionItem()));
        BlockPos first = new BlockPos(256, 99, 256), second = first.offset(2, 2, 2);
        Map<BlockPos, BlockState> before = snapshot(level, first);
        List<ItemStack> inventoryBefore = inventory(player);
        int tick = level.getServer().getTickCount();
        Method left = SelectionService.class.getDeclaredMethod("selectLeft", Player.class, Level.class,
            InteractionHand.class, BlockPos.class, Direction.class);
        Method right = SelectionService.class.getDeclaredMethod("selectRight", Player.class, Level.class,
            InteractionHand.class, BlockHitResult.class);
        left.setAccessible(true);
        right.setAccessible(true);
        try {
            check(!SelectionVisualization.show(player), "empty selection cannot activate visualization");
            check(command(player, "blocktone visualize") == 0, "empty visualize command reports failure");
            check(left.invoke(null, player, level, InteractionHand.OFF_HAND, first, Direction.UP) == InteractionResult.PASS,
                "offhand click passes through without a selection");
            check(BlockToolsMod.STATES.selection(player.getUUID()).points().isEmpty(), "offhand did not create a corner");
            check(left.invoke(null, player, level, InteractionHand.MAIN_HAND, first, Direction.UP) == (com.thenathe.blocktools.compat.PolymerTools.active() ? InteractionResult.FAIL : InteractionResult.SUCCESS),
                "left selection consumes interaction");
            var selection = BlockToolsMod.STATES.selection(player.getUUID());
            check(first.equals(selection.pointOne()) && selection.pointTwo() == null, "left-click sets only Point 1");
            check(SelectionVisualization.refresh(player, tick), "first corner automatically activates visualization");
            check(!SelectionVisualization.refresh(player, tick + 401), "first-corner visualization expires after twenty seconds");
            left.invoke(null, player, level, InteractionHand.MAIN_HAND, first, Direction.UP);
            check(SelectionVisualization.refresh(player, tick), "repeat click refreshes expired outline without requiring changed coordinates");
            check(right.invoke(null, player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second, false)) == InteractionResult.SUCCESS,
                "right selection consumes interaction");
            check(first.equals(selection.pointOne()) && second.equals(selection.pointTwo()), "right-click completes opposite corners");
            check(command(player, "blocktone visualize off") > 0, "hide command succeeds");
            check(!SelectionVisualization.refresh(player, tick), "hide command stops selected outline");
            check(selection.complete(), "hide preserves selected corners");
            check(command(player, "blocktone visualize") > 0 && SelectionVisualization.refresh(player, tick), "command restores selected outline");
            SelectionVisualization.hide(player.getUUID());
            check(!SelectionVisualization.refresh(player, tick), "direct hide removes activation");
            SelectionVisualization.show(player);
            SelectionVisualization.clear();
            check(!SelectionVisualization.refresh(player, tick), "global clear removes activation");
            SelectionVisualization.show(player);
            player.setShiftKeyDown(true);
            left.invoke(null, player, level, InteractionHand.MAIN_HAND, first, Direction.UP);
            player.setShiftKeyDown(false);
            check(selection.points().isEmpty() && !SelectionVisualization.refresh(player, tick), "sneak click clears corners and overlay");
            right.invoke(null, player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second, false));
            check(selection.pointOne() == null && second.equals(selection.pointTwo()), "right corner can be selected first");
            check(SelectionVisualization.refresh(player, tick), "right-only selection also previews");
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            check(nether != null, "test server provides another dimension");
            selection.set(nether, 1, first);
            check(!SelectionVisualization.refresh(player, tick), "active overlay stops after selection changes dimension");
            check(!SelectionVisualization.show(player), "wrong-dimension selection cannot activate");
            selection.clear();
            SelectionVisualization.clear();

            check(command(player, "pathtool to:bricks[100]") > 0 && command(player, "pathtool off") > 0,
                "path configuration can be paused for preview");
            var path = BlockToolsMod.STATES.path(player.getUUID());
            SelectionVisualization.clear();
            check(command(player, "pathtool visualize") > 0 && SelectionVisualization.refresh(player, tick), "paused path can be visualized");
            check(!path.enabled && path.lastCenter == null && path.lastSurfaces.isEmpty(), "path preview does not activate or advance editing trail");
            check(command(player, "pathtool visualize off") > 0 && !SelectionVisualization.refresh(player, tick), "path preview hides independently");
            check(path.palette != null, "hiding path preview preserves configuration");

            check(command(player, "tunneltool width:2 height:3 to:bricks[100]") > 0 && command(player, "tunneltool off") > 0,
                "tunnel configuration can be paused for preview");
            var tunnel = BlockToolsMod.TUNNELS.state(player.getUUID());
            SelectionVisualization.clear();
            check(command(player, "tunneltool visualize") > 0 && SelectionVisualization.refresh(player, tick), "paused tunnel can be visualized");
            check(!tunnel.enabled && tunnel.lastCenter == null && tunnel.processedCenters.isEmpty(), "tunnel preview does not activate or advance editing trail");
            check(command(player, "tunneltool visualize off") > 0 && !SelectionVisualization.refresh(player, tick), "tunnel preview hides independently");
            check(tunnel.configured(), "hiding tunnel preview preserves configuration");
            check(command(player, "tunnel visualize") > 0, "short tunnel alias supports visualize");
            check(!SelectionVisualization.refresh(player, tick + 401), "tool preview also expires");
            check(SelectionVisualization.showPath(player) && SelectionVisualization.showTunnel(player), "multiple preview types can coexist");
            SelectionVisualization.hideTunnel(player.getUUID());
            check(SelectionVisualization.refresh(player, tick), "hiding tunnel leaves path preview active");
            SelectionVisualization.hidePath(player.getUUID());
            check(!SelectionVisualization.refresh(player, tick), "hiding remaining path leaves no preview");
            SelectionVisualization.showPath(player);
            SelectionVisualization.showTunnel(player);
            SelectionVisualization.clear();
            check(!SelectionVisualization.refresh(player, tick), "global clear removes every tool preview");
            check(before.equals(snapshot(level, first)), "selection callbacks and every preview command leave nearby world blocks unchanged");
            List<ItemStack> inventoryAfter = inventory(player);
            check(inventoryBefore.size() == inventoryAfter.size(), "inventory size is unchanged");
            for (int i = 0; i < inventoryBefore.size(); i++) {
                check(ItemStack.matches(inventoryBefore.get(i), inventoryAfter.get(i)), "preview preserves full stack and components in slot " + i);
            }
        } finally {
            player.setShiftKeyDown(false);
            SelectionVisualization.clear();
            BlockToolsMod.STATES.remove(player.getUUID());
            BlockToolsMod.TUNNELS.remove(player.getUUID());
        }
        return assertions;
    }

    private static int command(ServerPlayer player, String input) throws Exception {
        return player.level().getServer().getCommands().getDispatcher().execute(input, player.createCommandSourceStack());
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = -4; x <= 4; x++) for (int y = -2; y <= 5; y++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = center.offset(x, y, z);
            blocks.put(pos, level.getBlockState(pos));
        }
        return blocks;
    }

    private static List<ItemStack> inventory(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) result.add(player.getInventory().getItem(i).copy());
        return result;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        assertions++;
    }
}
