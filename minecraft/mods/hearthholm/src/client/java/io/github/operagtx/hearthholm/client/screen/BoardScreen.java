package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The bulletin board: up to three requests with progress bars and a hand-in / claim button each. */
public final class BoardScreen extends HhScreen {
    private Views.Board view;

    public BoardScreen(Views.Board view) {
        super(Component.translatable("gui.hearthholm.board"), 280, 190);
        this.view = view;
    }

    public static void show(Views.Board view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof BoardScreen s) {
            s.view = view;
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new BoardScreen(view));
        }
    }

    private void send(int action, int quest) {
        ClientPlayNetworking.send(new Payloads.BoardAction(view.pos(), action, quest));
    }

    @Override
    protected void init() {
        super.init();
        int y = top + 30;
        for (Views.QuestLine q : view.quests()) {
            if (q.claimed()) {
                Button b = button(Component.translatable("gui.hearthholm.quest_claimed"), left + panelW - 78, y + 14, 70, 16, x -> {});
                b.active = false;
            } else if (q.done()) {
                button(Component.translatable("gui.hearthholm.quest_claim"), left + panelW - 78, y + 14, 70, 16, x -> send(Payloads.BoardAction.CLAIM, q.id()));
            } else if (q.icon().is(net.minecraft.world.item.Items.IRON_SWORD)) {
                Button b = button(Component.translatable("gui.hearthholm.quest_hunt"), left + panelW - 78, y + 14, 70, 16, x -> {});
                b.active = false;
            } else {
                Button b = button(Component.translatable("gui.hearthholm.quest_deliver"), left + panelW - 78, y + 14, 70, 16, x -> send(Payloads.BoardAction.DELIVER, q.id()));
                b.active = q.canDeliver();
            }
            y += 50;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g) {
        text(g, view.name(), left + 8, top + 8, TITLE);
        text(g, Component.translatable("gui.hearthholm.board_hint"), left + 8, top + 19, DIM);
        int y = top + 30;
        if (view.quests().isEmpty()) text(g, Component.translatable("gui.hearthholm.board_empty"), left + 8, y + 6, DIM);
        for (Views.QuestLine q : view.quests()) {
            inset(g, left + 6, y, panelW - 12, 46);
            g.item(q.icon(), left + 12, y + 6);
            g.textWithWordWrap(font, q.text(), left + 34, y + 5, panelW - 120, q.claimed() ? DIM : TEXT);
            text(g, q.reward(), left + 34, y + 30, WARN);
            bar(g, left + 12, y + 26, 16, 4, q.count() == 0 ? 0 : q.progress() / (float) q.count(), q.done() ? GOOD : 0xFFE8B84A);
            text(g, q.progress() + "/" + q.count(), left + panelW - 78, y + 4, q.done() ? GOOD : TEXT);
            y += 50;
        }
    }
}
