package com.thenathe.blocktools.tunnel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TunnelConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Path path = FabricLoader.getInstance().getConfigDir().resolve("block-tone-tunnel-tools.json");
	private TunnelConfig config = new TunnelConfig();

	public void load() {
		try {
			boolean writable = true;
			config = new TunnelConfig();
			Files.createDirectories(path.getParent());
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					TunnelConfig loaded = GSON.fromJson(reader, TunnelConfig.class);
					if (loaded != null && loaded.configVersion > 1) {
						BlockToolsMod.LOGGER.error(
							"{} uses future tunnel config version {}; using safe defaults without rewriting it.",
							path, loaded.configVersion
						);
						config = new TunnelConfig();
						writable = false;
					} else if (loaded != null) {
						config = loaded;
					}
				}
			}
			config.validate();
			if (writable) save();
			BlockToolsMod.LOGGER.info("Loaded tunnel settings from {}.", path);
		} catch (Exception exception) {
			BlockToolsMod.LOGGER.error("Could not load tunnel settings; retaining safe defaults.", exception);
			config = new TunnelConfig();
		}
	}

	private void save() throws Exception {
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public TunnelConfig config() {
		return config;
	}

	public Item toolItem() {
		return com.thenathe.blocktools.compat.PolymerTools.tunnelItem(BlockToolsMod.CONFIG.legacySelectionItem());
	}

	public Path path() {
		return path;
	}
}
