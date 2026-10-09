package com.thenathe.blocktools.material;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;

/** Regression checks for selecting a roof by geometry and retaining every supported state. */
public final class TypeAndStateTest {
    private TypeAndStateTest() { }

    public static void run() {
        testAliases();
        testCategoriesAndFlags();
        testSelectorsAndErrors();
        testAllShapeStates();
        testIncompatibleProperties();
    }

    private static void testAliases() {
        Map<BlockType, List<String>> aliases = Map.ofEntries(
            Map.entry(BlockType.STAIRS, List.of("stair", "stairs")),
            Map.entry(BlockType.SLABS, List.of("slab", "slabs")),
            Map.entry(BlockType.TRAPDOORS, List.of("trapdoor", "trapdoors")),
            Map.entry(BlockType.FENCES, List.of("fence", "fences")),
            Map.entry(BlockType.WALLS, List.of("wall", "walls")),
            Map.entry(BlockType.PANES, List.of("pane", "panes")),
            Map.entry(BlockType.FENCE_GATES, List.of("fence_gate", "fence_gates", "gate", "gates")),
            Map.entry(BlockType.LOGS, List.of("log", "logs")),
            Map.entry(BlockType.BLOCKS, List.of("block", "blocks")),
            Map.entry(BlockType.LIQUIDS, List.of("liquid", "liquids", "fluid", "fluids")),
            Map.entry(BlockType.AIR, List.of("air"))
        );
        aliases.forEach((expected, spellings) -> spellings.forEach(spelling -> {
            require(BlockType.parse(spelling) == expected, "wrong type for alias " + spelling);
            require(BlockType.parse(" " + spelling.toUpperCase(java.util.Locale.ROOT) + " ") == expected,
                "type alias was not case/space insensitive: " + spelling);
            require(BlockFilter.parse("type:" + spelling).canonical().equals("type:" + expected.key()),
                "type alias canonicalization changed: " + spelling);
        }));
    }

    private static void testCategoriesAndFlags() {
        Map<BlockType, Block> representative = Map.ofEntries(
            Map.entry(BlockType.STAIRS, Blocks.OAK_STAIRS),
            Map.entry(BlockType.SLABS, Blocks.STONE_SLAB),
            Map.entry(BlockType.TRAPDOORS, Blocks.OAK_TRAPDOOR),
            Map.entry(BlockType.FENCES, Blocks.OAK_FENCE),
            Map.entry(BlockType.WALLS, Blocks.COBBLESTONE_WALL),
            Map.entry(BlockType.PANES, Blocks.GLASS_PANE),
            Map.entry(BlockType.FENCE_GATES, Blocks.OAK_FENCE_GATE),
            Map.entry(BlockType.BLOCKS, Blocks.STONE),
            Map.entry(BlockType.LIQUIDS, Blocks.WATER),
            Map.entry(BlockType.AIR, Blocks.AIR)
        );
        representative.forEach((type, block) -> {
            BlockFilter filter = BlockFilter.parse("type:" + type.key());
            require(filter.matches(block), "type missed representative " + type);
            representative.forEach((otherType, otherBlock) -> {
                if (type != otherType) require(!filter.matches(otherBlock),
                    type + " incorrectly selected " + otherType);
            });
            require(!filter.isAll() && !filter.isDefault(), "typed filter was treated as from:any");
        });
        BlockFilter cubes = BlockFilter.parse("type:blocks");
        require(cubes.matches(Blocks.GLASS) && cubes.matches(Blocks.BRICKS), "full cubes were omitted");
        require(!cubes.matches(Blocks.TORCH) && !cubes.matches(Blocks.SHORT_GRASS),
            "non-cube decoration was classified as a full block");
        BlockFilter roof = BlockFilter.parse("type:stairs");
        require(roof.matches(Blocks.OAK_STAIRS) && roof.matches(Blocks.BRICK_STAIRS)
            && !roof.matches(Blocks.OAK_PLANKS) && !roof.matches(Blocks.BRICKS),
            "stair roof filter selected its supporting blocks");
        BlockFilter combined = BlockFilter.parse("type:stairs,slabs,blocks,liquids,air");
        for (Block block : List.of(Blocks.OAK_STAIRS, Blocks.STONE_SLAB, Blocks.STONE,
                Blocks.WATER, Blocks.LAVA, Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR)) {
            require(combined.matches(block), "combined type filter missed " + block);
        }
        require(!combined.matches(Blocks.OAK_TRAPDOOR), "type union included an unselected shape");
        require(combined.includesAir() && combined.includesLiquids(), "explicit air/liquid flags missing");
        require(combined.canonical().equals("type:air,blocks,liquids,slabs,stairs"),
            "type canonicalization did not sort normalized categories");
        require(!roof.includesAir() && !roof.includesLiquids(), "stair filter enabled air/liquid edits");
        require(BlockFilter.parse("type:air").includesAir()
            && !BlockFilter.parse("type:air").includesLiquids(), "air-only flags incorrect");
        require(BlockFilter.parse("type:liquids").includesLiquids()
            && !BlockFilter.parse("type:liquids").includesAir(), "liquid-only flags incorrect");
        require(!BlockFilter.parse("any").includesLiquids()
            && !BlockFilter.parse("any+air").includesLiquids(), "broad source implicitly opted into liquids");
        require(BlockFilter.parse("water").includesLiquids() && BlockFilter.parse("lava").includesLiquids(),
            "explicit liquid block IDs lost their opt-in");
    }

