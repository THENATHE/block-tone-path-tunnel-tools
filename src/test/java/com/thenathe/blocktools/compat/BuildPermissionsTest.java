package com.thenathe.blocktools.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Exercises optional-integration API behavior without requiring a running Minecraft server. */
public final class BuildPermissionsTest {
	private static int checks;

	public static void main(final String[] args) throws Exception {
		run();
		System.out.println("Build permissions API checks passed: " + checks);
	}

	public static void run() throws Exception {
		var bridge = new BuildPermissions.PreciousStonesProtection(FakeMod.class, FakeHandler.class);
		reset();
		check(bridge.allows(null, null, null, "minecraft:overworld"), "Unclaimed editable blocks are allowed");
		check(FakeHandler.breakCalls == 1 && FakeHandler.placeCalls == 1, "Both break and place are checked");
		check(!FakeHandler.notified, "Checks do not notify players");
		check("minecraft:overworld".equals(FakeMod.FIELDS.dimension), "Dimension is passed to the field store");

		reset();
		FakeMod.FIELDS.stone = new Object();
		check(!bridge.allows(null, null, null, "minecraft:the_nether"), "Claim stones are never edited");
		check(FakeHandler.breakCalls == 0 && FakeHandler.placeCalls == 0, "Claim-stone veto precedes bypass-capable APIs");

		reset();
		FakeMod.FIELDS.ownedBlock = new Object();
		check(!bridge.allows(null, null, null, "minecraft:overworld"), "Owned blocks are never directly edited");

		reset();
		FakeHandler.breakAllowed = false;
		check(!bridge.allows(null, null, null, "minecraft:overworld"), "Denied breaking blocks edits");
		check(FakeHandler.placeCalls == 0, "Denied breaking short-circuits placement");

		reset();
		FakeHandler.placeAllowed = false;
		check(!bridge.allows(null, null, null, "minecraft:overworld"), "Denied placement blocks edits");

		reset();
		FakeHandler.throwOnCheck = true;
		try {
			bridge.allows(null, null, null, "minecraft:overworld");
			throw new AssertionError("Invocation failures must surface to fail-closed wrapper");
		} catch (java.lang.reflect.InvocationTargetException expected) {
			checks++;
		}

		try {
			new BuildPermissions.PreciousStonesProtection(FakeMod.class, BrokenHandler.class);
			throw new AssertionError("Incompatible method return type must be rejected");
		} catch (NoSuchMethodException expected) {
			checks++;
		}
		try {
			new BuildPermissions.PreciousStonesProtection(BrokenMod.class, FakeHandler.class);
			throw new AssertionError("Nonstatic FIELDS API must be rejected");
		} catch (NoSuchFieldException expected) {
			checks++;
		}
		reset();
	}

	private static void reset() {
		FakeMod.FIELDS.stone = null;
		FakeMod.FIELDS.ownedBlock = null;
		FakeHandler.breakAllowed = true;
		FakeHandler.placeAllowed = true;
		FakeHandler.breakCalls = 0;
		FakeHandler.placeCalls = 0;
		FakeHandler.notified = false;
		FakeHandler.throwOnCheck = false;
	}

	private static void check(final boolean value, final String message) {
		if (!value) throw new AssertionError(message);
		checks++;
	}

	public static final class FakeMod {
		public static final FakeStore FIELDS = new FakeStore();
	}

	public static final class BrokenMod {
		public final FakeStore FIELDS = new FakeStore();
	}

	public static final class FakeStore {
		Object stone;
		Object ownedBlock;
		String dimension;

		public Object atStone(final String dimension, final BlockPos pos) {
			this.dimension = dimension;
			return stone;
		}

		public Object ownedBlockAt(final String dimension, final BlockPos pos) {
			this.dimension = dimension;
			return ownedBlock;
		}
	}

	public static final class FakeHandler {
		static boolean breakAllowed;
		static boolean placeAllowed;
		static int breakCalls;
		static int placeCalls;
		static boolean notified;
		static boolean throwOnCheck;

		public static boolean canBreak(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final boolean notify) {
			if (throwOnCheck) throw new IllegalStateException("Simulated integration failure");
			breakCalls++;
			notified |= notify;
			return breakAllowed;
		}

		public static boolean canPlace(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final boolean notify) {
			placeCalls++;
			notified |= notify;
			return placeAllowed;
		}
	}

	public static final class BrokenHandler {
		public static String canBreak(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final boolean notify) {
			return "true";
		}
	}
}
