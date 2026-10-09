package com.thenathe.blocktools.selection;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.state.PlayerStateManager.SelectionState;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class SelectionService {
	private SelectionService() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(SelectionService::selectRight);
		AttackBlockCallback.EVENT.register(SelectionService::selectLeft);
	}

	private static InteractionResult selectRight(
		final Player player,
		final Level level,
		final InteractionHand hand,
		final BlockHitResult hit
	) {
		if (hand != InteractionHand.MAIN_HAND || player.getItemInHand(hand).getItem() != BlockToolsMod.CONFIG.selectionItem()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) return InteractionResult.FAIL;
		return select(serverPlayer, hit.getBlockPos(), 2);
	}

	private static InteractionResult selectLeft(
		final Player player,
		final Level level,
		final InteractionHand hand,
		final BlockPos pos,
		final Direction direction
	) {
		if (hand != InteractionHand.MAIN_HAND || player.getItemInHand(hand).getItem() != BlockToolsMod.CONFIG.selectionItem()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) return InteractionResult.FAIL;
		InteractionResult result = select(serverPlayer, pos, 1);
        // Polymer redirects vanilla nugget mining here: always stop the break attempt.
        return com.thenathe.blocktools.compat.PolymerTools.active() ? InteractionResult.FAIL : result;
	}

	private static InteractionResult select(final ServerPlayer serverPlayer, final BlockPos point, final int index) {
		SelectionState selection = BlockToolsMod.STATES.selection(serverPlayer.getUUID());
		if (serverPlayer.isShiftKeyDown()) {
			selection.clear();
			SelectionVisualization.hide(serverPlayer.getUUID());
			serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.empty(), true);
			serverPlayer.sendSystemMessage(BlockToolsMod.toneMessage("Selection cleared."));
			return InteractionResult.SUCCESS;
		}

		if (!selection.set(serverPlayer.level(), index, point)) {
            SelectionVisualization.renewSelection(serverPlayer);
            return InteractionResult.SUCCESS;
        }
		SelectionVisualization.show(serverPlayer);
		serverPlayer.sendSystemMessage(BlockToolsMod.toneMessage(
			"Point " + index + " selected at " + coordinates(point)
				+ (selection.complete()
					? ". Selection complete; run `/blocktone to:<materials> [from:<blocks>]`."
					: index == 1 ? ". Right-click to select Point 2." : ". Left-click to select Point 1.")
		));
		return InteractionResult.SUCCESS;
	}

	private static String coordinates(final BlockPos point) {
		return "(" + point.getX() + ", " + point.getY() + ", " + point.getZ() + ")";
	}
}
