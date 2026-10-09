package com.thenathe.blocktools.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class PathOrientationTest {
	private PathOrientationTest() {
	}

	public static void run() {
		for (Direction travel : Direction.Plane.HORIZONTAL) {
			require(PathService.stairFacing(travel, 1) == travel, "uphill stair does not face uphill for " + travel);
			require(PathService.stairFacing(travel, -1) == travel.getOpposite(), "downhill stair high side is not uphill for " + travel);
		}

		BlockPos lower = new BlockPos(0, 63, 0);
		BlockPos upper = new BlockPos(1, 64, 0);
		require(PathService.stairSurface(lower, upper).equals(upper), "uphill stair was not put in the upper terrain block");
		require(PathService.stairSurface(upper, lower).equals(upper), "downhill stair was not put in the previous upper terrain block");
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
