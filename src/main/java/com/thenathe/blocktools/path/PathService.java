package com.thenathe.blocktools.path;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.BuildPermissions;
import com.thenathe.blocktools.material.BlockMaterials;
import com.thenathe.blocktools.world.WorldEdits;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.inventory.MaterialLedger;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.state.PlayerStateManager.PathState;
import com.thenathe.blocktools.tone.ToneService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PathService {
	private static final Set<Block> FLATTENABLE = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.COARSE_DIRT,
		Blocks.MYCELIUM,
		Blocks.ROOTED_DIRT
	);

	private PathService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PathService::tick);
	}

	private static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(player);
	}

	private static void tickPlayer(final ServerPlayer player) {
		PathState state = BlockToolsMod.STATES.path(player.getUUID());
		if (!state.enabled || state.palette == null || player.isSpectator() || !holdsPathItem(player)) {
			state.resetTrail();
			return;
		}

		ServerLevel level = player.level();
		String dimension = level.dimension().identifier().toString();
		BlockPos on = player.getOnPos();
		BlockPos current = new BlockPos(player.getBlockX(), on.getY(), player.getBlockZ());
		Direction fallback = horizontal(player.getDirection());

		if (state.lastCenter == null || !dimension.equals(state.lastDimension)) {
			state.resetTrail();
			if (placeStripe(player, state, current, fallback)) {
				state.lastCenter = current;
				state.lastDimension = dimension;
			}
			return;
		}

		int deltaX = current.getX() - state.lastCenter.getX();
		int deltaZ = current.getZ() - state.lastCenter.getZ();
		int distance = Math.max(Math.abs(deltaX), Math.abs(deltaZ));
		if (distance == 0) return;
		if (distance > BlockToolsMod.CONFIG.config().maxPathCatchUp
			|| Math.abs(current.getY() - state.lastCenter.getY()) > BlockToolsMod.CONFIG.config().pathVerticalSearch) {
			state.resetTrail();
			if (placeStripe(player, state, current, fallback)) {
				state.lastCenter = current;
				state.lastDimension = dimension;
			}
			return;
		}

		BlockPos previous = state.lastCenter;
		for (BlockPos next : line(state.lastCenter, current)) {
			Direction direction = travelDirection(previous, next, fallback);
			if (!placeStripe(player, state, next, direction)) return;
			state.lastCenter = next;
			state.lastDimension = dimension;
			previous = next;
		}
	}

	private static boolean placeStripe(
		final ServerPlayer player,
		final PathState state,
		final BlockPos center,
		final Direction direction
	) {
		ServerLevel level = player.level();
		if (state.lastDirection != null && state.lastDirection != direction) state.lastSurfaces.clear();
		state.lastDirection = direction;

		int left = (state.width - 1) / 2;
		int right = state.width - 1 - left;
		Map<BlockPos, Candidate> candidatePlan = new LinkedHashMap<>();
		Map<Integer, BlockPos> newSurfaces = new HashMap<>();
		for (int offset = -left; offset <= right; offset++) {
			BlockPos column = offset(center, direction, offset);
			BlockPos surface = findSurface(level, column.getX(), column.getZ(), center.getY());
			if (surface == null) continue;
			newSurfaces.put(offset, surface);

			BlockPos previousSurface = state.lastSurfaces.get(offset);
			int stepDelta = previousSurface == null ? 0 : surface.getY() - previousSurface.getY();
			boolean useStair = state.stairsEnabled && state.stairPalette != null && Math.abs(stepDelta) == 1;
			if (useStair) {
				BlockPos stairSurface = stairSurface(previousSurface, surface);
				Candidate stair = planCandidate(
					player, stairSurface, state.stairPalette, state.sourceFilter, state.palette, true,
					stairFacing(direction, stepDelta)
				);
				if (stair != null) candidatePlan.put(stair.surface, stair);
			}
			if (!useStair || stepDelta < 0) {
				Candidate normal = planCandidate(
					player, surface, state.palette, state.sourceFilter, state.palette, false, direction
				);
				if (normal != null) candidatePlan.put(normal.surface, normal);
			}
		}

		List<Candidate> candidates = new ArrayList<>(candidatePlan.values());
		Map<Item, Integer> grossRequired = new LinkedHashMap<>();
		Map<Item, Integer> grossReturned = new LinkedHashMap<>();
		for (Candidate candidate : candidates) {
			if (candidate.cost != null) grossRequired.merge(candidate.cost, 1, Integer::sum);
			if (candidate.refund != null) grossReturned.merge(candidate.refund, candidate.refundCount, Integer::sum);
			if (candidate.vegetationRefund != null) grossReturned.merge(candidate.vegetationRefund, candidate.vegetationRefundCount, Integer::sum);
		}
		MaterialLedger.Delta net = MaterialLedger.net(grossRequired, grossReturned);
		InventoryMaterials.InventorySnapshot inventorySnapshot = InventoryMaterials.snapshot(player);
		if (!player.isCreative()) {
			Map<Item, InventoryMaterials.Shortage> shortages = net.required().isEmpty()
				? Map.of()
				: inventorySnapshot.shortages(net.required());
			if (!shortages.isEmpty()) {
				long now = level.getGameTime();
				if (now - state.lastErrorGameTime >= 40L || state.lastErrorGameTime == Long.MIN_VALUE) {
					player.sendOverlayMessage(BlockToolsMod.pathError(ToneService.shortageMessage(shortages)));
					state.lastErrorGameTime = now;
				}
				return false;
			}
		}

		Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
		for (Candidate candidate : candidates) {
			if (candidate.vegetation != null) edits.put(candidate.vegetation, Blocks.AIR.defaultBlockState());
			if (candidate.vegetationUpper != null) edits.put(candidate.vegetationUpper, Blocks.AIR.defaultBlockState());
			edits.put(candidate.surface, candidate.desired);
		}
		if (!WorldEdits.apply(level, edits, () -> player.isCreative() || net.required().isEmpty()
			|| inventorySnapshot.consume(net.required()).isEmpty())) return false;
		if (!net.returned().isEmpty()) InventoryMaterials.give(player, net.returned());
		state.lastSurfaces.clear();
		state.lastSurfaces.putAll(newSurfaces);
		return true;
	}

	private static Candidate planCandidate(
		final ServerPlayer player,
		final BlockPos surface,
		final Palette palette,
		final BlockFilter sourceFilter,
		final Palette normalPalette,
		final boolean stair,
		final Direction stairFacing
	) {
		ServerLevel level = player.level();
		if (!level.isInWorldBounds(surface) || !level.hasChunkAt(surface) || !BuildPermissions.canChange(player, surface)) return null;
		BlockState existing = level.getBlockState(surface);
		if (level.getBlockEntity(surface) != null || existing.getDestroySpeed(level, surface) < 0
			|| !existing.getFluidState().isEmpty() || BlockMaterials.isMultipart(existing)) return null;
		boolean toolPlacedPath = stair && normalPalette != null && normalPalette.entryFor(existing.getBlock()) != null;
		if (!sourceFilter.matches(existing.getBlock()) && !toolPlacedPath) return null;
		BlockPos above = surface.above();
		if (!level.isInWorldBounds(above) || !level.hasChunkAt(above)) return null;
		BlockState aboveState = level.getBlockState(above);
		if (!aboveState.isAir() && !BuildPermissions.canChange(player, above)) return null;
		if (!aboveState.isAir() && !isIgnorableVegetation(level, above, aboveState)) return null;
		if (!aboveState.getFluidState().isEmpty()) return null;
		BlockPos vegetationUpper = null;
		if (BlockMaterials.isMultipart(aboveState)) {
			if (!aboveState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				|| aboveState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) return null;
			vegetationUpper = above.above();
			if (!BuildPermissions.canChange(player, vegetationUpper)) return null;
			BlockState upper = level.getBlockState(vegetationUpper);
			if (!upper.is(aboveState.getBlock()) || upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER
				|| !isIgnorableVegetation(level, vegetationUpper, upper)) return null;
		}

		Palette.Entry chosen = palette.entryFor(existing.getBlock());
		long hash = materialHash(player, surface, palette.canonical());
		if (chosen == null) chosen = palette.pick(hash);
		if (!stair && chosen != null && existing.getBlock() == chosen.block()) return null;

		boolean freePath = false;
		if (!stair && chosen != null && chosen.block() == Blocks.DIRT_PATH) {
			if (FLATTENABLE.contains(existing.getBlock())) {
				freePath = true;
			} else {
				chosen = palette.pickWithoutDirtPath(hash);
				if (chosen == null) return null;
			}
		}
		if (chosen == null) return null;

		BlockState desired = chosen.block().defaultBlockState();
		if (stair && chosen.block() instanceof StairBlock) {
			desired = desired
				.setValue(StairBlock.FACING, stairFacing)
				.setValue(StairBlock.HALF, Half.BOTTOM)
				.setValue(StairBlock.SHAPE, StairsShape.STRAIGHT)
				.setValue(StairBlock.WATERLOGGED, false);
		}
		if (existing.equals(desired) || (!stair && existing.getBlock() == chosen.block())) return null;
		Item cost = freePath || existing.getBlock() == chosen.block() ? null : chosen.block().asItem();
		Item refund = cost != null && existing.getBlock().asItem() != Items.AIR ? existing.getBlock().asItem() : null;
		Item vegetationRefund = !aboveState.isAir() && aboveState.getBlock().asItem() != Items.AIR
			? aboveState.getBlock().asItem()
			: null;
		return new Candidate(surface, aboveState.isAir() ? null : above, vegetationUpper, desired, cost, refund,
			BlockMaterials.itemCount(existing), vegetationRefund, vegetationUpper == null ? BlockMaterials.itemCount(aboveState) : 1);
	}

	static BlockPos stairSurface(final BlockPos previous, final BlockPos current) {
		return current.getY() > previous.getY() ? current : previous;
	}

	static Direction stairFacing(final Direction travel, final int stepDelta) {
		return stepDelta > 0 ? travel : travel.getOpposite();
	}

	/** Read-only terrain lookup shared by placement and the geometric path preview. */
	public static BlockPos findSurface(final ServerLevel level, final int x, final int z, final int anchorY) {
		int top = Math.min(level.getMaxY() - 1, anchorY + 1);
		int bottom = Math.max(level.getMinY(), anchorY - BlockToolsMod.CONFIG.config().pathVerticalSearch);
		for (int y = top; y >= bottom; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return null;
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.liquid() || state.is(BlockTags.LEAVES)) continue;
			if (isIgnorableVegetation(level, pos, state)) continue;
			if ((state.isSolid() && !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING)) && !state.getCollisionShape(level, pos).isEmpty()) return pos;
		}
		return null;
	}

	private static boolean isIgnorableVegetation(final ServerLevel level, final BlockPos pos, final BlockState state) {
		return state.getFluidState().isEmpty()
			&& state.is(BlockTags.REPLACEABLE)
			&& state.getCollisionShape(level, pos).isEmpty()
			&& level.getBlockEntity(pos) == null;
	}

	private static boolean holdsPathItem(final ServerPlayer player) {
		return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == BlockToolsMod.CONFIG.pathItem()
			|| player.getItemInHand(InteractionHand.OFF_HAND).getItem() == BlockToolsMod.CONFIG.pathItem();
	}

	private static BlockPos offset(final BlockPos center, final Direction direction, final int amount) {
		return center.relative(direction.getClockWise(), amount);
	}

	private static Direction travelDirection(final BlockPos from, final BlockPos to, final Direction fallback) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
		if (Math.abs(dz) > Math.abs(dx)) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
		if (fallback.getAxis().isHorizontal()) return fallback;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static Direction horizontal(final Direction direction) {
		return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
	}

	private static List<BlockPos> line(final BlockPos from, final BlockPos to) {
		List<BlockPos> result = new ArrayList<>();
		int x0 = from.getX();
		int z0 = from.getZ();
		int x1 = to.getX();
		int z1 = to.getZ();
		int dx = Math.abs(x1 - x0);
		int dz = Math.abs(z1 - z0);
		int sx = Integer.compare(x1, x0);
		int sz = Integer.compare(z1, z0);
		int error = dx - dz;
		while (x0 != x1 || z0 != z1) {
			int doubled = 2 * error;
			if (doubled > -dz) {
				error -= dz;
				x0 += sx;
			}
			if (doubled < dx) {
				error += dx;
				z0 += sz;
			}
			double progress = Math.max(Math.abs(x0 - from.getX()), Math.abs(z0 - from.getZ()))
				/ (double) Math.max(1, Math.max(dx, dz));
			int y = (int) Math.round(from.getY() + (to.getY() - from.getY()) * progress);
			result.add(new BlockPos(x0, y, z0));
		}
		return result;
	}

	private static long materialHash(final ServerPlayer player, final BlockPos pos, final String palette) {
		long value = pos.asLong() ^ player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
		value ^= (long) palette.hashCode() * 0x9E3779B97F4A7C15L;
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private record Candidate(
		BlockPos surface,
		BlockPos vegetation,
		BlockPos vegetationUpper,
		BlockState desired,
		Item cost,
		Item refund,
		int refundCount,
		Item vegetationRefund,
		int vegetationRefundCount
	) {
	}

}
