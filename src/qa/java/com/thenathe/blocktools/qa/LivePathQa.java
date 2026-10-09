package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.path.PathService;
import com.thenathe.blocktools.state.PlayerStateManager.PathState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.lang.reflect.Method;

/** Survival path checks against a real disposable ServerLevel and connected test player. */
public final class LivePathQa {
    private static final BlockPos CENTER = new BlockPos(264, 120, 264);
    private final ServerPlayer player;
    private final ServerLevel level;
    private final Method stripe;
    private int assertions;

    private LivePathQa(ServerPlayer player, ServerLevel level) throws Exception {
        this.player = player;
        this.level = level;
        stripe = PathService.class.getDeclaredMethod("placeStripe", ServerPlayer.class, PathState.class, BlockPos.class, Direction.class);
        stripe.setAccessible(true);
    }

    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        LivePathQa qa = new LivePathQa(player, level);
        qa.runChecks();
        return qa.assertions;
    }

    private void runChecks() throws Exception {
        PathState path = reset(Blocks.GRASS_BLOCK.defaultBlockState(), "dirt_path[100]", 1);
        check(place(path), "free dirt path conversion failed");
        check(level.getBlockState(CENTER.below()).is(Blocks.DIRT_PATH), "free path did not change grass ground");
        check(InventoryMaterials.count(player).isEmpty(), "free dirt path duplicated or charged ground items");
        check(place(path) && InventoryMaterials.count(player).isEmpty(), "repeated free path generated materials");

        path = reset(Blocks.STONE.defaultBlockState(), "bricks[100]", 2);
        player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 5));
        check(place(path), "funded two-lane stripe failed");
        check(level.getBlockState(CENTER.below()).is(Blocks.BRICKS)
            && level.getBlockState(CENTER.below().west()).is(Blocks.BRICKS), "funded stripe missed a lane");
        check(count(Items.BRICKS) == 3 && count(Items.STONE) == 2, "funded stripe did not settle exact cost and refund");
        check(place(path) && count(Items.BRICKS) == 3 && count(Items.STONE) == 2, "repeat path charged or refunded unchanged cells");

        path = reset(Blocks.STONE.defaultBlockState(), "bricks[100]", 2);
        player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
        check(!place(path), "short two-lane stripe was accepted");
        check(level.getBlockState(CENTER.below()).is(Blocks.STONE)
            && level.getBlockState(CENTER.below().west()).is(Blocks.STONE), "shortage partially changed a stripe");
        check(count(Items.BRICKS) == 1 && count(Items.STONE) == 0, "shortage partially charged or refunded materials");

        path = reset(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE), "bricks[100]", 1);
        player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
        check(place(path), "double-slab path conversion failed");
        check(count(Items.BRICKS) == 0 && count(Items.STONE_SLAB) == 2, "double-slab refund lost a slab");

        path = reset(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), "bricks[100]", 1);
        player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
        place(path);
        check(level.getBlockState(CENTER.below()).getValue(BlockStateProperties.WATERLOGGED), "path removed waterlogged ground");
        check(count(Items.BRICKS) == 1, "skipped wet ground consumed materials");

        path = reset(Blocks.DIRT.defaultBlockState(), "bricks[100]", 1);
        level.setBlock(CENTER, Blocks.TALL_GRASS.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        level.setBlock(CENTER.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
        check(place(path), "path under tall grass failed");
        check(level.getBlockState(CENTER).isAir() && level.getBlockState(CENTER.above()).isAir(), "both tall-grass halves were not cleared");
        check(count(Items.TALL_GRASS) == 1 && count(Items.DIRT) == 1 && count(Items.BRICKS) == 0, "tall-grass pair duplicated or lost its refund");

        for (GameType mode : new GameType[]{GameType.ADVENTURE, GameType.SPECTATOR}) {
            path = reset(Blocks.STONE.defaultBlockState(), "bricks[100]", 1);
            player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
            player.setGameMode(mode);
            place(path);
            check(level.getBlockState(CENTER.below()).is(Blocks.STONE) && count(Items.BRICKS) == 1,
                "path modified world/inventory in " + mode);
        }
        player.setGameMode(GameType.SURVIVAL);
    }

    private PathState reset(BlockState ground, String palette, int width) {
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        BlockToolsMod.STATES.remove(player.getUUID());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 4; y++) {
            level.setBlock(CENTER.offset(x, y, z), y == -1 ? ground : Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
        PathState state = BlockToolsMod.STATES.path(player.getUUID());
        state.palette = Palette.parseBracketed(palette, true, false);
        state.width = width;
        return state;
    }

    private boolean place(PathState path) throws Exception { return (boolean) stripe.invoke(null, player, path, CENTER, Direction.SOUTH); }
    private int count(Item item) { return InventoryMaterials.count(player).getOrDefault(item, 0); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
