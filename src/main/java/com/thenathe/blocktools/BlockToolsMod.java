package com.thenathe.blocktools;

import com.thenathe.blocktools.command.BlockToolsCommands;
import com.thenathe.blocktools.config.ConfigManager;
import com.thenathe.blocktools.path.PathService;
import com.thenathe.blocktools.selection.SelectionService;
import com.thenathe.blocktools.selection.SelectionVisualization;
import com.thenathe.blocktools.state.PlayerStateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.thenathe.blocktools.tunnel.*;
import com.thenathe.blocktools.compat.BuildPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockToolsMod implements ModInitializer {
	public static final String MOD_ID = "blocktonepathtools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ConfigManager CONFIG = new ConfigManager();
	public static final PlayerStateManager STATES = new PlayerStateManager();
	public static final TunnelConfigManager TUNNEL_CONFIG = new TunnelConfigManager();
	public static final TunnelStateManager TUNNELS = new TunnelStateManager();

	@Override
	public void onInitialize() {
		com.thenathe.blocktools.compat.PolymerTools.initialize();
		CONFIG.load();
		TUNNEL_CONFIG.load();
		com.thenathe.blocktools.compat.ToolRecipes.register();
		com.thenathe.blocktools.sprite.SpritePreferences.initialize();
		BuildPermissions.initialize();
		TunnelCommands.register();
		TunnelService.register();
		BlockToolsCommands.register();
		SelectionService.register();
		SelectionVisualization.register();
		PathService.register();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SelectionVisualization.remove(handler.getPlayer().getUUID());
            STATES.remove(handler.getPlayer().getUUID());
            TUNNELS.remove(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SelectionVisualization.clear();
            STATES.clear();
            TUNNELS.clear();
        });
		LOGGER.info("Block Tone, Path & Tunnel Tools initialized with server-authoritative vanilla-client support.");
	}

    public static void activateTunnel(final java.util.UUID player) {
        SelectionVisualization.hidePath(player);
        var path = STATES.path(player);
        path.enabled = false;
        path.resetTrail();
    }

    public static void activatePath(final java.util.UUID player) {
        SelectionVisualization.hideTunnel(player);
        var tunnel = TUNNELS.state(player);
        tunnel.enabled = false;
        tunnel.resetTrail();
    }

	public static Component message(final String text) {
		return tagged("Block Tools", ChatFormatting.GOLD, text, ChatFormatting.WHITE);
	}

	public static Component error(final String text) {
		return tagged("Block Tools", ChatFormatting.GOLD, text, ChatFormatting.RED);
	}

	public static Component toneMessage(final String text) {
		return tagged("Block Toning Tool", ChatFormatting.LIGHT_PURPLE, text, ChatFormatting.WHITE);
	}

	public static Component toneError(final String text) {
		return tagged("Block Toning Tool", ChatFormatting.LIGHT_PURPLE, text, ChatFormatting.RED);
	}

	public static Component pathMessage(final String text) {
		return tagged("Path Tool", ChatFormatting.AQUA, text, ChatFormatting.WHITE);
	}

	public static Component pathError(final String text) {
		return tagged("Path Tool", ChatFormatting.AQUA, text, ChatFormatting.RED);
	}

	private static Component tagged(
		final String heading,
		final ChatFormatting headingColor,
		final String text,
		final ChatFormatting textColor
	) {
		return ChatStyling.tagged(heading, headingColor, text, textColor);
	}
}
