package com.thenathe.blocktools;

import com.thenathe.blocktools.command.BlockSuggestionsTest;
import com.thenathe.blocktools.config.ModConfig;
import com.thenathe.blocktools.inventory.MaterialLedger;
import com.thenathe.blocktools.inventory.InventoryMaterialsTest;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.NamedSelectors;
import com.thenathe.blocktools.material.OverlayPalette;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.material.PathSelectors;
import com.thenathe.blocktools.material.ShuffleSelector;
import com.thenathe.blocktools.path.PathOrientationTest;
import com.thenathe.blocktools.state.SelectionStateTest;
import com.thenathe.blocktools.tone.ToneService;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LogicTest {
	private LogicTest() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		testCanonicalAndNamespacedIds();
		testMessageFormatting();
		testExactDistribution();
		testPaletteValidation();
		testPathPaletteAndSelectors();
		testPathDefaults();
		testHorizontalSelection();
		testVerticalSelection();
		testCuboidSelection();
		testInvalidSelection();
		testSourceFilters();
		testShuffle();
		testNamedSelectorsAndRemainder();
		testCalculateDistribution();
		testCalculatedAssignmentsAreToned();
		testNetMaterialLedger();
		InventoryMaterialsTest.run();
		MaterialSafetyTest.run();
		com.thenathe.blocktools.world.WorldEditsTest.run();
		BlockSuggestionsTest.run();
		com.thenathe.blocktools.material.TypeAndStateTest.run();
		com.thenathe.blocktools.material.ExpandedTypesTest.run();
		com.thenathe.blocktools.command.AirDestinationTest.run();
		PathOrientationTest.run();
		SelectionStateTest.run();
		com.thenathe.blocktools.selection.SelectionVisualizationTest.run();
		com.thenathe.blocktools.selection.ToolPreviewsTest.run();
		System.out.println("Block Tone & Path Tools logic checks passed.");
	}

	private static void testCanonicalAndNamespacedIds() {
		Palette palette = Palette.parse("bricks:50,minecraft:terracotta:30,stone:10,granite:10", false, false);
		require("bricks:50,terracotta:30,stone:10,granite:10".equals(palette.canonical()), "canonical IDs changed");
		expectFailure(() -> Palette.parse("brick:50,terracotta:30,stone:10,granite:10", false, false), "Unknown block");
		expectFailure(() -> Palette.parse("bricks:50,terracotta:30,stone:10,graninte:10", false, false), "Unknown block");
	}

	private static void testMessageFormatting() {
		Component message = ChatStyling.tagged(
			"Blocktone", ChatFormatting.LIGHT_PURPLE,
			"Command: `/blocktone shuffle from:stone,granite`\n  Point: (1, 2, 3)",
			ChatFormatting.WHITE
		);
		require(!message.getString().contains("`"), "command styling markup leaked into chat text");
		require(hasColor(message, "/blocktone shuffle from:stone,granite", ChatFormatting.GRAY),
			"command text was not gray");
		require(hasColor(message, "Command:", ChatFormatting.YELLOW), "information label was not differentiated");
		require(hasColor(message, "1", ChatFormatting.RED), "X coordinate did not use its own color");
		require(hasColor(message, "2", ChatFormatting.GREEN), "Y coordinate did not use its own color");
		require(hasColor(message, "3", ChatFormatting.AQUA), "Z coordinate did not use its own color");
	}

	private static boolean hasColor(final Component component, final String text, final ChatFormatting color) {
		TextColor expected = TextColor.fromLegacyFormat(color);
		return component.toFlatList().stream().anyMatch(part ->
			text.equals(part.getString()) && expected.equals(part.getStyle().getColor()));
	}

	private static void testExactDistribution() {
		Palette palette = Palette.parse("stone:33,granite:33,diorite:34", false, false);
		List<Palette.Entry> assignments = palette.exactAssignments(101, 42L);
		Map<Object, Integer> counts = new HashMap<>();
		for (Palette.Entry entry : assignments) counts.merge(entry.block(), 1, Integer::sum);
		require(assignments.size() == 101, "assignment count changed");
		require(counts.get(Blocks.STONE) == 33, "stone quota incorrect");
		require(counts.get(Blocks.GRANITE) == 33, "granite quota incorrect");
		require(counts.get(Blocks.DIORITE) == 35, "diorite quota incorrect");
	}

	private static void testPaletteValidation() {
		expectFailure(() -> Palette.parse("stone:90,granite:5", false, false), "total");
		expectFailure(() -> Palette.parse("stone:50,stone:50", false, false), "more than once");
		expectFailure(() -> Palette.parse("stone:50,missing_block:50", false, false), "Unknown block");
		expectFailure(() -> Palette.parse("stone:100", false, true), "not a stair");
	}

	private static void testPathPaletteAndSelectors() {
		Palette path = Palette.parseBracketed("dirt_path[60],cobblestone[40]", true, false);
		require("dirt_path[60],cobblestone[40]".equals(path.canonical()), "bracketed Path Tool palette changed");
		Palette stairs = Palette.parseBracketed("cobblestone_stairs[100]", false, true);
		require("cobblestone_stairs[100]".equals(stairs.canonical()), "bracketed stair palette changed");
		expectFailure(() -> Palette.parseBracketed("stone:100", true, false), "material[percent]");

		PathSelectors forward = PathSelectors.parse(
			"from:grass_block,dirt to:dirt_path[60],cobblestone[40] stairs:cobblestone_stairs[100]"
		);
		PathSelectors reversed = PathSelectors.parse(
			"stairs:cobblestone_stairs[100] to:dirt_path[60],cobblestone[40] from:grass_block,dirt"
		);
		require(forward.equals(reversed), "Path Tool selectors were not position-independent");
		require(PathSelectors.parse("to:stone[100]").from() == null, "omitted Path Tool from: did not remain optional");
		expectFailure(() -> PathSelectors.parse("from:dirt"), "to: selector");
		expectFailure(() -> PathSelectors.parse("stairs:cobblestone_stairs[100] from:dirt"), "to: selector");
		expectFailure(() -> PathSelectors.parse("stone[100]"), "to: selector");
		expectFailure(() -> PathSelectors.parse("to:stone[100] to:dirt[100]"), "appears more than once");
	}

	private static void testPathDefaults() {
		ModConfig fresh = new ModConfig();
		fresh.normalize();
		require(fresh.defaultPathWidth == 3, "new Path Tool width did not default to 3");
		require(fresh.configVersion == 4, "new configuration version was not normalized");
        require(fresh.enablePolymerTools, "Polymer integration must default on");
        ModConfig customized = new ModConfig();
        customized.configVersion = 3;
        customized.selectionItem = "minecraft:feather";
        customized.defaultPathWidth = 1;
        customized.enablePolymerTools = false;
        customized.normalize();
        require(customized.configVersion == 4 && customized.defaultPathWidth == 1
            && "minecraft:feather".equals(customized.selectionItem) && !customized.enablePolymerTools,
            "schema4 migration preserves deliberate custom settings");

		ModConfig legacy = new ModConfig();
		legacy.configVersion = null;
		legacy.defaultPathWidth = 1;
		legacy.normalize();
		require(legacy.defaultPathWidth == 3, "legacy default width 1 was not migrated to 3");
	}

	private static void testHorizontalSelection() {
		List<BlockPos> positions = ToneService.cuboid(List.of(
			new BlockPos(0, 64, 0), new BlockPos(2, 64, 3)
		), 100);
		require(positions.size() == 12, "horizontal selection area incorrect");
	}

	private static void testVerticalSelection() {
		List<BlockPos> positions = ToneService.cuboid(List.of(
			new BlockPos(5, 60, 7), new BlockPos(5, 62, 9)
		), 100);
		require(positions.size() == 9, "vertical selection area incorrect");
	}

	private static void testCuboidSelection() {
		List<BlockPos> positions = ToneService.cuboid(List.of(
			new BlockPos(0, 10, 0), new BlockPos(2, 12, 3)
		), 100);
		require(positions.size() == 36, "three-dimensional cuboid volume incorrect");
	}

	private static void testInvalidSelection() {
		expectFailure(() -> ToneService.cuboid(List.of(new BlockPos(0, 0, 0)), 100), "two opposite corners");
		expectFailure(() -> ToneService.cuboid(List.of(
			new BlockPos(0, 0, 0), new BlockPos(10, 10, 10)
		), 100), "configured maximum");
	}

	private static void testSourceFilters() {
		BlockFilter any = BlockFilter.parse("any");
		require(BlockFilter.parse(null).isDefault() && BlockFilter.parse("").isDefault(),
			"missing from: did not default to from:any");
		require(any.isAll() && any.isDefault() && any.matches(Blocks.STONE) && any.matches(Blocks.GRANITE),
			"any source filter did not match non-air blocks");
		require(!any.matches(Blocks.AIR) && !any.includesAir(), "from:any unexpectedly included air");
		require("any".equals(any.canonical()), "from:any canonical form changed");
		BlockFilter anyWithAir = BlockFilter.parse("any+air");
		require(anyWithAir.matches(Blocks.STONE) && anyWithAir.matches(Blocks.AIR) && anyWithAir.includesAir(),
			"from:any+air did not include both blocks and air");
		require(!anyWithAir.isDefault() && "any+air".equals(anyWithAir.canonical()),
			"from:any+air was treated as the default non-air filter");
		require(BlockFilter.parse("all+air").matches(Blocks.AIR), "all+air compatibility spelling lost air");
		BlockFilter airOnly = BlockFilter.parse("air");
		require(airOnly.matches(Blocks.AIR) && !airOnly.matches(Blocks.STONE), "explicit from:air did not select only air");
		BlockFilter one = BlockFilter.parse("stone");
		require(one.matches(Blocks.STONE) && !one.matches(Blocks.GRANITE), "single source filter matched the wrong blocks");
		BlockFilter several = BlockFilter.parse("minecraft:stone,granite,diorite");
		require(several.matches(Blocks.STONE) && several.matches(Blocks.GRANITE) && several.matches(Blocks.DIORITE),
			"multiple source filter omitted a block");
		expectFailure(() -> BlockFilter.parse("stone,stone"), "more than once");
		BlockFilter withAir = BlockFilter.parse("stone,air");
		require(withAir.matches(Blocks.STONE) && withAir.matches(Blocks.AIR), "explicit source list omitted air");
		expectFailure(() -> BlockFilter.parse("any,stone"), "any/any+air by itself");
		expectFailure(() -> BlockFilter.parse("graninte"), "Unknown source block");
	}

	private static void testShuffle() {
		ShuffleSelector selector = ShuffleSelector.parse("from:stone, granite,polished_granite");
		require(selector.from().matches(Blocks.STONE) && selector.from().matches(Blocks.GRANITE),
			"shuffle source selector omitted a requested block");
		expectFailure(() -> ShuffleSelector.parse(""), "requires from:<blocks>");
		expectFailure(() -> ShuffleSelector.parse("stone,granite"), "requires from:<blocks>");
		expectFailure(() -> ShuffleSelector.parse("from:stone to:granite"), "exactly one");

		BlockState northStair = Blocks.STONE_STAIRS.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		BlockState southStair = Blocks.STONE_STAIRS.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		List<BlockState> existing = List.of(
			Blocks.STONE.defaultBlockState(),
			Blocks.GRANITE.defaultBlockState(),
			northStair,
			southStair
		);
		List<BlockState> shuffled = ToneService.shuffledStates(existing, 42L);
		require(!shuffled.equals(existing), "shuffle returned the original arrangement");
		for (BlockState state : existing) {
			require(java.util.Collections.frequency(shuffled, state) == java.util.Collections.frequency(existing, state),
				"shuffle changed an exact block-state count");
		}
		List<BlockState> identical = java.util.Collections.nCopies(5, Blocks.BRICKS.defaultBlockState());
		require(ToneService.shuffledStates(identical, 42L).equals(identical),
			"shuffle changed an all-identical block-state list");
	}

	private static void testCalculateDistribution() {
		OverlayPalette materials = OverlayPalette.parse("bricks,iron_block,quartz_block");
		Map<net.minecraft.world.item.Item, Integer> inventory = Map.of(
			Blocks.IRON_BLOCK.asItem(), 100,
			Blocks.QUARTZ_BLOCK.asItem(), 100
		);
		Map<net.minecraft.world.level.block.Block, Integer> allBricks = Map.of(Blocks.BRICKS, 1_000);
		Map<net.minecraft.world.level.block.Block, Integer> result = ToneService.calculatedCounts(
			1_000, materials, inventory, allBricks
		);
		require(result.get(Blocks.BRICKS) == 800, "placed bricks did not supply the calculate remainder");
		require(result.get(Blocks.IRON_BLOCK) == 100, "calculate iron count incorrect");
		require(result.get(Blocks.QUARTZ_BLOCK) == 100, "calculate quartz count incorrect");

		Map<net.minecraft.world.level.block.Block, Integer> withPlacedAccents = Map.of(
			Blocks.BRICKS, 900,
			Blocks.IRON_BLOCK, 50,
			Blocks.QUARTZ_BLOCK, 50
		);
		result = ToneService.calculatedCounts(1_000, materials, inventory, withPlacedAccents);
		require(result.get(Blocks.BRICKS) == 700, "placed accents did not reduce the dominant existing material");
		require(result.get(Blocks.IRON_BLOCK) == 150, "placed iron blocks were not included in calculate mode");
		require(result.get(Blocks.QUARTZ_BLOCK) == 150, "placed quartz blocks were not included in calculate mode");

		result = ToneService.calculatedCounts(100, materials, inventory, Map.of(Blocks.IRON_BLOCK, 10));
		require(result.values().stream().mapToInt(Integer::intValue).sum() == 100,
			"oversupplied calculate distribution did not fit the target");
		require(result.get(Blocks.IRON_BLOCK) >= 10, "oversupplied calculate distribution discarded placed blocks");

		Map<net.minecraft.world.item.Item, Integer> equalInventory = Map.of(
			Blocks.BRICKS.asItem(), 100,
			Blocks.GRANITE.asItem(), 100,
			Blocks.POLISHED_GRANITE.asItem(), 100
		);
		OverlayPalette equalMaterials = OverlayPalette.parse("bricks,granite,polished_granite");
		result = ToneService.calculatedCounts(300, equalMaterials, equalInventory, Map.of());
		require(result.get(Blocks.BRICKS) == 100, "calculate mode ignored carried bricks");
		require(result.get(Blocks.GRANITE) == 100, "equal carried granite was not one third");
		require(result.get(Blocks.POLISHED_GRANITE) == 100, "equal carried polished granite was not one third");

		Map<net.minecraft.world.item.Item, Integer> sprinkleInventory = Map.of(
			Blocks.GRANITE.asItem(), 500,
			Blocks.POLISHED_GRANITE.asItem(), 500
		);
		result = ToneService.calculatedCounts(
			10_000, equalMaterials, sprinkleInventory, Map.of(Blocks.BRICKS, 10_000)
		);
		require(result.get(Blocks.BRICKS) == 9_000, "dominant placed brick did not fill the calculate remainder");
		require(result.get(Blocks.GRANITE) == 500, "calculate mode did not use all carried granite");
		require(result.get(Blocks.POLISHED_GRANITE) == 500, "calculate mode did not use all carried polished granite");

		result = ToneService.calculatedCounts(1_000, equalMaterials, sprinkleInventory, Map.of());
		require(result.values().stream().mapToInt(Integer::intValue).sum() == 1_000,
			"available automatic materials did not fill their exact capacity");
		result = ToneService.calculatedCounts(10_000, equalMaterials, sprinkleInventory, Map.of());
		require(result.values().stream().mapToInt(Integer::intValue).sum() == 1_000,
			"calculate mode invented materials instead of retaining an unsupplied remainder");

		Map<net.minecraft.world.item.Item, Integer> relativeInventory = Map.of(
			Blocks.GRANITE.asItem(), 100,
			Blocks.POLISHED_GRANITE.asItem(), 200
		);
		result = ToneService.calculatedCounts(150, equalMaterials, relativeInventory, Map.of());
		require(result.get(Blocks.GRANITE) == 50 && result.get(Blocks.POLISHED_GRANITE) == 100,
			"oversupplied carried materials were not fitted relative to one another");
	}

	private static void testNamedSelectorsAndRemainder() {
		NamedSelectors forward = NamedSelectors.parse("from:bricks to:iron_block[10],quartz_block[10]");
		NamedSelectors reversed = NamedSelectors.parse("to:iron_block[10],quartz_block[10] from:bricks");
		require(forward.equals(reversed), "from: and to: selectors were not position-independent");
		require("bricks".equals(forward.from()), "named source selector changed");
		require(NamedSelectors.parse("to:iron_block[],quartz_block[]").from() == null,
			"to:-only selector did not leave from: optional");
		expectFailure(() -> NamedSelectors.parse("from:bricks iron_block[10],quartz_block[10]"), "to: selector");
		expectFailure(() -> NamedSelectors.parse("from:bricks"), "to: selector");
		expectFailure(() -> NamedSelectors.parse("to:"), "needs a material list");
		expectFailure(() -> NamedSelectors.parse("to:bricks to:granite"), "appears more than once");
		expectFailure(() -> NamedSelectors.parse("from:stone from:granite to:bricks"), "appears more than once");

		OverlayPalette palette = OverlayPalette.parse(forward.to());
		OverlayPalette.Distribution thousand = palette.exactDistribution(1_000);
		require(thousand.materialCounts().get(Blocks.IRON_BLOCK) == 100, "named iron percentage incorrect");
		require(thousand.materialCounts().get(Blocks.QUARTZ_BLOCK) == 100, "named quartz percentage incorrect");
		require(thousand.unchangedCount() == 800, "named remainder did not preserve existing material");

		OverlayPalette.Distribution seven = palette.exactDistribution(7);
		require(seven.materialCounts().get(Blocks.IRON_BLOCK) == 1, "uneven iron quota rounding incorrect");
		require(seven.materialCounts().get(Blocks.QUARTZ_BLOCK) == 1, "uneven quartz quota rounding incorrect");
		require(seven.unchangedCount() == 5, "uneven remainder rounding incorrect");

		OverlayPalette automatic = OverlayPalette.parse("bricks,granite[],polished_granite");
		OverlayPalette.Distribution thirds = automatic.exactDistribution(300);
		require(thirds.materialCounts().get(Blocks.BRICKS) == 100, "bare automatic material did not receive one third");
		require(thirds.materialCounts().get(Blocks.GRANITE) == 100, "blank [] material did not receive one third");
		require(thirds.materialCounts().get(Blocks.POLISHED_GRANITE) == 100, "automatic remainder was not split evenly");
		require(thirds.unchangedCount() == 0, "an all-automatic palette retained an unexpected remainder");

		OverlayPalette additive = OverlayPalette.parse("bricks[20],granite[],polished_granite");
		OverlayPalette.Distribution additiveDistribution = additive.exactDistribution(1_000);
		require(additiveDistribution.materialCounts().get(Blocks.BRICKS) == 200, "explicit additive percentage changed");
		require(additiveDistribution.materialCounts().get(Blocks.GRANITE) == 400, "first automatic remainder share changed");
		require(additiveDistribution.materialCounts().get(Blocks.POLISHED_GRANITE) == 400, "second automatic remainder share changed");
		require(additiveDistribution.unchangedCount() == 0, "automatic materials did not claim the full remainder");

		List<net.minecraft.world.level.block.Block> assignments = ToneService.overlayAssignments(
			java.util.Collections.nCopies(1_000, Blocks.BRICKS), thousand.materialCounts(), 42L
		);
		require(assignments.stream().filter(Blocks.IRON_BLOCK::equals).count() == 100,
			"overlay did not place the exact iron quota");
		require(assignments.stream().filter(Blocks.QUARTZ_BLOCK::equals).count() == 100,
			"overlay did not place the exact quartz quota");
		require(assignments.stream().filter(Blocks.BRICKS::equals).count() == 800,
			"overlay did not retain the existing remainder");
		List<net.minecraft.world.level.block.Block> airAssignments = ToneService.overlayAssignments(
			java.util.Collections.nCopies(10, Blocks.AIR), Map.of(Blocks.BRICKS, 4), 42L
		);
		require(airAssignments.stream().filter(Blocks.BRICKS::equals).count() == 4,
			"air-inclusive overlay did not place its requested blocks");
		require(airAssignments.stream().filter(Blocks.AIR::equals).count() == 6,
			"air-inclusive overlay did not preserve its unchanged remainder");
		expectFailure(() -> OverlayPalette.parse("iron_block[60],quartz_block[50]"), "maximum is 100");
		expectFailure(() -> OverlayPalette.parse("bricks[100],granite[]"), "need some percentage remaining");
	}

	private static void testNetMaterialLedger() {
		MaterialLedger.Delta delta = MaterialLedger.net(
			Map.of(Blocks.BRICKS.asItem(), 100, Blocks.IRON_BLOCK.asItem(), 100),
			Map.of(Blocks.BRICKS.asItem(), 200, Blocks.STONE.asItem(), 50)
		);
		require(delta.required().equals(Map.of(Blocks.IRON_BLOCK.asItem(), 100)),
			"returned blocks did not fund matching placements");
		require(delta.returned().equals(Map.of(Blocks.BRICKS.asItem(), 100, Blocks.STONE.asItem(), 50)),
			"removed or unused blocks were not returned as net surplus");
		expectFailure(() -> MaterialLedger.net(Map.of(Blocks.STONE.asItem(), -1), Map.of()), "cannot be negative");
	}

	private static void testCalculatedAssignmentsAreToned() {
		List<net.minecraft.world.level.block.Block> existing = java.util.Collections.nCopies(1_000, Blocks.BRICKS);
		List<net.minecraft.world.level.block.Block> assignments = ToneService.assignments(existing, Map.of(
			Blocks.BRICKS, 800,
			Blocks.IRON_BLOCK, 100,
			Blocks.QUARTZ_BLOCK, 100
		), 42L);
		require(assignments.stream().filter(Blocks.BRICKS::equals).count() == 800, "brick assignment count changed");
		require(assignments.stream().filter(Blocks.IRON_BLOCK::equals).count() == 100, "iron assignment count changed");
		require(assignments.stream().filter(Blocks.QUARTZ_BLOCK::equals).count() == 100, "quartz assignment count changed");
		long accentsInFirstHalf = assignments.subList(0, 500).stream().filter(block -> block != Blocks.BRICKS).count();
		long accentsInSecondHalf = assignments.subList(500, 1_000).stream().filter(block -> block != Blocks.BRICKS).count();
		require(accentsInFirstHalf > 0 && accentsInSecondHalf > 0,
			"calculated accents clustered at one end instead of toning the selection");
	}

	private static void expectFailure(final Runnable action, final String messagePart) {
		try {
			action.run();
			throw new AssertionError("Expected failure containing: " + messagePart);
		} catch (IllegalArgumentException exception) {
			require(exception.getMessage().contains(messagePart), "unexpected error: " + exception.getMessage());
		}
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
