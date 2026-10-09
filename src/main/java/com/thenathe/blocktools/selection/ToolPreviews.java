package com.thenathe.blocktools.selection;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.path.PathService;
import com.thenathe.blocktools.tunnel.TunnelService;
import com.thenathe.blocktools.tunnel.TunnelSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Geometric footprints only: previewing never plans edits, checks inventory, or calls protection hooks. */
public final class ToolPreviews {
	static final int MAX_POINTS = 256;
	private static final int PREVIEW_LENGTH = 3;
	private static final int OUTLINE_POINTS = 128;

	private ToolPreviews() {
	}

	public static String path(final ServerPlayer player) {
		var state = BlockToolsMod.STATES.path(player.getUUID());
		Direction direction = previewDirection(player.getDirection(), state.lastCenter,
			state.lastDirection, state.lastDimension, player.level().dimension().identifier().toString());
		BlockPos center = new BlockPos(player.getBlockX(), player.getOnPos().getY(), player.getBlockZ());
		List<Marker> markers = new ArrayList<>();
		int found = 0;
		for (BlockPos column : pathColumns(center, direction, state.width)) {
			BlockPos surface = PathService.findSurface(player.level(), column.getX(), column.getZ(), center.getY());
			if (surface == null) continue;
			found++;
			surfaceSquare(markers, surface, Color.PATH);
			// Follow each terrain height so an uphill preview never disappears below the ground.
			if (isCenterLane(center, column, direction)) arrow(markers, surface, direction, Color.BOUNDARY, 1.12);
		}
		emit(player, markers);
		return "Path preview: width " + state.width + " | " + facing(direction) + " | "
			+ (state.enabled ? "ON" : "PAUSED") + " | terrain footprint"
			+ (found == 0 ? " (no loaded surface)" : "");
	}

	public static String tunnel(final ServerPlayer player) {
		var state = BlockToolsMod.TUNNELS.state(player.getUUID());
		String dimension = player.level().dimension().identifier().toString();
		if (state.runOrigin != null && !dimension.equals(state.runDimension)) {
			return "Tunnel line is in another dimension. Put tool away, then re-equip.";
		}
		Direction direction = previewDirection(player.getDirection(), state.runOrigin,
			state.runDirection, state.runDimension, dimension);
		BlockPos center = tunnelPreviewCenter(player.blockPosition(), state.runOrigin, direction);
		emit(player, tunnelMarkers(center, direction, state.width, state.height,
			state.railMode, BlockToolsMod.TUNNEL_CONFIG.config().poweredRailInterval));
		if (!center.equals(player.blockPosition())) return "Tunnel Tool: Off line. Put tool away, then re-equip.";
		if (state.runOrigin != null && player.getDirection() != state.runDirection) {
			return "Tunnel Tool: Put tool away, turn, then re-equip.";
		}
		return "Tunnel preview: " + state.width + " x " + state.height + " | " + facing(direction)
			+ " | rail:" + state.railMode.name().toLowerCase(Locale.ROOT)
			+ " | " + (state.enabled ? "ON" : "PAUSED")
			+ " | " + (state.width % 2 == 0 ? "stand left-center" : "stand center") + " | footprint";
	}

	/** Keep the preview on the held run's lane and height while following progress along it. */
	static BlockPos tunnelPreviewCenter(final BlockPos current, final BlockPos origin, final Direction direction) {
		if (origin == null) return current;
		return direction.getAxis() == Direction.Axis.X
			? new BlockPos(current.getX(), origin.getY(), origin.getZ())
			: new BlockPos(origin.getX(), origin.getY(), current.getZ());
	}

	/** A trail from another dimension must never rotate the current preview. */
	static Direction previewDirection(final Direction facing, final BlockPos lastCenter,
		final Direction lastDirection, final String lastDimension, final String dimension) {
		if (lastCenter != null && lastDirection != null && lastDirection.getAxis().isHorizontal()
			&& dimension.equals(lastDimension)) return lastDirection;
		return facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
	}

	static List<BlockPos> pathColumns(final BlockPos center, final Direction direction, final int width) {
		List<BlockPos> columns = new ArrayList<>();
		int left = (width - 1) / 2;
		int right = width - 1 - left;
		for (int ahead = 0; ahead < PREVIEW_LENGTH; ahead++) {
			for (int lane = -left; lane <= right; lane++) {
				columns.add(center.relative(direction, ahead).relative(direction.getClockWise(), lane));
			}
		}
		return List.copyOf(columns);
	}

