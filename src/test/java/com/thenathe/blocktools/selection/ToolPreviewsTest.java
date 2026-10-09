package com.thenathe.blocktools.selection;

import com.thenathe.blocktools.tunnel.TunnelService;
import com.thenathe.blocktools.tunnel.TunnelSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.List;

public final class ToolPreviewsTest {
	private ToolPreviewsTest() {
	}

	public static void run() {
		BlockPos origin = new BlockPos(-17, 64, -9);
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			for (int width = 1; width <= 5; width++) {
				pathFootprint(origin, direction, width);
				for (int height = 2; height <= 5; height++) {
					for (TunnelSpec.RailMode mode : TunnelSpec.RailMode.values()) {
						if (width == 1 && mode != TunnelSpec.RailMode.OFF) continue;
						tunnelFootprint(origin, direction, width, height, mode);
					}
				}
			}
			boosterFootprint(direction);
		}
		keepsOnlyCurrentDimensionRunDirection(origin);
		keepsTunnelPreviewOnOriginalLine(origin);
		System.out.println("Tool preview geometry, alignment, rail, and bounded particle checks passed.");
	}

	private static void pathFootprint(final BlockPos origin, final Direction direction, final int width) {
		List<BlockPos> columns = ToolPreviews.pathColumns(origin, direction, width);
		require(columns.size() == width * 3 && new HashSet<>(columns).size() == columns.size(),
			"path must preview exactly three distinct width stripes");
		for (int ahead = 0; ahead < 3; ahead++) {
			BlockPos center = origin.relative(direction, ahead);
			for (int lane = -(width - 1) / 2; lane <= width / 2; lane++) {
				require(columns.contains(center.relative(direction.getClockWise(), lane)),
					"preview must use path placement's clockwise bias for even widths");
			}
		}
		require(columns.stream().noneMatch(pos -> pos.equals(origin.relative(direction.getOpposite()))),
			"path preview must point ahead of the player");
	}

	private static void tunnelFootprint(final BlockPos origin, final Direction direction, final int width,
		final int height, final TunnelSpec.RailMode mode) {
		List<ToolPreviews.Marker> markers = ToolPreviews.tunnelMarkers(origin, direction, width, height, mode, 8);
		require(!markers.isEmpty() && markers.size() <= ToolPreviews.MAX_POINTS,
			"every tunnel footprint must stay within its particle packet budget");
		BlockPos a = TunnelService.lateral(origin, direction, -TunnelService.leftExtent(width));
		BlockPos b = TunnelService.lateral(origin.relative(direction, 2), direction,
			TunnelService.rightExtent(width)).above(height - 1);
		double minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX()) + 1;
		double minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ()) + 1;
		for (ToolPreviews.Marker marker : markers) {
			require(Double.isFinite(marker.x()) && Double.isFinite(marker.y()) && Double.isFinite(marker.z()),
				"particle coordinates must be finite");
			require(marker.x() >= minX && marker.x() <= maxX && marker.z() >= minZ && marker.z() <= maxZ
				&& marker.y() >= origin.getY() && marker.y() <= origin.getY() + height,
				"markers must lie at or inside the true clear interior, visible against opaque shell blocks");
		}
		require(count(markers, ToolPreviews.Color.BOUNDARY) > 0, "clear interior always needs a white outline");
		require(count(markers, ToolPreviews.Color.PATH) == 15, "three arrows must mark the player lane");
		if (mode == TunnelSpec.RailMode.ONLY) {
			require(count(markers, ToolPreviews.Color.FLOOR) == 0 && count(markers, ToolPreviews.Color.CEILING) == 0
				&& count(markers, ToolPreviews.Color.WALL) == 0, "rail-only must not imply shell replacement");
		} else {
			require(count(markers, ToolPreviews.Color.FLOOR) == 3 * width, "every lining floor block needs a marker");
			require(count(markers, ToolPreviews.Color.CEILING) == 3 * width, "every lining ceiling block needs a marker");
			require(count(markers, ToolPreviews.Color.WALL) == 6 * height, "both full-height walls need markers");
		}
		if (mode == TunnelSpec.RailMode.OFF) {
			require(count(markers, ToolPreviews.Color.RAIL) == 0 && count(markers, ToolPreviews.Color.BOOSTER) == 0,
				"rail:off must show no rail or booster");
		} else {
			for (int ahead = 0; ahead < 3; ahead++) {
				BlockPos expected = origin.relative(direction, ahead).relative(direction.getClockWise(), width % 2 == 0 ? 1 : 0);
				require(markers.stream().anyMatch(marker -> (marker.color() == ToolPreviews.Color.RAIL
					|| marker.color() == ToolPreviews.Color.BOOSTER) && marker.x() == expected.getX() + .5
					&& marker.z() == expected.getZ() + .5 && marker.y() == expected.getY() + .16),
					"each rail marker must use the actual centered/right-center rail lane");
			}
		}
	}

	private static void boosterFootprint(final Direction direction) {
		for (int interval : new int[] {8, 16, 128}) {
			BlockPos origin = direction.getAxis() == Direction.Axis.X
				? new BlockPos(-interval, 72, 13) : new BlockPos(13, 72, -interval);
			var markers = ToolPreviews.tunnelMarkers(origin, direction, 2, 3, TunnelSpec.RailMode.ONLY, interval);
			require(count(markers, ToolPreviews.Color.BOOSTER) == 4,
				"negative coordinate booster needs three red rail dots plus one adjacent red torch dot");
			BlockPos torch = origin.relative(direction.getClockWise(), TunnelService.torchLane(2));
			require(markers.stream().anyMatch(marker -> marker.color() == ToolPreviews.Color.BOOSTER
				&& marker.x() == torch.getX() + .5 && marker.z() == torch.getZ() + .5
				&& marker.y() == torch.getY() + .65), "booster preview must use the actual torch lane");
		}
	}

	private static void keepsOnlyCurrentDimensionRunDirection(final BlockPos origin) {
		require(ToolPreviews.previewDirection(Direction.SOUTH, origin, Direction.NORTH, "overworld", "overworld") == Direction.NORTH,
			"a current run must keep its original lateral basis while looking or walking backward");
		require(ToolPreviews.previewDirection(Direction.SOUTH, origin, Direction.NORTH, "nether", "overworld") == Direction.SOUTH,
			"a stale dimension's run must not control preview orientation");
		require(ToolPreviews.previewDirection(Direction.EAST, null, Direction.NORTH, "overworld", "overworld") == Direction.EAST,
			"a missing trail center must use player facing");
		require(ToolPreviews.previewDirection(Direction.UP, null, null, null, "overworld") == Direction.NORTH,
			"nonhorizontal input needs a stable horizontal fallback");
	}

	private static void keepsTunnelPreviewOnOriginalLine(final BlockPos origin) {
		BlockPos current = origin.offset(4, 7, -9);
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
			require(ToolPreviews.tunnelPreviewCenter(current, origin, direction)
				.equals(new BlockPos(origin.getX(), origin.getY(), current.getZ())),
				"north/south preview stays on original X and Y after sideways/elevation changes");
		}
		for (Direction direction : new Direction[] {Direction.EAST, Direction.WEST}) {
			require(ToolPreviews.tunnelPreviewCenter(current, origin, direction)
				.equals(new BlockPos(current.getX(), origin.getY(), origin.getZ())),
				"east/west preview stays on original Z and Y after sideways/elevation changes");
		}
		require(ToolPreviews.tunnelPreviewCenter(current, null, Direction.NORTH).equals(current),
			"released tool preview follows current position");
	}

	private static long count(final List<ToolPreviews.Marker> markers, final ToolPreviews.Color color) {
		return markers.stream().filter(marker -> marker.color() == color).count();
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
