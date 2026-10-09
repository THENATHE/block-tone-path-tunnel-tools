package com.thenathe.blocktools.tunnel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.Set;

public final class TunnelGeometryTest {
	private TunnelGeometryTest() {
	}

	public static void main(final String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		centersOddWidthsOnPlayer();
		biasesEvenWidthsRightOfPlayer();
		placesEvenRailsOnRightCenterLane();
		keepsRunOrientationWhenBacktracking();
		usesCoordinateStableBoosters();
		preservesSpecialRails();
		rejectsUnsafeLiningShapes();
		enforcesDimensionLimits();
		parsesRailOnlyWithoutLining();
		coversAllShellDimensionsAndDirections();
		keepsRailsAndTorchesInsideEveryWidth();
		refusesOccupiedBoosterLanes();
		boundsContinuousTrailMemory();
		interpolatesStraightRunsWithoutRepeatingOrigin();
		rejectsMalformedPalettesAndClauses();
		selectsWeightedBlocksAcrossNegativeHashes();
		System.out.println("Tunnel geometry and safety regression checks passed.");
	}

	private static void centersOddWidthsOnPlayer() {
		require(TunnelService.leftExtent(3) == 1 && TunnelService.rightExtent(3) == 1,
			"width 3 must extend equally around the player");
		require(TunnelService.leftExtent(5) == 2 && TunnelService.rightExtent(5) == 2,
			"width 5 must extend equally around the player");
		require(TunnelService.railLane(3) == 0 && TunnelService.railLane(5) == 0,
			"odd-width rail must use the player's middle lane");
	}

	private static void biasesEvenWidthsRightOfPlayer() {
		require(TunnelService.leftExtent(2) == 0 && TunnelService.rightExtent(2) == 1,
			"width 2 must put the player on the left and extra lane on the right");
		require(TunnelService.leftExtent(4) == 1 && TunnelService.rightExtent(4) == 2,
			"width 4 must put the player in the left-center lane");
	}

	private static void placesEvenRailsOnRightCenterLane() {
		require(TunnelService.railLane(2) == 1 && TunnelService.railLane(4) == 1,
			"even-width rail must use the right-center lane");
		require(TunnelService.torchLane(2) == 0,
			"width-2 booster torch must use the player's same-level left lane so it powers the right-lane rail");
		require(TunnelService.torchLane(3) == 1,
			"odd-width booster torch must use a same-level lane adjacent to the centered rail");
		require(TunnelService.alignmentMessage(2).contains("LEFT")
			&& TunnelService.alignmentMessage(2).contains("RIGHT"),
			"even-width guidance must explicitly tell the player left and rail right");
	}

	private static void keepsRunOrientationWhenBacktracking() {
		require(TunnelService.sameRunAxis(Direction.NORTH, Direction.SOUTH),
			"backtracking on the same axis must keep the original left/right basis");
		require(!TunnelService.sameRunAxis(Direction.NORTH, Direction.EAST),
			"turning onto another axis must start a new straight run");
	}

	private static void usesCoordinateStableBoosters() {
		for (int coordinate : new int[] {-16, -8, 0, 8, 16}) {
			require(TunnelService.isBoosterCoordinate(new BlockPos(9, 64, coordinate), Direction.NORTH, 8),
				"north/south boosters must remain stable at coordinate " + coordinate);
			require(TunnelService.isBoosterCoordinate(new BlockPos(coordinate, 64, 9), Direction.WEST, 8),
				"east/west boosters must remain stable at coordinate " + coordinate);
		}
		require(!TunnelService.isBoosterCoordinate(new BlockPos(0, 64, -7), Direction.SOUTH, 8),
			"negative non-multiples must not become boosters");
	}

	private static void preservesSpecialRails() {
		require(TunnelService.isSpecialRail(Blocks.DETECTOR_RAIL), "detector rails must be preserved");
		require(TunnelService.isSpecialRail(Blocks.ACTIVATOR_RAIL), "activator rails must be preserved");
		require(!TunnelService.isSpecialRail(Blocks.RAIL), "ordinary rail remains managed by the tool");
		require(!TunnelService.isSpecialRail(Blocks.POWERED_RAIL), "powered rail remains managed but must not be downgraded");
	}

	private static void rejectsUnsafeLiningShapes() {
		require(TunnelPalette.isSafeLiningBlock(Blocks.STONE), "ordinary full cubes must be accepted");
		require(!TunnelPalette.isSafeLiningBlock(Blocks.STONE_SLAB), "partial-height slabs must be rejected");
		require(!TunnelPalette.isSafeLiningBlock(Blocks.SAND), "falling blocks must be rejected");
		require(!TunnelPalette.isSafeLiningBlock(Blocks.TNT), "TNT must be rejected near booster torches");
		require(!TunnelPalette.isSafeLiningBlock(Blocks.PISTON), "pistons must be rejected near booster torches");
	}

	private static void enforcesDimensionLimits() {
		rejects("width:6 height:3 rail:only");
		rejects("width:2 height:6 rail:only");
		rejects("width:0 height:3 rail:only");
		rejects("width:2 height:1 rail:only");
		rejects("width:1 height:3 rail:only");
		rejects("width:1 height:3 to:stone[100] rail:on");
		TunnelSpec oneWideLining = TunnelSpec.parse("width:1 height:3 to:stone[100] rail:off", 3, 3);
		require(oneWideLining.width() == 1 && oneWideLining.railMode() == TunnelSpec.RailMode.OFF,
			"width-1 lining must remain supported when rails are off");
	}