	static List<Marker> tunnelMarkers(final BlockPos center, final Direction direction, final int width,
		final int height, final TunnelSpec.RailMode railMode, final int boosterInterval) {
		List<Marker> markers = new ArrayList<>();
		BlockPos first = TunnelService.lateral(center, direction, -TunnelService.leftExtent(width));
		BlockPos last = TunnelService.lateral(center.relative(direction, PREVIEW_LENGTH - 1), direction,
			TunnelService.rightExtent(width)).above(height - 1);
		var boundary = SelectionVisualization.outline(first, last);
		int boundaryCount = Math.min(OUTLINE_POINTS, boundary.size());
		for (int i = 0; i < boundaryCount; i++) {
			var point = boundary.get(i * boundary.size() / boundaryCount);
			markers.add(new Marker(point.x(), point.y(), point.z(), Color.BOUNDARY));
		}
		for (int ahead = 0; ahead < PREVIEW_LENGTH; ahead++) {
			BlockPos slice = center.relative(direction, ahead);
			if (railMode != TunnelSpec.RailMode.ONLY) {
				for (var entry : TunnelService.shell(slice, direction, width, height).entrySet()) {
					BlockPos pos = entry.getKey();
					switch (entry.getValue()) {
						case FLOOR -> markers.add(new Marker(pos.getX() + .5, pos.getY() + 1.04,
							pos.getZ() + .5, Color.FLOOR));
						case CEILING -> markers.add(new Marker(pos.getX() + .5, pos.getY() - .04,
							pos.getZ() + .5, Color.CEILING));
						case WALL -> {
							// Put wall markers just inside the clear space, visible against opaque lining.
							Direction inward = direction.getAxis() == Direction.Axis.Z
								? (pos.getX() < slice.getX() ? Direction.EAST : Direction.WEST)
								: (pos.getZ() < slice.getZ() ? Direction.SOUTH : Direction.NORTH);
							markers.add(new Marker(pos.getX() + .5 + inward.getStepX() * .54,
								pos.getY() + .5, pos.getZ() + .5 + inward.getStepZ() * .54, Color.WALL));
						}
					}
				}
			}
			if (railMode != TunnelSpec.RailMode.OFF && width >= 2) {
				BlockPos rail = TunnelService.lateral(slice, direction, TunnelService.railLane(width));
				boolean booster = TunnelService.isBoosterCoordinate(slice, direction, boosterInterval);
				for (double distance : new double[] {-.35, 0, .35}) {
					markers.add(new Marker(rail.getX() + .5 + direction.getStepX() * distance,
						rail.getY() + .16, rail.getZ() + .5 + direction.getStepZ() * distance,
						booster ? Color.BOOSTER : Color.RAIL));
				}
				if (booster) {
					BlockPos torch = TunnelService.lateral(slice, direction, TunnelService.torchLane(width));
					markers.add(new Marker(torch.getX() + .5, torch.getY() + .65, torch.getZ() + .5, Color.BOOSTER));
				}
			}
			arrow(markers, slice, direction, Color.PATH, .3);
		}
		return List.copyOf(markers.subList(0, Math.min(markers.size(), MAX_POINTS)));
	}

	private static boolean isCenterLane(final BlockPos center, final BlockPos column, final Direction direction) {
		return direction.getAxis() == Direction.Axis.Z ? center.getX() == column.getX() : center.getZ() == column.getZ();
	}

	private static void surfaceSquare(final List<Marker> markers, final BlockPos surface, final Color color) {
		for (int edge = 0; edge < 4; edge++) {
			for (int step = 0; step < 2; step++) {
				double along = step * .5;
				double x = edge == 0 ? along : edge == 1 ? 1 : edge == 2 ? 1 - along : 0;
				double z = edge == 0 ? 0 : edge == 1 ? along : edge == 2 ? 1 : 1 - along;
				markers.add(new Marker(surface.getX() + x, surface.getY() + 1.04, surface.getZ() + z, color));
			}
		}
	}

	private static void arrow(final List<Marker> markers, final BlockPos center, final Direction direction,
		final Color color, final double elevation) {
		Direction side = direction.getClockWise();
		for (double[] position : new double[][] {{-.25, 0}, {0, 0}, {.3, 0}, {.05, -.2}, {.05, .2}}) {
			markers.add(new Marker(center.getX() + .5 + direction.getStepX() * position[0] + side.getStepX() * position[1],
				center.getY() + elevation,
				center.getZ() + .5 + direction.getStepZ() * position[0] + side.getStepZ() * position[1], color));
		}
	}

	private static void emit(final ServerPlayer player, final List<Marker> markers) {
		for (int i = 0; i < Math.min(markers.size(), MAX_POINTS); i++) {
			Marker marker = markers.get(i);
			SelectionVisualization.point(player, marker.color().particle, marker.x(), marker.y(), marker.z());
		}
	}

	private static String facing(final Direction direction) {
		return "facing " + direction.name().toLowerCase(Locale.ROOT);
	}

	record Marker(double x, double y, double z, Color color) {
	}

	enum Color {
		BOUNDARY(ParticleTypes.END_ROD),
		PATH(new DustParticleOptions(0x41DCF5, 1.0F)),
		WALL(new DustParticleOptions(0xAC78FF, 1.0F)),
		FLOOR(new DustParticleOptions(0x63E58B, 1.0F)),
		CEILING(new DustParticleOptions(0x699CFF, 1.0F)),
		RAIL(new DustParticleOptions(0xFFC945, 1.0F)),
		BOOSTER(new DustParticleOptions(0xFF554E, 1.0F));

		private final ParticleOptions particle;

		Color(final ParticleOptions particle) {
			this.particle = particle;
		}
	}
}
