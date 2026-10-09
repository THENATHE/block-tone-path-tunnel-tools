package com.thenathe.blocktools.compat;

import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Permission checks shared by direct world edits, which do not fire normal block events. */
public final class BuildPermissions {
	private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

	private BuildPermissions() {
	}

	/** Resolve the optional integration after Fabric has established the installed mod list. */
	public static void initialize() {
		protection();
	}

	public static boolean canChange(final ServerPlayer player, final BlockPos pos) {
		if (player == null || pos == null || !player.isAlive() || player.isSpectator() || !player.mayBuild()) return false;
		ServerLevel level = player.level();
		if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
			|| !level.getWorldBorder().isWithinBounds(pos)
			|| !level.mayInteract(player, pos) || !player.mayInteract(level, pos)) return false;
		return protection().canChange(player, level, pos);
	}

	private static OptionalProtection protection() {
		return PreciousStonesHolder.INSTANCE;
	}

	private static final class PreciousStonesHolder {
		private static final OptionalProtection INSTANCE = createProtection();
	}

	private static OptionalProtection createProtection() {
		try {
			if (!FabricLoader.getInstance().isModLoaded("preciousstones")) return (player, level, pos) -> true;
			ClassLoader loader = BuildPermissions.class.getClassLoader();
			return new PreciousStonesProtection(
				Class.forName("com.thenathe.preciousstones.PreciousStonesMod", false, loader),
				Class.forName("com.thenathe.preciousstones.protection.ProtectionHandler", false, loader)
			);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
			logFailure(failure);
			return (player, level, pos) -> false;
		}
	}

	private static void logFailure(final Throwable failure) {
		if (FAILURE_LOGGED.compareAndSet(false, true)) {
			BlockToolsMod.LOGGER.error("Precious Stones protection integration failed. Block Tools world edits are disabled to preserve protected blocks.", failure);
		}
	}

	@FunctionalInterface
	private interface OptionalProtection {
		boolean canChange(ServerPlayer player, ServerLevel level, BlockPos pos);
	}

	/** Only public, stable Precious Stones API is accessed; there is no required dependency. */
	static final class PreciousStonesProtection implements OptionalProtection {
		private final Field fields;
		private final Method atStone;
		private final Method ownedBlockAt;
		private final Method canBreak;
		private final Method canPlace;
		private volatile boolean failed;

		PreciousStonesProtection(final Class<?> modClass, final Class<?> handlerClass) throws ReflectiveOperationException {
			fields = modClass.getField("FIELDS");
			if (!Modifier.isStatic(fields.getModifiers())) throw new NoSuchFieldException("Precious Stones FIELDS must be static");
			atStone = fields.getType().getMethod("atStone", String.class, BlockPos.class);
			ownedBlockAt = fields.getType().getMethod("ownedBlockAt", String.class, BlockPos.class);
			canBreak = permissionMethod(handlerClass, "canBreak");
			canPlace = permissionMethod(handlerClass, "canPlace");
		}

		private static Method permissionMethod(final Class<?> type, final String name) throws NoSuchMethodException {
			Method method = type.getMethod(name, ServerPlayer.class, ServerLevel.class, BlockPos.class, boolean.class);
			if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != boolean.class) {
				throw new NoSuchMethodException("Precious Stones " + name + " must be static and return boolean");
			}
			return method;
		}

		@Override
		public boolean canChange(final ServerPlayer player, final ServerLevel level, final BlockPos pos) {
			if (failed) return false;
			try {
				return allows(player, level, pos, level.dimension().identifier().toString());
			} catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
				failed = true;
				logFailure(failure);
				return false;
			}
		}

		boolean allows(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final String dimension)
			throws ReflectiveOperationException {
			Object store = fields.get(null);
			if (store == null) throw new IllegalStateException("Precious Stones field store is unavailable");
			// Direct edits cannot maintain claim-stone and owned-block bookkeeping, even for owners/admins.
			if (atStone.invoke(store, dimension, pos) != null || ownedBlockAt.invoke(store, dimension, pos) != null) return false;
			return Boolean.TRUE.equals(canBreak.invoke(null, player, level, pos, false))
				&& Boolean.TRUE.equals(canPlace.invoke(null, player, level, pos, false));
		}
	}
}
