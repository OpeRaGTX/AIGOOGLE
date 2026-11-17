package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Caravan trade screen: goods for sale on the left (paid in hearth marks from the warehouse), spare stock to sell on the right. */
public final class TradeScreen extends HhScreen {
    private static final int ROWS = 8, ROW_H = 22;
    private Views.Trade view;

    public TradeScreen(Views.Trade view) {
        super(Component.translatable("gui.hearthholm.trade"), 304, 220);
        this.view = view;
    }

    public static void show(Views.Trade view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TradeScreen s) {
            s.view = view;
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new TradeScreen(view));
        }
    }

    private void send(int action, int index) {
        ClientPlayNetworking.send(new Payloads.TradeAction(view.pos(), action, index));
    }

    @Override
    protected void init() {
        super.init();
        int y0 = top + 40;
        for (int i = 0; i < Math.min(ROWS, view.offers().size()); i++) {
            Views.TradeOffer o = view.offers().get(i);
            int idx = i;
            Button b = button(Component.translatable("gui.hearthholm.buy_for", o.price()), left + 108, y0 + i * ROW_H, 42, 18, x -> send(Payloads.TradeAction.BUY, idx));
            b.active = !o.soldOut() && view.marks() >= o.price();
        }
        for (int i = 0; i < Math.min(ROWS, view.sells().size()); i++) {
            Views.SellLine l = view.sells().get(i);
            int idx = i;
            button(Component.translatable("gui.hearthholm.sell_for", Math.min(l.spare(), 256) / l.perMark()), left + 256, y0 + i * ROW_H, 40, 18,
                    x -> send(Payloads.TradeAction.SELL, idx));
        }
        button(Component.translatable("gui.hearthholm.close"), left + panelW - 70, top + panelH - 22, 62, 16, b -> onClose());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void line(GuiGraphicsExtractor g, Component c, int x, int y, int w, int color) {
        List<FormattedCharSequence> parts = font.split(c, w);
        if (!parts.isEmpty()) g.text(font, parts.getFirst(), x, y, color, false);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + 8;
        text(g, view.trader(), x, top + 8, TITLE);
        Component marks = Component.translatable("gui.hearthholm.trade_marks", view.marks());
        text(g, marks, left + panelW - 8 - font.width(marks), top + 8, WARN);
        text(g, Component.translatable("gui.hearthholm.trade_buy"), x, top + 26, DIM);
        text(g, Component.translatable("gui.hearthholm.trade_sell"), left + 160, top + 26, DIM);
        g.fill(left + 154, top + 24, left + 155, top + panelH - 28, TRIM_DARK);
        int y0 = top + 40;
        if (view.offers().isEmpty()) text(g, Component.translatable("gui.hearthholm.trade_nothing"), x, y0 + 4, DIM);
        for (int i = 0; i < Math.min(ROWS, view.offers().size()); i++) {
            Views.TradeOffer o = view.offers().get(i);
            int y = y0 + i * ROW_H;
            g.item(o.stack(), x, y + 1);
            if (o.stack().getCount() > 1) g.itemDecorations(font, o.stack(), x, y + 1);
            line(g, o.label(), x + 20, y + 5, 80, o.soldOut() ? DIM : TEXT);
            if (inside(mx, my, x, y, 100, 18)) g.setTooltipForNextFrame(font, o.label(), mx, my);
        }
        if (view.sells().isEmpty()) g.textWithWordWrap(font, Component.translatable("gui.hearthholm.trade_no_surplus"), left + 160, y0 + 4, 136, DIM);
        for (int i = 0; i < Math.min(ROWS, view.sells().size()); i++) {
            Views.SellLine l = view.sells().get(i);
            int y = y0 + i * ROW_H;
            ItemStack st = l.stack();
            g.item(st, left + 160, y + 1);
            line(g, Component.translatable("gui.hearthholm.trade_spare", l.spare(), l.perMark()), left + 180, y + 5, 74, TEXT);
            if (inside(mx, my, left + 160, y, 94, 18)) g.setTooltipForNextFrame(font, st.getHoverName(), mx, my);
        }
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.trade_hint"), x, top + panelH - 26, 200, DIM);
    }
}
