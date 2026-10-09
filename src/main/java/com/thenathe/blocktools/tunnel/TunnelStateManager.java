package com.thenathe.blocktools.tunnel;

import com.thenathe.blocktools.BlockToolsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class TunnelStateManager {
	private final Map<UUID, TunnelState> states = new HashMap<>();

	public TunnelState state(final UUID playerId) {
		return states.computeIfAbsent(playerId, ignored -> new TunnelState(
			BlockToolsMod.TUNNEL_CONFIG.config().defaultWidth,
			BlockToolsMod.TUNNEL_CONFIG.config().defaultHeight
		));
	}

	public void remove(final UUID playerId) {
		states.remove(playerId);
	}

	public void resetTrails() {
		states.values().forEach(TunnelState::resetTrail);
	}

	public void clear() {
		states.clear();
	}

	public static final class TunnelState {
		public TunnelPalette walls;
		public TunnelPalette floor;
		public TunnelPalette ceiling;
		public TunnelSpec.RailMode railMode = TunnelSpec.RailMode.OFF;
		public boolean enabled;
		public int width;
		public int height;
		public BlockPos runOrigin;
		public String runDimension;
		public Direction runDirection;
		public BlockPos lastCenter;
		public String lastDimension;
		public Direction lastDirection;
		public final Set<BlockPos> processedCenters = new LinkedHashSet<>();
		public long lastErrorGameTime = Long.MIN_VALUE;
		public String lastResetNotice;

		TunnelState(final int width, final int height) {
			this.width = width;
			this.height = height;
		}

		public void configure(final TunnelSpec spec) {
			width = spec.width();
			height = spec.height();
			walls = spec.walls();
			floor = spec.floor();
			ceiling = spec.ceiling();
			railMode = spec.railMode();
			enabled = true;
			resetTrail();
		}

		public boolean configured() {
			return railMode == TunnelSpec.RailMode.ONLY || walls != null && floor != null && ceiling != null;
		}

		public void disarm() {
			walls = null;
			floor = null;
			ceiling = null;
			railMode = TunnelSpec.RailMode.OFF;
			enabled = false;
			resetTrail();
		}

		public void resetTrail() {
			lastResetNotice = null;
			runOrigin = null;
			runDimension = null;
			runDirection = null;
			lastCenter = null;
			lastDimension = null;
			lastDirection = null;
			processedCenters.clear();
		}
	}
}
