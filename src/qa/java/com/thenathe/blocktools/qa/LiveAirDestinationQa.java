package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real command, staged-world, and survival-accounting checks for air palettes. */
public final class LiveAirDestinationQa {
    private static final BlockPos CENTER = new BlockPos(432, 150, 432);
    private static final int EXTENT = 16;
    private final ServerPlayer player;
    private final ServerLevel level;
    private int assertions;

    private LiveAirDestinationQa(ServerPlayer player, ServerLevel level) {
        this.player = player;
        this.level = level;
        for (int x = CENTER.getX() >> 4; x <= (CENTER.getX() + EXTENT) >> 4; x++) {
            for (int z = CENTER.getZ() >> 4; z <= (CENTER.getZ() + EXTENT) >> 4; z++) level.getChunk(x, z);
        }
    }

    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        LiveAirDestinationQa qa = new LiveAirDestinationQa(player, level);
        try {
            qa.sourceSelectionAndRefunds();
            qa.quotasAndLegacy();
            qa.calculate();
            qa.safeSourceGuards();
            qa.atomicFailures();
            qa.negation();
        } finally {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            BlockToolsMod.STATES.remove(player.getUUID());
            BlockToolsMod.TUNNELS.remove(player.getUUID());
        }
        return qa.assertions;
    }

    private void sourceSelectionAndRefunds() throws Exception {
        reset(GameType.SURVIVAL);
        BlockPos stair = CENTER.offset(1, 0, 1), slab = stair.east(), trapdoor = slab.east();
        put(stair, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        put(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
        put(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState());
        put(stair.below(), Blocks.STONE.defaultBlockState());
        select(stair.below(), trapdoor);
        check(command("blocktone type:stairs to:air") > 0, "air-only type command failed without carried items");
        check(level.getBlockState(stair).is(Blocks.AIR) && level.getFluidState(stair).isEmpty(), "waterlogged removal retained block or contained fluid");
        check(level.getBlockState(stair.below()).is(Blocks.STONE) && level.getBlockState(slab).is(Blocks.OAK_SLAB)
            && level.getBlockState(trapdoor).is(Blocks.OAK_TRAPDOOR), "type removal changed unmatched support or shapes");
        check(count(Items.OAK_STAIRS) == 1 && count(Items.AIR) == 0, "air removal settled wrong stair refund or charged air");
        check(command("blocktone from:oak_slab to:minecraft:air[100]") > 0, "namespaced air removal rejected exact block source");
        check(level.getBlockState(slab).isAir() && count(Items.OAK_SLAB) == 2, "double slab removal did not refund two slabs");
        Map<Item, Integer> before = InventoryMaterials.count(player);
        check(command("blocktone from:air to:air[100]") > 0, "air-to-air no-op failed");
        check(InventoryMaterials.count(player).equals(before), "air-to-air no-op duplicated materials");
        check(command("blocktone type:trapdoors to:air[100]") > 0 && count(Items.OAK_TRAPDOOR) == 1, "trapdoor removal/refund failed");
    }

    private void quotasAndLegacy() throws Exception {
        for (String spec : List.of("to:air[25]", "to:air[25],bricks[25]", "to:air:50,bricks:50", "to:minecraft:air:100")) {
            reset(GameType.SURVIVAL);
            row(8, Blocks.STONE);
            player.getInventory().add(new ItemStack(Items.BRICKS, 8));
            check(command("blocktone from:stone " + spec) > 0, "air mixed/legacy palette rejected: " + spec);
            int air = spec.equals("to:air[25]") || spec.equals("to:air[25],bricks[25]") ? 2 : spec.endsWith("air:100") ? 8 : 4;
            int bricks = spec.contains("bricks[25]") ? 2 : spec.contains("bricks:50") ? 4 : 0;
            check(rowCount(8, Blocks.AIR) == air && rowCount(8, Blocks.BRICKS) == bricks && rowCount(8, Blocks.STONE) == 8 - air - bricks,
                "air palette lost exact quota or unchanged remainder: " + spec);
            check(count(Items.STONE) == air + bricks && count(Items.BRICKS) == 8 - bricks && count(Items.AIR) == 0,
                "mixed air inventory settlement incorrect: " + spec);
        }
    }

    private void calculate() throws Exception {
        reset(GameType.SURVIVAL);
        row(10, Blocks.STONE);
        Map<BlockPos, BlockState> before = snapshot();
        check(command("blocktone calc from:stone to:air") > 0, "zero-item air calc preview rejected");
        check(snapshot().equals(before) && InventoryMaterials.count(player).isEmpty(), "air calc preview changed world/inventory");
        check(command("blocktone calc apply from:stone to:air") > 0 && rowCount(10, Blocks.AIR) == 10 && count(Items.STONE) == 10,
            "zero-item air calc apply failed");

        reset(GameType.SURVIVAL);
        row(10, Blocks.STONE);
        player.getInventory().add(new ItemStack(Items.BRICKS, 3));
        check(command("blocktone calc apply from:stone to:air,bricks") > 0, "free-air fallback mixed calc failed");
        check(rowCount(10, Blocks.AIR) == 7 && rowCount(10, Blocks.BRICKS) == 3 && count(Items.BRICKS) == 0 && count(Items.STONE) == 10,
            "calc air did not fill capacity left after carried paid material");

        reset(GameType.SURVIVAL);
        row(10, Blocks.BRICKS);
        for (int i = 5; i < 10; i++) put(CENTER.offset(i, 0, 1), Blocks.STONE.defaultBlockState());
        player.getInventory().add(new ItemStack(Items.BRICKS, 3));
        check(command("blocktone calc apply to:air,bricks") > 0, "existing-material air calc failed");
        check(rowCount(10, Blocks.AIR) == 2 && rowCount(10, Blocks.BRICKS) == 8 && count(Items.STONE) == 5 && count(Items.BRICKS) == 0,
            "calc air took capacity before requested existing paid material");

        reset(GameType.SURVIVAL);
        row(10, Blocks.STONE);
        player.getInventory().add(new ItemStack(Items.BRICKS, 3));
        check(command("blocktone calc apply to:air[20],bricks") > 0, "fixed air calc rejected");
        check(rowCount(10, Blocks.AIR) == 2 && rowCount(10, Blocks.BRICKS) == 3 && rowCount(10, Blocks.STONE) == 5,
            "explicit air quota became an automatic free fallback");

        reset(GameType.CREATIVE);
        row(10, Blocks.STONE);
        check(command("blocktone calc apply to:air,bricks") > 0 && rowCount(10, Blocks.AIR) == 5 && rowCount(10, Blocks.BRICKS) == 5,
            "creative calc automatic entries lost equal split");
    }

    private void safeSourceGuards() throws Exception {
        reset(GameType.SURVIVAL);
        Block[] sources = {Blocks.STONE, Blocks.CHEST, Blocks.BEDROCK, Blocks.BED.red(), Blocks.WATER};
        for (int i = 0; i < sources.length; i++) put(CENTER.offset(i * 3, 0, 1), sources[i].defaultBlockState());
        select(CENTER.offset(0, 0, 1), CENTER.offset((sources.length - 1) * 3, 0, 1));
        check(command("blocktone to:air") > 0, "default source clearing failed");
        check(level.getBlockState(CENTER.offset(0, 0, 1)).isAir(), "safe ordinary source was not cleared");
        for (int i = 1; i < sources.length; i++) check(level.getBlockState(CENTER.offset(i * 3, 0, 1)).is(sources[i]), "air destination bypassed protected/multipart/fluid source: " + sources[i]);
        check(count(Items.STONE) == 1 && count(Items.CHEST) == 0 && count(Items.BEDROCK) == 0 && count(Blocks.BED.red().asItem()) == 0,
            "air clearing returned protected source materials");
        check(command("blocktone type:liquids to:air") > 0 && level.getBlockState(CENTER.offset(12, 0, 1)).isAir(), "explicit fluid source could not be cleared");
    }

    private void atomicFailures() throws Exception {
        reset(GameType.SURVIVAL);
        row(8, Blocks.STONE);
        rejected("blocktone to:air[50],bricks[50]", "mixed removal with unavailable paid destination");
        rejected("blocktone calc apply to:air[50],bricks[50]", "calc removal with unavailable fixed paid destination");
        rejected("blocktone to:cave_air", "cave air destination");
        rejected("blocktone to:void_air", "void air destination");

        reset(GameType.SURVIVAL);
        BlockPos base = CENTER.offset(1, 0, 1), torch = base.above();
        put(base, Blocks.STONE.defaultBlockState());
        put(torch, Blocks.TORCH.defaultBlockState());
        select(base, torch);
        // Existing torch claims its exact quota, leaving air to remove its support.
        rejected("blocktone to:air[50],torch[50]", "staged removal invalidates an unchanged target's support");
    }

    private void negation() throws Exception {
        reset(GameType.SURVIVAL);
        Block[] sources = {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.WATER};
        for (int i = 0; i < sources.length; i++) put(CENTER.offset(i, 0, 1), sources[i].defaultBlockState());
        select(CENTER.offset(0, 0, 1), CENTER.offset(sources.length - 1, 0, 1));
        check(command("blocktone from:not(grass_block,dirt) to:air") > 0, "negated block source rejected");
        for (int i = 0; i < sources.length; i++) check(level.getBlockState(CENTER.offset(i, 0, 1)).is(i < 2 || i == 5 ? sources[i] : Blocks.AIR),
            "negated source failed preserve/clear boundary: " + sources[i]);
        check(count(Items.STONE) == 1 && count(Items.OAK_STAIRS) == 1 && count(Items.OAK_SLAB) == 1 && count(Items.DIRT) == 0,
            "negated clearing returned excluded materials");

        reset(GameType.SURVIVAL);
        row(3, Blocks.STONE);
        put(CENTER.offset(1, 0, 1), Blocks.OAK_STAIRS.defaultBlockState());
        put(CENTER.offset(2, 0, 1), Blocks.OAK_SLAB.defaultBlockState());
        check(command("blocktone type:not(stairs,slabs) to:air") > 0 && rowCount(3, Blocks.AIR) == 1
            && rowCount(3, Blocks.OAK_STAIRS) == 1 && rowCount(3, Blocks.OAK_SLAB) == 1, "negated type source lost excluded shapes");
        rejected("blocktone from:not(stone to:air", "unclosed negated source");
        rejected("blocktone from:not(missing_block) to:air", "invalid negated source ID");
    }

    private void rejected(String specification, String context) throws Exception {
        Map<BlockPos, BlockState> before = snapshot();
        Map<Item, Integer> inventory = InventoryMaterials.count(player);
        check(command(specification) == 0, context + " should reject");
        check(snapshot().equals(before) && InventoryMaterials.count(player).equals(inventory), context + " changed world or inventory before failing");
    }

    private void row(int count, Block material) {
        for (int i = 0; i < count; i++) put(CENTER.offset(i, 0, 1), material.defaultBlockState());
        select(CENTER.offset(0, 0, 1), CENTER.offset(count - 1, 0, 1));
    }
    private int rowCount(int count, Block block) {
        int result = 0;
        for (int i = 0; i < count; i++) if (level.getBlockState(CENTER.offset(i, 0, 1)).is(block)) result++;
        return result;
    }
    private void reset(GameType mode) {
        player.setGameMode(mode);
        player.getInventory().clearContent();
        BlockToolsMod.STATES.remove(player.getUUID());
        BlockToolsMod.TUNNELS.remove(player.getUUID());
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -1; y <= 2; y++) put(CENTER.offset(x, y, z), Blocks.AIR.defaultBlockState());
    }
    private Map<BlockPos, BlockState> snapshot() {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -1; y <= 2; y++) {
            BlockPos pos = CENTER.offset(x, y, z);
            result.put(pos, level.getBlockState(pos));
        }
        return result;
    }
    private void select(BlockPos first, BlockPos second) {
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 1, first);
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 2, second);
    }
    private void put(BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_SKIP_ALL_SIDEEFFECTS); }
    private int command(String specification) throws Exception { return level.getServer().getCommands().getDispatcher().execute(specification, player.createCommandSourceStack()); }
    private int count(Item item) { return InventoryMaterials.count(player).getOrDefault(item, 0); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
