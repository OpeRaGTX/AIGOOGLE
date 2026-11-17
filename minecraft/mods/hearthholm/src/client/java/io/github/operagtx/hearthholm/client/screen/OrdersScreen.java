package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Order board: the list of "keep N in stock" orders with live status on the left, and a picker
 * on the right (workshop tab → curated item → amount → set).
 */
public final class OrdersScreen extends HhScreen {
    private static final int ROW = 22, ROWS = 7, LIST_W = 206, GRID_COLS = 7;
    private static Workshop lastTab = Workshop.CARPENTRY;
    private Views.Orders view;
    private Workshop tab = lastTab;
    private Item selected = lastTab.catalog().getFirst();
    private int amount = 64;
    private int scroll;

    public OrdersScreen(Views.Orders view) {
        super(Component.translatable("gui.hearthholm.orders"), 364, 226);
        this.view = view;
        int ex = existing(selected);
        if (ex > 0) amount = ex;
    }

    public static void show(Views.Orders view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof OrdersScreen s && s.view.board().equals(view.board())) {
            s.view = view;
            s.scroll = Math.max(0, Math.min(s.scroll, view.lines().size() - ROWS));
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new OrdersScreen(view));
        }
    }

    private void send(Item item, int amount) {
        ClientPlayNetworking.send(new Payloads.OrderAction(view.board(), BuiltInRegistries.ITEM.getKey(item).toString(), amount));
    }

    private int px() { return left + LIST_W + 16; }

    private int existing(Item item) {
        for (Views.OrderLine l : view.lines()) if (BuiltInRegistries.ITEM.getValue(l.item()) == item) return l.target();
        return -1;
    }

    @Override
    protected void init() {
        super.init();
        Workshop[] kinds = java.util.Arrays.stream(Workshop.values()).filter(Workshop::hasCatalog).toArray(Workshop[]::new);
        for (int i = 0; i < kinds.length; i++) {
            Workshop w = kinds[i];
            Profession p = Professions.get(w.profession());
            Button b = button(p.label(), px() + (i % 2) * 68, top + 20 + (i / 2) * 16, 66, 14, x -> {
                tab = w;
                lastTab = w;
                selected = w.catalog().getFirst();
                int ex = existing(selected);
                amount = ex > 0 ? ex : 64;
                rebuildWidgets();
            });
            b.active = w != tab;
        }
        int ay = top + 124;
        int[] steps = {-10, -1, 1, 10};
        int[] xs = {px(), px() + 24, px() + 86, px() + 104};
        int[] ws = {22, 16, 16, 24};
        for (int i = 0; i < steps.length; i++) {
            int d = steps[i];
            button(Component.literal((d > 0 ? "+" : "") + d), xs[i], ay, ws[i], 14,
                    b -> { amount = Math.max(1, Math.min(999, amount + d)); });
        }
        button(Component.translatable(existing(selected) > 0 ? "gui.hearthholm.order_update" : "gui.hearthholm.order_set"),
                px(), ay + 18, 128, 16, b -> send(selected, amount));
        for (int i = scroll; i < view.lines().size() && i < scroll + ROWS; i++) {
            Views.OrderLine l = view.lines().get(i);
            int y = top + 34 + (i - scroll) * ROW;
            Item item = BuiltInRegistries.ITEM.getValue(l.item());
            Button x = button(Component.literal("✖"), left + LIST_W - 6, y + 4, 12, 12, b -> send(item, 0));
            x.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hearthholm.order_remove")));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        text(g, Component.translatable("gui.hearthholm.orders").append(" · ").append(view.name()), left + 8, top + 7, TITLE);
        // orders list
        int lx = left + 8;
        text(g, Component.translatable("gui.hearthholm.orders_list"), lx, top + 22, DIM);
        List<Views.OrderLine> lines = view.lines();
        if (lines.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.hearthholm.orders_empty"), lx + 2, top + 38, LIST_W - 8, DIM);
        }
        for (int i = scroll; i < lines.size() && i < scroll + ROWS; i++) {
            Views.OrderLine l = lines.get(i);
            int y = top + 34 + (i - scroll) * ROW;
            boolean hover = inside(mx, my, lx, y, LIST_W - 16, ROW - 2);
            g.fill(lx, y, lx + LIST_W, y + ROW - 2, hover ? 0xFF3A2D20 : BG2);
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(l.item()));
            g.item(st, lx + 2, y + 2);
            String count = l.stock() + " / " + l.target();
            int cw = font.width(count);
            text(g, font.plainSubstrByWidth(st.getHoverName().getString(), LIST_W - 40 - cw), lx + 21, y + 2, TEXT);
            text(g, count, lx + LIST_W - 18 - cw, y + 2, l.stock() >= l.target() ? GOOD : TEXT);
            text(g, font.plainSubstrByWidth(l.status().getString(), LIST_W - 40), lx + 21, y + 11, l.color());
            if (hover) g.setTooltipForNextFrame(font, l.status(), mx, my);
        }
        if (lines.size() > ROWS) {
            text(g, (scroll + 1) + "-" + Math.min(lines.size(), scroll + ROWS) + " / " + lines.size(), lx, top + panelH - 30, DIM);
        }
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.orders_hint", Workshop.DEFAULT_MEALS),
                lx, top + panelH - 20, LIST_W, DIM);

        // picker
        int x = px(), w = panelW - LIST_W - 24;
        inset(g, x - 4, top + 16, w + 8, panelH - 22);
        for (Views.ShopInfo info : view.shops()) {
            if (!info.kind().equals(tab.key())) continue;
            Component shop = Component.translatable(info.workshop() ? "gui.hearthholm.shop_ok" : "gui.hearthholm.shop_missing", tab.label());
            text(g, font.plainSubstrByWidth(shop.getString(), w), x, top + 54, info.workshop() ? GOOD : BAD);
            Component crafters = Component.translatable("gui.hearthholm.shop_crafters", info.crafters());
            if (!info.furnace() && tab != Workshop.CARPENTRY) crafters = crafters.copy().append(" · ").append(Component.translatable("gui.hearthholm.furnace_no"));
            text(g, font.plainSubstrByWidth(crafters.getString(), w), x, top + 64, info.crafters() > 0 ? TEXT : WARN);
        }
        List<Item> items = tab.catalog();
        for (int i = 0; i < items.size(); i++) {
            int ix = x + (i % GRID_COLS) * 18, iy = top + 76 + (i / GRID_COLS) * 18;
            Item it = items.get(i);
            boolean sel = it == selected;
            g.fill(ix, iy, ix + 17, iy + 17, sel ? TRIM : 0xFF120D09);
            if (sel) g.fill(ix + 1, iy + 1, ix + 16, iy + 16, 0xFF4A3A26);
            ItemStack st = new ItemStack(it);
            g.item(st, ix, iy);
            if (existing(it) > 0) g.fill(ix + 13, iy + 1, ix + 16, iy + 4, GOOD);
            if (inside(mx, my, ix, iy, 17, 17)) g.setTooltipForNextFrame(font, st.getHoverName(), mx, my);
        }
        Component name = new ItemStack(selected).getHoverName();
        text(g, font.plainSubstrByWidth(name.getString(), w), x, top + 113, TITLE);
        String a = String.valueOf(amount);
        text(g, a, x + 63 - font.width(a) / 2, top + 127, TEXT);
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.order_explain", amount, name), x, top + 162, w, DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        List<Item> items = tab.catalog();
        for (int i = 0; i < items.size(); i++) {
            int ix = px() + (i % GRID_COLS) * 18, iy = top + 76 + (i / GRID_COLS) * 18;
            if (inside(e.x(), e.y(), ix, iy, 17, 17)) {
                selected = items.get(i);
                int ex = existing(selected);
                amount = ex > 0 ? ex : 64;
                rebuildWidgets();
                return true;
            }
        }
        for (int i = scroll; i < view.lines().size() && i < scroll + ROWS; i++) {
            int y = top + 34 + (i - scroll) * ROW;
            if (inside(e.x(), e.y(), left + 8, y, LIST_W - 16, ROW - 2)) {
                Item it = BuiltInRegistries.ITEM.getValue(view.lines().get(i).item());
                Workshop w = Workshop.forItem(it);
                if (w != null) {
                    tab = w;
                    lastTab = w;
                    selected = it;
                    amount = view.lines().get(i).target();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < px() - 6) {
            scroll = Math.max(0, Math.min(Math.max(0, view.lines().size() - ROWS), scroll - (int) Math.signum(sy)));
            rebuildWidgets();
            return true;
        }
        amount = Math.max(1, Math.min(999, amount + (int) Math.signum(sy)));
        return true;
    }
}
