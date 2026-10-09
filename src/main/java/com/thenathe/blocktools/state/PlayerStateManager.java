package com.thenathe.blocktools.state;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.Palette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerStateManager {
	private final Map<UUID, SelectionState> selections = new HashMap<>();
	private final Map<UUID, PathState> paths = new HashMap<>();

	public SelectionState selection(final UUID player) {
		return selections.computeIfAbsent(player, ignored -> new SelectionState());
	}

	public PathState path(final UUID player) {
		return paths.computeIfAbsent(player, ignored -> new PathState(BlockToolsMod.CONFIG.config().defaultPathWidth));
	}

	public void clear() {
		selections.clear();
		paths.clear();
	}

	public void resetTrails() {
		paths.values().forEach(PathState::resetTrail);
	}

	public void remove(final UUID player) {
		selections.remove(player);
		paths.remove(player);
	}

	public static final class SelectionState {
		private BlockPos pointOne;
		private BlockPos pointTwo;
		private String dimension;

		public boolean set(final ServerLevel level, final int index, final BlockPos point) {
			return set(level.dimension().identifier().toString(), index, point);
		}

		boolean set(final String currentDimension, final int index, final BlockPos point) {
			if (index != 1 && index != 2) throw new IllegalArgumentException("Selection point index must be 1 or 2.");
			if (!currentDimension.equals(dimension)) {
				pointOne = null;
				pointTwo = null;
				dimension = currentDimension;
			}
			BlockPos existing = index == 1 ? pointOne : pointTwo;
			if (point.equals(existing)) return false;
			if (index == 1) pointOne = point.immutable();
			if (index == 2) pointTwo = point.immutable();
			return true;
		}

		public void clear() {
			pointOne = null;
			pointTwo = null;
			dimension = null;
		}

		public List<BlockPos> points() {
			if (pointOne == null && pointTwo == null) return List.of();
			if (pointOne == null) return List.of(pointTwo);
			if (pointTwo == null) return List.of(pointOne);
			return List.of(pointOne, pointTwo);
		}

		public BlockPos pointOne() {
			return pointOne;
		}

		public BlockPos pointTwo() {
			return pointTwo;
		}

		public boolean complete() {
			return pointOne != null && pointTwo != null;
		}

		public boolean belongsTo(final ServerLevel level) {
			return dimension != null && dimension.equals(level.dimension().identifier().toString());
		}
	}

	public static final class PathState {
		public Palette palette;
		public Palette stairPalette;
		public BlockFilter sourceFilter = BlockFilter.all();
		public boolean stairsEnabled;
		public boolean enabled;
		public int width;
		public BlockPos lastCenter;
		public String lastDimension;
		public Direction lastDirection;
		public final Map<Integer, BlockPos> lastSurfaces = new HashMap<>();
		public long lastErrorGameTime = Long.MIN_VALUE;

		private PathState(final int width) {
			this.width = width;
		}

		public void resetTrail() {
			lastCenter = null;
			lastDimension = null;
			lastDirection = null;
			lastSurfaces.clear();
		}
	}
}
