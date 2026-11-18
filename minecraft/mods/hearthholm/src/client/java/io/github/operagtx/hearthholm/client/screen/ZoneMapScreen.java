package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.worksite.SiteOption;
import io.github.operagtx.hearthholm.worksite.WorkSiteType;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wave J2: the map screen of a zone work site (the lumberjack's chopping block; the miner of J3 reuses it). Left: the
 * {@link ZoneMapWidget}; right: tools, brush size, zoom, the site's options, an optional Y-layer row, legend and cursor info.
 */
public final class ZoneMapScreen extends HhScreen {
    private static final int RX = 220, RW = 176;   // right column, relative to the panel

    private Views.WorkSite view;
    private final WorkSiteType type;
    private final WorkZone.Spec spec;
    private ZoneMapWidget map;
    private int layerY;
    private boolean confirmClear;
    private long confirmUntil;
    private long lastEdit = -1000, lastRefresh;
    private boolean editDirty;
    private boolean confirmEverything;
    private Button clearAllButton;
    private int sliderX, sliderY;
    private boolean sliding;
    private Button clearButton;
    private final List<Button> toolButtons = new ArrayList<>();
    private final Button[] zoomButtons = new Button[3];

    public ZoneMapScreen(Payloads.ZoneMapData data) {
        super(Component.translatable("gui.hearthholm.zone.title", WorkSiteTypes.get(data.view().type()).label()), 404, 230);
        this.view = data.view();
        this.type = WorkSiteTypes.get(view.type());
        WorkZone.Spec sp = WorkZone.specOf(view.type());
        this.spec = sp == null ? new WorkZone.Spec(data.size(), data.cell(), false) : sp;
        this.layerY = data.layerY();
        this.pendingData = data;
    }

    private Payloads.ZoneMapData pendingData;

    public static void show(Payloads.ZoneMapData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ZoneMapScreen s && s.view.pos().equals(data.view().pos())) {
            s.update(data);
        } else {
            mc.setScreenAndShow(new ZoneMapScreen(data));
        }
    }

    /** The server answered a work-site action (option change) with a fresh view. */
    public static boolean updateView(Views.WorkSite v) {
        if (Minecraft.getInstance().gui.screen() instanceof ZoneMapScreen s && s.view.pos().equals(v.pos())) {
            s.view = v;
            s.rebuildWidgets();
            return true;
        }
        return false;
    }

    public ZoneMapWidget widget() { return map; }

    private void update(Payloads.ZoneMapData d) {
        boolean sameOptions = d.view().values().equals(view.values());
        this.view = d.view();
        if (map == null) { pendingData = d; return; }
        boolean fresh = minecraft == null || minecraft.level == null || minecraft.level.getGameTime() - lastEdit > 30;
        map.setData(d, fresh && !map.busy());
        if (!sameOptions) rebuildWidgets();
    }

    private void sendEdit(int op, List<Payloads.Rect> rects) {
        lastEdit = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        editDirty = true;
        ClientPlayNetworking.send(new Payloads.ZoneEdit(view.pos(), map.layer(), op, List.copyOf(rects), spec.layered() ? map.height() : 0));
    }

    private void sendOption(String key, int value) {
        editDirty = true;
        ClientPlayNetworking.send(new Payloads.WorkSiteAction(view.pos(), key, value));
    }

    private int opt(String key, int def) { return view.values().getOrDefault(key, def); }

    @Override
    protected void init() {
        super.init();
        if (map == null) {
            map = new ZoneMapWidget(left + 10, top + 18, pendingData, this::sendEdit);
            pendingData = null;
        }
        toolButtons.clear();
        if (spec.layered()) { initMiner(); return; }
        int x0 = left + RX, y = top + 16;
        ZoneMapWidget.Tool[] tools = {ZoneMapWidget.Tool.BRUSH, ZoneMapWidget.Tool.RECT, ZoneMapWidget.Tool.ERASER, ZoneMapWidget.Tool.ERASE_RECT};
        String[] keys = {"brush", "rect", "eraser", "erase_rect"};
        for (int i = 0; i < 4; i++) {
            ZoneMapWidget.Tool t = tools[i];
            Button b = button(fitLabel(Component.translatable("gui.hearthholm.zone.tool." + keys[i]), 78), x0 + (i % 2) * 90, y + (i / 2) * 20, 86, 16, bb -> map.setTool(t));
            b.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.zone.tool." + keys[i] + ".tip")));
            toolButtons.add(b);
        }
        clearButton = button(fitLabel(Component.translatable(confirmClear ? "gui.hearthholm.zone.clear_confirm" : "gui.hearthholm.zone.clear"), 168), x0, y + 40, RW, 16, b -> {
            long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
            if (confirmClear && now <= confirmUntil) { map.clearAll(); confirmClear = false; }
            else { confirmClear = true; confirmUntil = now + 80; }
            rebuildWidgets();
        });
        clearButton.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.zone.clear.tip")));
        y += 62;
        // brush size
        button(Component.literal("-"), x0, y, 16, 16, b -> { map.setBrush(map.brush() - 1); });
        button(Component.literal("+"), x0 + RW - 16, y, 16, 16, b -> { map.setBrush(map.brush() + 1); });
        y += 20;
        // zoom: fit / 2x / 4x
        String[] zl = {"gui.hearthholm.zone.zoom_fit", "gui.hearthholm.zone.zoom_2", "gui.hearthholm.zone.zoom_4"};
        for (int i = 0; i < 3; i++) {
            int lv = i;
            zoomButtons[i] = button(Component.translatable(zl[i]), x0 + i * 60, y, 56, 16, b -> map.setZoom(lv));
            zoomButtons[i].setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.zone.zoom.tip")));
        }
        y += 22;
        // the site's options (auto radius, replant, plant in zone ...)
        for (SiteOption o : type.options()) {
            int value = view.values().getOrDefault(o.key(), o.def());
            if (o.kind() == SiteOption.Kind.TOGGLE) {
                Component label = Component.empty().append(o.label()).append(": ").append(o.valueLabel(value));
                Button b = button(fitLabel(label, 168), x0, y, RW, 16, bb -> sendOption(o.key(), o.next(value, 1)));
                if (language("option.hearthholm." + o.key() + ".tip")) b.setTooltip(Tooltip.create(Component.translatable("option.hearthholm." + o.key() + ".tip")));
            } else if (o.kind() == SiteOption.Kind.RANGE) {
                button(Component.literal("<"), x0, y, 16, 16, bb -> sendOption(o.key(), o.next(value, -1)));
                button(Component.literal(">"), x0 + RW - 16, y, 16, 16, bb -> sendOption(o.key(), o.next(value, 1)));
            }
            y += 18;
        }
        if (spec.layered()) {
            button(Component.literal("<"), x0, y, 16, 16, b -> changeLayer(-1));
            button(Component.literal(">"), x0 + RW - 16, y, 16, 16, b -> changeLayer(1));
        }
    }

    // ---- wave J3: the miner's layout ------------------------------------------------------------

    private static final int HW = 86;   // half-width button

    private void initMiner() {
        int x0 = left + RX, y = top + 16;
        boolean drawn = opt("plan", 0) == 1;
        java.util.Arrays.fill(zoomButtons, null);
        map.setEditable(drawn);
        if (drawn) {
            ZoneMapWidget.Tool[] tools = {ZoneMapWidget.Tool.BRUSH, ZoneMapWidget.Tool.RECT, ZoneMapWidget.Tool.ERASER, ZoneMapWidget.Tool.ERASE_RECT};
            String[] keys = {"brush", "rect", "eraser", "erase_rect"};
            for (int i = 0; i < 4; i++) {
                ZoneMapWidget.Tool t = tools[i];
                Button b = button(fitLabel(Component.translatable("gui.hearthholm.zone.tool." + keys[i]), HW - 8), x0 + (i % 2) * 90, y + (i / 2) * 18, HW, 16, bb -> map.setTool(t));
                b.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.zone.tool." + keys[i] + ".tip")));
                toolButtons.add(b);
            }
            y += 36;
            Button cl = button(fitLabel(Component.translatable("gui.hearthholm.mine.clear_layer"), HW - 8), x0, y, HW, 16, b -> map.clearAll());
            cl.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.clear_layer.tip")));
            clearAllButton = button(fitLabel(Component.translatable(confirmEverything ? "gui.hearthholm.mine.confirm" : "gui.hearthholm.mine.clear_all"), HW - 8), x0 + 90, y, HW, 16, b -> {
                long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
                if (confirmEverything && now <= confirmUntil) { map.clearEverything(); confirmEverything = false; }
                else { confirmEverything = true; confirmUntil = now + 80; }
                rebuildWidgets();
            });
            clearAllButton.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.clear_all.tip")));
            y += 18;
            // brush size (cells) on the left, zoom on the right
            button(Component.literal("-"), x0, y, 16, 16, b -> map.setBrush(map.brush() - 1));
            button(Component.literal("+"), x0 + HW - 16, y, 16, 16, b -> map.setBrush(map.brush() + 1));
            String[] zl = {"gui.hearthholm.zone.zoom_fit", "gui.hearthholm.zone.zoom_2", "gui.hearthholm.zone.zoom_4"};
            for (int i = 0; i < 3; i++) {
                int lv = i;
                zoomButtons[i] = button(Component.translatable(zl[i]), x0 + 90 + i * 30, y, 28, 16, b -> map.setZoom(lv));
                zoomButtons[i].setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.zone.zoom.tip")));
            }
            y += 18;
            // brush height on the left, torches on the right
            button(Component.literal("-"), x0, y, 16, 16, b -> map.setHeight(map.height() - 1)).setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.height.tip")));
            button(Component.literal("+"), x0 + HW - 16, y, 16, 16, b -> map.setHeight(map.height() + 1)).setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.height.tip")));
            torchButton(x0 + 90, y);
            y += 18;
            // the layer: slider bar, then - label + and the jump to the marker's level
            sliderX = x0;
            sliderY = y;
            y += 14;
            button(Component.literal("-"), x0, y, 16, 16, b -> changeLayer(-1));
            button(Component.literal("+"), x0 + HW - 16, y, 16, 16, b -> changeLayer(1));
            Button jump = button(fitLabel(Component.translatable("gui.hearthholm.mine.jump"), HW - 8), x0 + 90, y, HW, 16, b -> setLayer(map.data().info().markerY()));
            jump.setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.jump.tip")));
            y += 20;
        } else {
            // automatic layout: the old options live here
            for (SiteOption o : type.options()) {
                if (o.key().equals("mode")) {
                    int value = opt("mode", 0);
                    Button b = button(fitLabel(Component.empty().append(o.label()).append(": ").append(o.valueLabel(value)), RW - 8), x0, y, RW, 16, bb -> sendOption("mode", o.next(value, 1)));
                    b.setTooltip(Tooltip.create(Component.translatable("option.hearthholm.mode.tip")));
                    y += 18;
                } else if (o.key().equals("depth") || o.key().equals("length")) {
                    int value = opt(o.key(), o.def());
                    button(Component.literal("<"), x0, y, 16, 16, bb -> sendOption(o.key(), o.next(value, -1)));
                    button(Component.literal(">"), x0 + RW - 16, y, 16, 16, bb -> sendOption(o.key(), o.next(value, 1)));
                    y += 18;
                }
            }
            SiteOption br = type.option("branches");
            int bv = opt("branches", 1);
            button(fitLabel(Component.empty().append(br.label()).append(": ").append(br.valueLabel(bv)), HW - 8), x0, y, HW, 16, bb -> sendOption("branches", br.next(bv, 1)));
            torchButton(x0 + 90, y);
            y += 18 + 40;
        }
        // the plan switch and what happens when the plan is done
        planY = y;
        button(fitLabel(Component.translatable(drawn ? "gui.hearthholm.mine.plan_drawn" : "gui.hearthholm.mine.plan_auto"), RW - 8), x0, y, RW, 16,
                b -> sendOption("plan", drawn ? 0 : 1)).setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.plan.tip")));
        y += 18;
        if (drawn) {
            boolean after = opt("after", 0) == 1;
            button(fitLabel(Component.translatable(after ? "gui.hearthholm.mine.after_auto" : "gui.hearthholm.mine.after_idle"), RW - 8), x0, y, RW, 16,
                    b -> sendOption("after", after ? 0 : 1)).setTooltip(Tooltip.create(Component.translatable("gui.hearthholm.mine.after.tip")));
        }
    }

    private int planY;

    private void torchButton(int x, int y) {
        SiteOption o = type.option("torches");
        int v = opt("torches", 1);
        button(fitLabel(Component.empty().append(o.label()).append(": ").append(o.valueLabel(v)), HW - 8), x, y, HW, 16, b -> sendOption("torches", o.next(v, 1)));
    }

    /** Shows another layer (the slider, the +/- buttons and tests use this); repeated until the server's snapshot arrives. */
    public void showLayer(int y) { setLayer(y); }

    private void setLayer(int y) {
        Payloads.MineInfo info = map.data().info();
        layerY = Math.max(info.minY(), Math.min(info.maxY(), y));
        ClientPlayNetworking.send(new Payloads.ZoneMapRequest(view.pos(), layerY));
        lastRefresh = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    /** The layer slider bar: drag or click to choose the Y level. */
    private boolean sliderHit(double mx, double my) {
        return spec.layered() && opt("plan", 0) == 1 && mx >= sliderX && mx < sliderX + RW && my >= sliderY && my < sliderY + 12;
    }

    private void sliderTo(double mx) {
        Payloads.MineInfo info = map.data().info();
        double f = Math.max(0, Math.min(1, (mx - sliderX - 3) / (RW - 6)));
        int y = info.minY() + (int) Math.round(f * (info.maxY() - info.minY()));
        if (y != layerY) setLayer(y);
    }

    private String timeText(int seconds) {
        if (seconds < 90) return Component.translatable("gui.hearthholm.mine.t_s", Math.max(1, seconds)).getString();
        if (seconds < 5400) return Component.translatable("gui.hearthholm.mine.t_min", Math.round(seconds / 60f)).getString();
        return Component.translatable("gui.hearthholm.mine.t_h", seconds / 3600, (seconds % 3600) / 60).getString();
    }

    private void contentMiner(GuiGraphicsExtractor g, int mx, int my) {
        int x0 = left + RX;
        boolean drawn = opt("plan", 0) == 1;
        Payloads.MineInfo info = map.data().info();
        if (drawn) {
            int y = top + 16 + 36 + 18;
            String bs = fit(Component.translatable("gui.hearthholm.mine.brush_size", map.brush() * 2 + "b"), HW - 36);
            g.text(font, bs, x0 + (HW - font.width(bs)) / 2, y + 4, TEXT, false);
            y += 18;
            String hs = fit(Component.translatable("gui.hearthholm.mine.height", map.height()), HW - 36);
            g.text(font, hs, x0 + (HW - font.width(hs)) / 2, y + 4, TEXT, false);
            y += 18;
            // the slider
            int sw = RW;
            inset(g, sliderX, sliderY + 4, sw, 4);
            double f = info.maxY() > info.minY() ? (layerY - info.minY()) / (double) (info.maxY() - info.minY()) : 0;
            int tx = sliderX + 3 + (int) Math.round(f * (sw - 6));
            double fm = info.maxY() > info.minY() ? (info.markerY() - info.minY()) / (double) (info.maxY() - info.minY()) : 0;
            int mxk = sliderX + 3 + (int) Math.round(fm * (sw - 6));
            g.fill(mxk, sliderY + 2, mxk + 1, sliderY + 10, 0xFFFFFFFF);   // the marker's level
            g.fill(tx - 2, sliderY, tx + 3, sliderY + 12, 0xFF000000);
            g.fill(tx - 1, sliderY + 1, tx + 2, sliderY + 11, sliding ? 0xFFFFE08A : TRIM);
            String ls = fit(Component.translatable("gui.hearthholm.mine.layer", layerY), HW - 36);
            g.text(font, ls, x0 + (HW - font.width(ls)) / 2, sliderY + 14 + 4, TITLE, false);
        } else {
            g.text(font, fit(Component.translatable("gui.hearthholm.mine.auto_note"), RW), x0, top + 16 + 18 * 5 + 2, DIM, false);
            int oy = top + 16 + 18;
            for (String k : new String[]{"depth", "length"}) {
                String s = fit(Component.translatable("option.hearthholm." + k).append(": " + opt(k, 0)), RW - 40);
                g.text(font, s, x0 + (RW - font.width(s)) / 2, oy + 4, TITLE, false);
                oy += 18;
            }
        }
        // summary of the drawn plan
        int sy = top + panelH - 21 - 24;
        g.text(font, fit(Component.translatable("gui.hearthholm.mine.sum_cells", info.cells(), info.blocks()), RW), x0, sy, TEXT, false);
        g.text(font, fit(Component.translatable("gui.hearthholm.mine.sum_time", info.blocks() == 0 ? "-" : timeText(info.seconds())), RW), x0, sy + 10, TEXT, false);
    }

    private void legendMiner(GuiGraphicsExtractor g) {
        int ly = map.y + ZoneMapWidget.VIEW + 3;
        int[] rgb = {ZoneMapWidget.zoneRgb(), ZoneMapWidget.oreRgb(), ZoneMapWidget.blockedRgb(), 0xFF4C9CFF, 0xFFF09A3C};
        String[] keys = {"plan", "ore", "locked", "above", "below"};
        int x = map.x;
        for (int i = 0; i < 5; i++) {
            g.fill(x, ly, x + 6, ly + 6, 0xFF000000);
            g.fill(x + 1, ly + 1, x + 5, ly + 5, rgb[i]);
            String s = fit(Component.translatable("gui.hearthholm.mine.legend." + keys[i]), 32);
            g.text(font, s, x + 8, ly - 1, DIM, false);
            x += 8 + 32;
        }
    }

    private boolean language(String key) {
        return net.minecraft.locale.Language.getInstance().has(key);
    }

    private void changeLayer(int d) { setLayer(layerY + d); }

    @Override
    public void tick() {
        super.tick();
        long now = minecraft == null || minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (confirmClear && now > confirmUntil) { confirmClear = false; rebuildWidgets(); }
        if (confirmEverything && now > confirmUntil) { confirmEverything = false; rebuildWidgets(); }
        if (spec.layered() && map != null && layerY != map.layer() && now - lastRefresh >= 11) {   // a throttled slider request is repeated
            lastRefresh = now;
            ClientPlayNetworking.send(new Payloads.ZoneMapRequest(view.pos(), layerY));
        }
        boolean soon = editDirty && now - lastEdit >= 12 && now - lastRefresh >= 10;
        if (map != null && !map.busy() && (now - lastRefresh >= 200 || soon)) {
            lastRefresh = now;
            editDirty = false;
            ClientPlayNetworking.send(new Payloads.ZoneMapRequest(view.pos(), layerY));
        }
    }

    @Override
    public void removed() {
        if (map != null) { map.flush(); map.close(); }
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (map != null && e.button() == 0 && sliderHit(e.x(), e.y())) { sliding = true; sliderTo(e.x()); return true; }
        if (map != null && map.mouseClicked(e.x(), e.y(), e.button())) return true;
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (sliding) { sliderTo(e.x()); return true; }
        if (map != null && map.mouseDragged(e.x(), e.y(), e.button(), dx, dy)) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (sliding) { sliding = false; return true; }
        if (map != null && map.mouseReleased(e.x(), e.y(), e.button())) return true;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (map != null && sliderHit(mx, my) && sy != 0) { changeLayer(sy > 0 ? 1 : -1); return true; }
        if (map != null && map.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
        // active tool and zoom marks go on top of the buttons
        ZoneMapWidget.Tool t = map.tool();
        for (int i = 0; i < toolButtons.size(); i++) {
            if (t.ordinal() == i) g.outline(toolButtons.get(i).getX() - 1, toolButtons.get(i).getY() - 1, toolButtons.get(i).getWidth() + 2, toolButtons.get(i).getHeight() + 2, 0xFFFFE08A);
        }
        for (int i = 0; i < 3; i++) {
            if (map.zoomLevel() == i && zoomButtons[i] != null) g.outline(zoomButtons[i].getX() - 1, zoomButtons[i].getY() - 1, zoomButtons[i].getWidth() + 2, zoomButtons[i].getHeight() + 2, 0xFFFFE08A);
        }
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        int x0 = left + RX;
        text(g, title, left + 10, top + 5, TITLE);
        WorkZone zone = map.zone();
        Component cells = Component.translatable("gui.hearthholm.zone.cells", zone.cellCount(map.layer()));
        String cs = fit(cells, 120);
        text(g, cs, left + panelW - 8 - font.width(cs), top + 5, DIM);
        map.render(g, mx, my);
        if (spec.layered()) {
            legendMiner(g);
            contentMiner(g, mx, my);
            fitText(g, Component.translatable("gui.hearthholm.worker").append(": ").append(view.worker()), x0, top + panelH - 21, RW, TEXT, mx, my);
            String inf;
            if (map.hoverX() >= 0 && map.hoverX() < zone.size() && map.hoverZ() >= 0 && map.hoverZ() < zone.size()) {
                inf = Component.translatable("gui.hearthholm.zone.cursor", zone.blockX(map.hoverX()), zone.blockZ(map.hoverZ()), map.hoverX(), map.hoverZ()).getString();
            } else inf = Component.translatable("gui.hearthholm.mine.hint").getString();
            g.text(font, fit(inf, RW), x0, top + panelH - 11, DIM, false);
            return;
        }
        if (zone.cellCount(map.layer()) == 0) {
            int bx = map.x, by = map.y + ZoneMapWidget.VIEW - 12;
            g.fill(bx, by, bx + ZoneMapWidget.VIEW, by + 12, 0xCC120D09);
            String s = fit(Component.translatable("gui.hearthholm.zone.empty", view.values().getOrDefault("radius", 0)), ZoneMapWidget.VIEW - 6);
            g.text(font, s, bx + 3, by + 2, WARN, false);
        }
        // brush size row (between the - and + buttons)
        int by = top + 16 + 62;
        String bs = fit(Component.translatable("gui.hearthholm.zone.brush_size", map.brush()), RW - 40);
        g.text(font, bs, x0 + (RW - font.width(bs)) / 2, by + 4, TEXT, false);
        // range option rows: label between the arrows
        int oy = top + 16 + 62 + 20 + 22;
        for (SiteOption o : type.options()) {
            if (o.kind() == SiteOption.Kind.RANGE) {
                int value = view.values().getOrDefault(o.key(), o.def());
                Component lab = language("gui.hearthholm.zone." + o.key()) ? Component.translatable("gui.hearthholm.zone." + o.key(), value)
                        : Component.empty().append(o.label()).append(": " + value);
                String s = fit(lab, RW - 40);
                g.text(font, s, x0 + (RW - font.width(s)) / 2, oy + 4, TITLE, false);
            }
            oy += 18;
        }
        if (spec.layered()) {
            String s = fit(Component.translatable("gui.hearthholm.zone.layer", layerY), RW - 40);
            g.text(font, s, x0 + (RW - font.width(s)) / 2, oy + 4, TITLE, false);
            oy += 18;
        }
        // legend
        int ly = oy + 2;
        legend(g, x0, ly, ZoneMapWidget.zoneRgb(), "gui.hearthholm.zone.legend.zone");
        legend(g, x0 + 90, ly, ZoneMapWidget.trunkRgb(), "gui.hearthholm.zone.legend.tree");
        legend(g, x0, ly + 10, ZoneMapWidget.legendColor(0), "gui.hearthholm.zone.legend.site");
        legend(g, x0 + 90, ly + 10, ZoneMapWidget.legendColor(1), "gui.hearthholm.zone.legend.chest");
        if (ly + 30 <= top + panelH - 22) legend(g, x0, ly + 20, ZoneMapWidget.legendColor(2), "gui.hearthholm.zone.legend.warehouse");

        // worker and cursor
        fitText(g, Component.translatable("gui.hearthholm.worker").append(": ").append(view.worker()), x0, top + panelH - 21, RW, TEXT, mx, my);
        String info;
        if (map.hoverX() >= 0 && map.hoverX() < zone.size() && map.hoverZ() >= 0 && map.hoverZ() < zone.size()) {
            info = Component.translatable("gui.hearthholm.zone.cursor", zone.blockX(map.hoverX()), zone.blockZ(map.hoverZ()), map.hoverX(), map.hoverZ()).getString();
        } else info = Component.translatable("gui.hearthholm.zone.hint").getString();
        g.text(font, fit(info, RW), x0, top + panelH - 11, DIM, false);
    }

    private void legend(GuiGraphicsExtractor g, int x, int y, int rgb, String key) {
        g.fill(x, y, x + 7, y + 7, 0xFF000000);
        g.fill(x + 1, y + 1, x + 6, y + 6, rgb);
        g.text(font, fit(Component.translatable(key), 78), x + 10, y, TEXT, false);
    }
}