    private static void testSelectorsAndErrors() {
        NamedSelectors first = NamedSelectors.parse("type:stairs,slabs to:brick_stairs,brick_slab");
        NamedSelectors last = NamedSelectors.parse("to:brick_stairs,brick_slab type:stairs,slabs");
        NamedSelectors nested = NamedSelectors.parse("from:type:stairs,slabs to:brick_stairs,brick_slab");
        require(first.equals(last) && first.equals(nested), "typed source changed with clause order/nesting");
        require(first.from().equals("type:stairs,slabs"), "typed source lost its prefix");
        expectFailure(() -> NamedSelectors.parse("type:stairs from:stone to:brick_stairs"), "type:");
        expectFailure(() -> NamedSelectors.parse("from:stone type:stairs to:brick_stairs"), "from:");
        expectFailure(() -> NamedSelectors.parse("type:stairs type:slabs to:brick_stairs"), "source selectors");
        expectFailure(() -> NamedSelectors.parse("type: to:brick_stairs"), "type list");
        expectFailure(() -> NamedSelectors.parse("type:stairs"), "to: selector");
        expectFailure(() -> BlockFilter.parse("type:"), "empty entry");
        expectFailure(() -> BlockFilter.parse("type:stairs,"), "empty entry");
        expectFailure(() -> BlockFilter.parse("type:stairs,,slabs"), "empty entry");
        expectFailure(() -> BlockFilter.parse("type:stairs,stair"), "more than once");
        expectFailure(() -> BlockFilter.parse("type:fluid,liquids"), "more than once");
        expectFailure(() -> BlockFilter.parse("type:roof"), "Unknown source type");
        expectFailure(() -> BlockFilter.parse("type:stairs,stone"), "Unknown source type");
        expectFailure(() -> BlockFilter.parse("type:any"), "Unknown source type");
    }

    private static void testAllShapeStates() {
        // Iterate the complete state definitions: every facing, half, corner shape,
        // waterlogging flag, connection height, open/powered flag, and log axis.
        int states = 0;
        states += assertAllStates(Blocks.OAK_STAIRS, Blocks.BRICK_STAIRS,
            BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF,
            BlockStateProperties.STAIRS_SHAPE, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.STONE_SLAB, Blocks.BRICK_SLAB,
            BlockStateProperties.SLAB_TYPE, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.OAK_TRAPDOOR, Blocks.IRON_TRAPDOOR,
            BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF,
            BlockStateProperties.OPEN, BlockStateProperties.POWERED, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE,
            BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.COBBLESTONE_WALL, Blocks.BRICK_WALL,
            BlockStateProperties.UP, BlockStateProperties.NORTH_WALL, BlockStateProperties.EAST_WALL,
            BlockStateProperties.SOUTH_WALL, BlockStateProperties.WEST_WALL, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.GLASS_PANE, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_stained_glass_pane")),
            BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.WATERLOGGED);
        states += assertAllStates(Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE,
            BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.OPEN,
            BlockStateProperties.POWERED, BlockStateProperties.IN_WALL);
        states += assertAllStates(Blocks.OAK_LOG, Blocks.BIRCH_LOG, BlockStateProperties.AXIS);
        require(states >= 570, "shape-state coverage unexpectedly shrank: " + states);
        System.out.println("Type filters and " + states + " replacement shape states passed.");
    }

    private static int assertAllStates(Block source, Block destination, Property<?>... properties) {
        List<BlockState> states = source.getStateDefinition().getPossibleStates();
        for (BlockState original : states) {
            BlockState replacement = ReplacementStates.preserve(original, destination);
            require(replacement.is(destination), "replacement kept the source material: " + original);
            for (Property<?> property : properties) {
                require(replacement.getValue(property).equals(original.getValue(property)),
                    "replacement changed " + property.getName() + " for " + original + " -> " + replacement);
            }
            require(ReplacementStates.preserve(original, source).equals(original),
                "same-material replacement reset a state: " + original);
        }
        return states.size();
    }

    private static void testIncompatibleProperties() {
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
            .setValue(BlockStateProperties.WATERLOGGED, true);
        require(ReplacementStates.preserve(stair, Blocks.BRICKS).equals(Blocks.BRICKS.defaultBlockState()),
            "properties absent from a full block were applied");
        require(ReplacementStates.preserve(Blocks.BRICKS.defaultBlockState(), Blocks.OAK_STAIRS)
            .equals(Blocks.OAK_STAIRS.defaultBlockState()), "missing source properties changed destination defaults");
        BlockState vertical = Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        BlockState horizontal = Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST);
        require(ReplacementStates.preserve(vertical, Blocks.BRICK_STAIRS)
            .getValue(BlockStateProperties.HORIZONTAL_FACING)
            == Blocks.BRICK_STAIRS.defaultBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING),
            "unsupported vertical facing did not retain the stair default");
        require(ReplacementStates.preserve(horizontal, Blocks.BRICK_STAIRS)
            .getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST,
            "same-name facing properties with different value domains were not copied");
        BlockState upperDoor = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.OPEN, true);
        BlockState trapdoor = ReplacementStates.preserve(upperDoor, Blocks.OAK_TRAPDOOR);
        require(trapdoor.getValue(BlockStateProperties.HALF)
            == Blocks.OAK_TRAPDOOR.defaultBlockState().getValue(BlockStateProperties.HALF),
            "different half value domains were incorrectly coerced");
        require(trapdoor.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
            && trapdoor.getValue(BlockStateProperties.OPEN),
            "one incompatible property prevented compatible properties from being copied");
    }

    private static void expectFailure(Runnable action, String messagePart) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + messagePart);
        } catch (IllegalArgumentException exception) {
            require(exception.getMessage().contains(messagePart), "unexpected error: " + exception.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
