package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.inventory.InventoryMaterials;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact user-command regression checks in a loaded, disposable real server world. */
public final class LiveToneShapeQa {
    private static final BlockPos CENTER = new BlockPos(288, 150, 288);
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int EXTENT = 48;
    private final ServerPlayer player;
    private final ServerLevel level;
    private int assertions;

    private LiveToneShapeQa(ServerPlayer player, ServerLevel level) {
        this.player = player;
        this.level = level;
        for (int x = CENTER.getX() >> 4; x <= (CENTER.getX() + EXTENT) >> 4; x++) {
            for (int z = CENTER.getZ() >> 4; z <= (CENTER.getZ() + EXTENT) >> 4; z++) level.getChunk(x, z);
        }
    }

    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        LiveToneShapeQa qa = new LiveToneShapeQa(player, level);
        try {
            qa.roof();
            qa.calculate();
            qa.slabs();
            qa.trapdoors();
            qa.connections();
            qa.waterlogged();
            qa.explicitAirAndLiquids();
            qa.failures();
        } finally {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            BlockToolsMod.STATES.remove(player.getUUID());
            BlockToolsMod.TUNNELS.remove(player.getUUID());
        }
        return qa.assertions;
    }

    private void roof() throws Exception {
        reset(GameType.CREATIVE);
        List<BlockPos> stairs = new ArrayList<>();
        Map<BlockPos, BlockState> roots = new LinkedHashMap<>();
        Block[] oldMaterials = {Blocks.OAK_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.STONE_BRICK_STAIRS};
        int motif = 0;
        for (Half half : Half.values()) for (Direction facing : HORIZONTAL) for (StairsShape shape : StairsShape.values()) {
            BlockPos root = CENTER.offset(3 + motif % 8 * 5, 0, 3 + motif / 8 * 5);
            BlockState state = oldMaterials[motif % oldMaterials.length].defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, half);
            put(root, state);
            stairs.add(root);
            if (shape != StairsShape.STRAIGHT) {
                boolean outer = shape == StairsShape.OUTER_LEFT || shape == StairsShape.OUTER_RIGHT;
                boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
                BlockPos neighbor = root.relative(outer ? facing : facing.getOpposite());
                put(neighbor, oldMaterials[(motif + 1) % oldMaterials.length].defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, left ? facing.getCounterClockWise() : facing.getClockWise())
                    .setValue(BlockStateProperties.HALF, half));
                stairs.add(neighbor);
            }
            for (BlockPos pos : stairs) put(pos.below(), Blocks.STONE.defaultBlockState());
            normalize(stairs);
            check(level.getBlockState(root).getValue(BlockStateProperties.STAIRS_SHAPE) == shape,
                "roof fixture must have natural " + facing + " " + half + " " + shape + " at " + root);
            roots.put(root, level.getBlockState(root));
            motif++;
        }
        // Full blocks and a slab inside the exact same selected cuboid must remain untouched.
        put(CENTER.offset(2, 0, 2), Blocks.BRICKS.defaultBlockState());
        put(CENTER.offset(2, 0, 3), Blocks.STONE_SLAB.defaultBlockState());
        select(CENTER, CENTER.offset(42, 0, 27).below());
        Map<BlockPos, BlockState> before = snapshot();
        check(command("blocktone type:stairs to:brick_stairs[50],sandstone_stairs[50]") > 0, "type stair roof command rejected");
        Map<Block, Integer> distribution = new LinkedHashMap<>();
        for (BlockPos pos : stairs) {
            BlockState after = level.getBlockState(pos);
            distribution.merge(after.getBlock(), 1, Integer::sum);
            check(after.is(Blocks.BRICK_STAIRS) || after.is(Blocks.SANDSTONE_STAIRS), "roof stair not replaced at " + pos);
            check(after.getValues().toList().equals(before.get(pos).getValues().toList()), "roof orientation/half/corner state changed at " + pos);
        }
        check(stairs.size() == 72, "expected 72 mixed roof stairs");
        check(distribution.getOrDefault(Blocks.BRICK_STAIRS, 0) == 36
            && distribution.getOrDefault(Blocks.SANDSTONE_STAIRS, 0) == 36, "roof mixed palette missed exact 50/50 quotas");
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            if (!(entry.getValue().getBlock() instanceof StairBlock)) {
                check(level.getBlockState(entry.getKey()).equals(entry.getValue()), "type:stairs changed foundation/non-stair at " + entry.getKey());
            }
        }
        check(command("blocktone from:type:stair to:brick_stairs[50],sandstone_stairs[50]") > 0,
            "from:type:stair singular alias rejected");
        for (Map.Entry<BlockPos, BlockState> entry : roots.entrySet()) {
            check(level.getBlockState(entry.getKey()).getValues().toList().equals(entry.getValue().getValues().toList()),
                "repeat roof toning changed natural corner shape");
        }
    }

    private void calculate() throws Exception {
        reset(GameType.SURVIVAL);
        List<BlockPos> targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BlockPos pos = CENTER.offset(2 + i * 3, 0, 2);
            put(pos.below(), Blocks.STONE.defaultBlockState());
            put(pos, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, HORIZONTAL[i])
                .setValue(BlockStateProperties.HALF, i % 2 == 0 ? Half.TOP : Half.BOTTOM));
            targets.add(pos);
        }
        select(CENTER, CENTER.offset(12, 0, 3).below());
        player.getInventory().setItem(0, new ItemStack(Items.BRICK_STAIRS, 2));
        player.getInventory().setItem(1, new ItemStack(Items.SANDSTONE_STAIRS, 2));
        Map<BlockPos, BlockState> before = snapshot();
        Map<Item, Integer> inventory = InventoryMaterials.count(player);
        check(command("blocktone calc type:stairs to:brick_stairs,sandstone_stairs") > 0, "type-filtered calculate preview rejected");
        check(snapshot().equals(before) && InventoryMaterials.count(player).equals(inventory), "calculate preview changed roof or inventory");
        check(command("blocktone calc apply type:stairs to:brick_stairs,sandstone_stairs") > 0, "type-filtered calculate apply rejected");
        int bricks = 0;
        for (BlockPos pos : targets) {
            BlockState after = level.getBlockState(pos);
            check(after.getValues().toList().equals(before.get(pos).getValues().toList()), "calculate apply lost stair properties");
            check(after.is(Blocks.BRICK_STAIRS) || after.is(Blocks.SANDSTONE_STAIRS), "calculate apply missed stair");
            if (after.is(Blocks.BRICK_STAIRS)) bricks++;
            check(level.getBlockState(pos.below()).is(Blocks.STONE), "calculate apply changed roof support");
        }
        check(bricks == 2 && count(Items.BRICK_STAIRS) == 0 && count(Items.SANDSTONE_STAIRS) == 0
            && count(Items.OAK_STAIRS) == 4, "calculate apply failed exact palette/inventory settlement");
    }

    private void slabs() throws Exception {
        reset(GameType.SURVIVAL);
        List<BlockPos> positions = new ArrayList<>();
        int i = 0;
        for (SlabType type : new SlabType[]{SlabType.BOTTOM, SlabType.TOP, SlabType.DOUBLE}) {
            BlockPos pos = CENTER.offset(2 + i++ * 3, 0, 2);
            put(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type));
            positions.add(pos);
        }
        select(CENTER, CENTER.offset(10, 0, 3));
        player.getInventory().setItem(0, new ItemStack(Items.BRICK_SLAB, 4));
        Map<BlockPos, BlockState> before = snapshot();
        check(command("blocktone type:slab to:brick_slab[100]") > 0, "slab family command rejected");
        for (BlockPos pos : positions) {
            check(level.getBlockState(pos).is(Blocks.BRICK_SLAB)
                && level.getBlockState(pos).getValues().toList().equals(before.get(pos).getValues().toList()), "slab top/bottom/double was not preserved");
        }
        check(count(Items.BRICK_SLAB) == 0 && count(Items.STONE_SLAB) == 4, "double slab must charge and refund two slab items");
        check(command("blocktone type:slabs to:brick_slab[100]") > 0 && count(Items.STONE_SLAB) == 4,
            "repeated slab command duplicated inventory");
    }

    private void trapdoors() throws Exception {
        reset(GameType.CREATIVE);
        List<BlockPos> positions = new ArrayList<>();
        int i = 0;
        for (Direction facing : HORIZONTAL) for (Half half : Half.values()) for (boolean open : new boolean[]{false, true}) {
            BlockPos pos = CENTER.offset(2 + i % 8 * 5, 0, 2 + i / 8 * 5);
            boolean powered = open && i % 3 == 0;
            if (powered) put(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            put(pos, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half).setValue(BlockStateProperties.OPEN, open)
                .setValue(BlockStateProperties.POWERED, powered));
            positions.add(pos);
            i++;
        }
        select(CENTER, CENTER.offset(42, 0, 10).below());
        Map<BlockPos, BlockState> before = snapshot();
        check(command("blocktone type:trapdoors to:spruce_trapdoor[100]") > 0, "trapdoor family command rejected");
        for (BlockPos pos : positions) {
            check(level.getBlockState(pos).is(Blocks.SPRUCE_TRAPDOOR)
                && level.getBlockState(pos).getValues().toList().equals(before.get(pos).getValues().toList()),
                "trapdoor facing/half/open/powered was not preserved at " + pos);
        }
    }

    private void connections() throws Exception {
        for (ConnectionCase connection : List.of(
            new ConnectionCase("fences", Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE),
            new ConnectionCase("walls", Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL),
            new ConnectionCase("panes", Blocks.GLASS_PANE, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_stained_glass_pane"))))) {
            reset(GameType.CREATIVE);
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                BlockPos pos = CENTER.offset(3 + i * 5, 0, 3);
                put(pos, connection.source.defaultBlockState());
                for (int side = 0; side <= i; side++) put(pos.relative(HORIZONTAL[side]), Blocks.STONE.defaultBlockState());
                if (i % 2 == 0) put(pos.above(), Blocks.STONE.defaultBlockState());
                positions.add(pos);
            }
            normalize(positions);
            select(CENTER, CENTER.offset(24, 1, 6).below());
            Map<BlockPos, BlockState> before = snapshot();
            check(command("blocktone type:" + connection.type + " to:" + blockName(connection.destination) + "[100]") > 0,
                "connected " + connection.type + " family command rejected");
            for (BlockPos pos : positions) {
                check(level.getBlockState(pos).is(connection.destination)
                    && level.getBlockState(pos).getValues().toList().equals(before.get(pos).getValues().toList()),
                    "natural " + connection.type + " connections/post changed at " + pos);
            }
        }
        reset(GameType.CREATIVE);
        BlockPos gate = CENTER.offset(3, 0, 3);
        put(gate.east(), Blocks.COBBLESTONE_WALL.defaultBlockState());
        put(gate.west(), Blocks.COBBLESTONE_WALL.defaultBlockState());
        put(gate.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        put(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.POWERED, true));
        normalize(List.of(gate));
        BlockState before = level.getBlockState(gate);
        check(before.getValue(BlockStateProperties.IN_WALL), "gate fixture must naturally be in a wall");
        select(gate, gate);
        check(command("blocktone type:fence_gates to:spruce_fence_gate[100]") > 0, "fence gate category command rejected");
        check(level.getBlockState(gate).is(Blocks.SPRUCE_FENCE_GATE)
            && level.getBlockState(gate).getValues().toList().equals(before.getValues().toList()), "gate facing/open/powered/in_wall changed");

        reset(GameType.CREATIVE);
        for (int i = 0; i < Direction.Axis.values().length; i++) {
            put(CENTER.offset(2 + i * 3, 0, 2), Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.values()[i]));
        }
        select(CENTER, CENTER.offset(10, 0, 3));
        Map<BlockPos, BlockState> logs = snapshot();
        check(command("blocktone type:logs to:spruce_log[100]") > 0, "log family command rejected");
        for (Map.Entry<BlockPos, BlockState> entry : logs.entrySet()) {
            if (entry.getValue().is(Blocks.OAK_LOG)) check(level.getBlockState(entry.getKey()).is(Blocks.SPRUCE_LOG)
                && level.getBlockState(entry.getKey()).getValues().toList().equals(entry.getValue().getValues().toList()), "log axis changed");
        }
    }

    private void waterlogged() throws Exception {
        for (ConnectionCase test : List.of(
            new ConnectionCase("stairs", Blocks.OAK_STAIRS, Blocks.BRICK_STAIRS),
            new ConnectionCase("slabs", Blocks.OAK_SLAB, Blocks.BRICK_SLAB),
            new ConnectionCase("trapdoors", Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR),
            new ConnectionCase("fences", Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE),
            new ConnectionCase("walls", Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL),
            new ConnectionCase("panes", Blocks.GLASS_PANE, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_stained_glass_pane"))))) {
            reset(GameType.SURVIVAL);
            BlockPos pos = CENTER.offset(3, 0, 3);
            put(pos, test.source.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            normalize(List.of(pos));
            BlockState before = level.getBlockState(pos);
            select(pos, pos);
            player.getInventory().setItem(0, new ItemStack(test.destination.asItem(), 1));
            check(command("blocktone type:" + test.type + " to:" + blockName(test.destination) + "[100]") > 0,
                "waterlogged " + test.type + " replacement rejected");
            check(level.getBlockState(pos).is(test.destination) && level.getBlockState(pos).getValues().toList().equals(before.getValues().toList())
                && !level.getFluidState(pos).isEmpty(), "waterlogged " + test.type + " state/water was lost");
            check(count(test.destination.asItem()) == 0 && count(test.source.asItem()) == 1, "waterlogged replacement inventory wrong");
        }
    }

    private void explicitAirAndLiquids() throws Exception {
        reset(GameType.CREATIVE);
        BlockPos wet = CENTER.offset(2, 0, 2);
        BlockPos dry = wet.east();
        BlockPos wetStair = wet.east(2);
        put(wet, Blocks.WATER.defaultBlockState());
        put(wetStair, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        select(wet, wetStair);
        check(command("blocktone type:liquid to:bricks[100]") > 0, "explicit liquid category rejected");
        check(level.getBlockState(wet).is(Blocks.BRICKS) && level.getBlockState(dry).isAir()
            && level.getBlockState(wetStair).is(Blocks.OAK_STAIRS) && level.getBlockState(wetStair).getValue(BlockStateProperties.WATERLOGGED),
            "liquid category must select pure fluid and exclude waterlogged stairs/air");
        check(command("blocktone type:air to:bricks[100]") > 0 && level.getBlockState(dry).is(Blocks.BRICKS),
            "air category did not explicitly fill empty cell");

        reset(GameType.CREATIVE);
        BlockPos slab = CENTER.offset(2, 0, 2);
        put(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
        put(slab.east(), Blocks.STONE.defaultBlockState());
        select(slab, slab.east());
        check(command("blocktone type:blocks to:bricks[100]") > 0, "full blocks category rejected");
        check(level.getBlockState(slab).is(Blocks.STONE_SLAB) && level.getBlockState(slab.east()).is(Blocks.BRICKS),
            "type:blocks must leave category-specific double slabs untouched");

        reset(GameType.CREATIVE);
        BlockPos row = CENTER.offset(2, 0, 2);
        put(row, Blocks.OAK_STAIRS.defaultBlockState());
        put(row.east(), Blocks.STONE_SLAB.defaultBlockState());
        put(row.east(2), Blocks.STONE.defaultBlockState());
        put(row.east(3), Blocks.WATER.defaultBlockState());
        select(row, row.east(4));
        check(command("blocktone type:stairs,slabs,blocks,liquids,air to:bricks[100]") > 0, "comma-separated mixed type list rejected");
        for (int i = 0; i < 5; i++) check(level.getBlockState(row.east(i)).is(Blocks.BRICKS), "mixed type list missed category at " + i);
    }

    private void failures() throws Exception {
        reset(GameType.SURVIVAL);
        BlockPos pos = CENTER.offset(3, 0, 3);
        put(pos, Blocks.OAK_STAIRS.defaultBlockState());
        put(pos.east(3), Blocks.OAK_STAIRS.defaultBlockState());
        select(pos, pos.east(3));
        player.getInventory().setItem(0, new ItemStack(Items.BRICK_STAIRS, 1));
        Map<BlockPos, BlockState> before = snapshot();
        Map<Item, Integer> inventory = InventoryMaterials.count(player);
        for (String specification : List.of("type:stairs to:brick_stairs[100]", "type:unknown to:brick_stairs[100]",
            "type:stairs, to:brick_stairs[100]", "type:stairs from:oak_stairs to:brick_stairs[100]")) {
            check(command("blocktone " + specification) == 0, "invalid/shortage type command must fail: " + specification);
            check(snapshot().equals(before) && InventoryMaterials.count(player).equals(inventory),
                "invalid/shortage command partially changed world or inventory: " + specification);
        }
        put(pos.east(), Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.BRICK_SLAB, 1));
        before = snapshot();
        inventory = InventoryMaterials.count(player);
        check(command("blocktone type:slabs to:brick_slab[100]") == 0, "one slab item must not fund a double slab");
        check(snapshot().equals(before) && InventoryMaterials.count(player).equals(inventory), "double slab shortage was not atomic");
    }

    private void reset(GameType mode) {
        player.setGameMode(mode);
        player.getInventory().clearContent();
        BlockToolsMod.STATES.remove(player.getUUID());
        BlockToolsMod.TUNNELS.remove(player.getUUID());
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -2; y <= 2; y++) {
            put(CENTER.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private void select(BlockPos first, BlockPos second) {
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 1, first);
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 2, second);
    }

    private void normalize(List<BlockPos> positions) {
        for (int pass = 0; pass < 2; pass++) for (BlockPos pos : positions) {
            put(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos));
        }
    }

    private Map<BlockPos, BlockState> snapshot() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -2; y <= 2; y++) {
            BlockPos pos = CENTER.offset(x, y, z);
            states.put(pos, level.getBlockState(pos));
        }
        return states;
    }

    private void put(BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_SKIP_ALL_SIDEEFFECTS); }
    private int command(String command) throws Exception {
        return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    }
    private int count(Item item) { return InventoryMaterials.count(player).getOrDefault(item, 0); }
    private String blockName(Block block) { return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toShortString(); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
    private record ConnectionCase(String type, Block source, Block destination) { }
}