	private static void parsesRailOnlyWithoutLining() {
		TunnelSpec spec = TunnelSpec.parse("rail:only height:3 width:2", 3, 3);
		require(spec.width() == 2 && spec.height() == 3, "rail-only dimensions must parse position-independently");
		require(spec.railMode() == TunnelSpec.RailMode.ONLY && !spec.linesTunnel(),
			"rail:only must not configure shell replacement");
	}


	private static void coversAllShellDimensionsAndDirections() {
		BlockPos center = new BlockPos(-25, 70, 31);
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			for (int width = 1; width <= 5; width++) {
				for (int height = 2; height <= 5; height++) {
					var shell = TunnelService.shell(center, direction, width, height);
					require(shell.size() == 2 * width + 2 * height, "shell size must match the complete perimeter");
					Set<BlockPos> interior = new HashSet<>();
					for (int lane = -TunnelService.leftExtent(width); lane <= TunnelService.rightExtent(width); lane++) {
						BlockPos column = TunnelService.lateral(center, direction, lane);
						require(shell.get(column.below()) == TunnelService.Surface.FLOOR, "every lane needs floor");
						require(shell.get(column.above(height)) == TunnelService.Surface.CEILING, "every lane needs ceiling");
						for (int y = 0; y < height; y++) interior.add(column.above(y));
					}
					require(interior.size() == width * height, "interior must have the configured clear volume");
					for (BlockPos pos : shell.keySet()) require(!interior.contains(pos), "shell must never fill the player interior");
				}
			}
		}
	}

	private static void keepsRailsAndTorchesInsideEveryWidth() {
		for (int width = 2; width <= 5; width++) {
			int rail = TunnelService.railLane(width);
			int torch = TunnelService.torchLane(width);
			require(Math.abs(rail - torch) == 1, "booster must be adjacent to its rail");
			require(torch >= -TunnelService.leftExtent(width) && torch <= TunnelService.rightExtent(width),
				"torch must occupy interior, never shell");
		}
	}

	private static void refusesOccupiedBoosterLanes() {
		require(TunnelService.canUseTorchPosition(Blocks.AIR.defaultBlockState()), "empty booster lane is allowed");
		require(TunnelService.canUseTorchPosition(Blocks.REDSTONE_TORCH.defaultBlockState()), "existing booster is retained");
		require(!TunnelService.canUseTorchPosition(Blocks.REDSTONE_WIRE.defaultBlockState()), "existing redstone must not be overwritten");
		require(!TunnelService.canUseTorchPosition(Blocks.WHEAT.defaultBlockState()), "crops must not be overwritten");
		require(!TunnelService.canUseTorchPosition(Blocks.TORCH.defaultBlockState()), "ordinary lighting must not be silently replaced");
	}

	private static void boundsContinuousTrailMemory() {
		var state = new TunnelStateManager.TunnelState(3, 3);
		for (int x = 0; x < TunnelService.MAX_REMEMBERED_CENTERS + 100; x++) {
			TunnelService.remember(state, new BlockPos(x, 64, 0), "minecraft:overworld", Direction.EAST);
		}
		require(state.processedCenters.size() == TunnelService.MAX_REMEMBERED_CENTERS, "long tunnels must use bounded memory");
		require(!state.processedCenters.contains(new BlockPos(0, 64, 0)), "oldest center is evicted");
		require(state.processedCenters.contains(state.lastCenter), "latest center remains available for backtracking");
		state.resetTrail();
		require(state.lastCenter == null && state.lastDirection == null && state.processedCenters.isEmpty(), "reset removes traversal state");
	}

	private static void interpolatesStraightRunsWithoutRepeatingOrigin() {
		BlockPos origin = new BlockPos(-2, 70, -4);
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			var line = TunnelService.line(origin, origin.relative(direction, 8));
			require(line.size() == 8 && !line.contains(origin), "catch-up excludes already processed origin");
			for (int i = 0; i < line.size(); i++) require(line.get(i).equals(origin.relative(direction, i + 1)), "catch-up must have no gaps");
		}
		require(TunnelService.line(origin, origin).isEmpty(), "standing still requires no catch-up");
	}

	private static void rejectsMalformedPalettesAndClauses() {
		for (String spec : new String[] {
			"to:stone[99]", "to:stone[101]", "to:stone[0]", "to:stone[50],stone[50]",
			"to:stone[100],", "to:missing:block[100]", "to:water[100]", "to:chest[100]",
			"to:stone[100] all:andesite[100]", "to:stone[100] walls:stone[100]",
			"walls:stone[100] floor:stone[100]", "rail:only to:stone[100]",
			"width:2 width:3 rail:only", "height:3 height:4 rail:only", "rail:only rails:off",
			"rail:invalid", "rail:only unknown:value", "width:no rail:only"
		}) rejects(spec);
	}

	private static void selectsWeightedBlocksAcrossNegativeHashes() {
		TunnelPalette palette = TunnelPalette.parse("stone[70],andesite[30]");
		int stone = 0;
		int andesite = 0;
		for (long hash = -100; hash < 100; hash++) {
			Block selected = palette.pick(hash);
			if (selected == Blocks.STONE) stone++;
			else if (selected == Blocks.ANDESITE) andesite++;
			else throw new AssertionError("palette returned an unconfigured block");
		}
		require(stone == 140 && andesite == 60, "positive and negative hash residues must preserve configured weights");
		require(palette.pick(Long.MIN_VALUE) != null && palette.pick(Long.MAX_VALUE) != null, "extreme hashes must be valid");
	}

	private static void rejects(final String specification) {
		try {
			TunnelSpec.parse(specification, 3, 3);
			throw new AssertionError("Expected rejection: " + specification);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
