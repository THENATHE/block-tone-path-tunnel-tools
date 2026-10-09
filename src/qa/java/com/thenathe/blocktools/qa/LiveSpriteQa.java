package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.sprite.SpritePreferences;
import com.thenathe.blocktools.sprite.SpritePreferences.Mode;
import net.minecraft.server.level.ServerPlayer;

/** Persistent override and session-only detection exercised on a live server. */
public final class LiveSpriteQa {
    private static int checks;
    public static int run(ServerPlayer player) throws Exception {
        checks = 0;
        var id = player.getUUID();
        var server = player.level().getServer();
        SpritePreferences.select(player, Mode.AUTO);
        SpritePreferences.forgetSession(id);
        check(!SpritePreferences.high(id), "auto without client defaults16");
        SpritePreferences.report(player, true);
        check(SpritePreferences.high(id), "client32 detection selects32 in auto");
        SpritePreferences.select(player, Mode.LOW);
        check(!SpritePreferences.high(id), "manual16 overrides detected32");
        SpritePreferences.load(server);
        check(SpritePreferences.mode(id) == Mode.LOW && !SpritePreferences.high(id), "manual16 survives disk reload");
        check(!SpritePreferences.hasClient(id), "disk reload does not preserve automatic report");
        SpritePreferences.select(player, Mode.HIGH);
        SpritePreferences.report(player, false);
        check(SpritePreferences.high(id), "manual32 overrides detected16");
        check(!SpritePreferences.high(java.util.UUID.fromString("00000000-0000-0000-0000-000000000032")),
            "one player's manual32 does not affect another player's default16");
        SpritePreferences.forgetSession(id);
        check(SpritePreferences.high(id), "manual32 survives disconnect without report");
        SpritePreferences.load(server);
        check(SpritePreferences.mode(id) == Mode.HIGH && SpritePreferences.high(id), "manual32 survives disk reload");
        SpritePreferences.select(player, Mode.AUTO);
        SpritePreferences.load(server);
        check(SpritePreferences.mode(id) == Mode.AUTO && !SpritePreferences.high(id), "returning to auto removes persisted override");
        return checks;
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
