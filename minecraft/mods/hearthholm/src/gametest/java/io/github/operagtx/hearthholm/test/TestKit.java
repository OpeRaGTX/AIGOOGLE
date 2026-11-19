package io.github.operagtx.hearthholm.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Shared helpers of the wave tests (language switch, window size). New waves reuse these instead of copying them (J1/I2 keep their private copies). */
final class TestKit {
    private TestKit() {}

    /** Switches the client language and reloads resources; GUI checks and screenshots in RU/EN use this. */
    static void setLang(ClientGameTestContext ctx, String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
        });
        var fut = ctx.computeOnClient(mc -> mc.reloadResourcePacks());
        ctx.waitFor(mc -> fut.isDone(), 2400);
        ctx.waitTicks(120);
    }

    /** The test window at GUI scale 2 (the layout the UI was designed for). */
    static void setWindow(ClientGameTestContext ctx, int w, int h) {
        ctx.getInput().resizeWindow(w, h);
        ctx.runOnClient(mc -> mc.options.guiScale().set(2));
        ctx.waitTicks(8);
    }
}
