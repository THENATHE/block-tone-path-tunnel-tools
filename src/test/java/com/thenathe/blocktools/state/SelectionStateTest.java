package com.thenathe.blocktools.state;

import net.minecraft.core.BlockPos;

public final class SelectionStateTest {
	private SelectionStateTest() {
	}

	public static void run() {
		PlayerStateManager.SelectionState selection = new PlayerStateManager.SelectionState();
		BlockPos first = new BlockPos(1, 2, 3);
		BlockPos second = new BlockPos(10, 20, 30);
		selection.set("minecraft:overworld", 1, first);
		selection.set("minecraft:overworld", 2, second);
		require(selection.complete(), "setting Point 1 and Point 2 did not complete the selection");
		require(selection.pointOne().equals(first), "left-click slot did not retain Point 1");
		require(selection.pointTwo().equals(second), "right-click slot did not retain Point 2");
		require(!selection.set("minecraft:overworld", 1, first), "duplicate Point 1 selection was not suppressed");
		require(!selection.set("minecraft:overworld", 2, second), "duplicate Point 2 selection was not suppressed");

		BlockPos updatedFirst = new BlockPos(4, 5, 6);
		require(selection.set("minecraft:overworld", 1, updatedFirst), "a changed Point 1 was suppressed");
		require(selection.pointOne().equals(updatedFirst), "reselecting Point 1 did not update Point 1");
		require(selection.pointTwo().equals(second), "reselecting Point 1 reset Point 2");

		BlockPos netherSecond = new BlockPos(7, 8, 9);
		require(selection.set("minecraft:the_nether", 2, netherSecond), "a cross-dimension selection was suppressed");
		require(selection.pointOne() == null, "changing dimension retained Point 1 from another dimension");
		require(selection.pointTwo().equals(netherSecond), "right-click did not independently set Point 2");
		require(!selection.complete(), "a cross-dimension partial selection was marked complete");
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
