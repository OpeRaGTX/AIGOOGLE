package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.core.Stat;
import io.github.operagtx.hearthholm.core.Trait;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The town hall: overview with next steps, residents, hiring and buildings. */
public final class TownHallScreen extends HhScreen {
    private static final int OVERVIEW = 0, PEOPLE = 1, HIRE = 2, BUILDINGS = 3, DEFENSE = 4, OWNERS = 5;
    private static int lastTab = OVERVIEW;
    private Views.Settlement view;
    private int tab = lastTab;
    private int scroll;
    private EditBox nameBox;
    private Payloads.OwnersView owners;   // wave K1: last "Owners" answer of the server

    public TownHallScreen(Views.Settlement view) {
        super(Component.translatable("gui.hearthholm.town_hall"), 340, 228);
        this.view = view;
    }

    public static void show(Views.Settlement view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TownHallScreen s && s.view.desk().equals(view.desk())) {
            s.view = view;
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new TownHallScreen(view));
        }
    }

    /** Wave K1: the server answered the Owners tab (or a change of it). */
    public static void showOwners(Payloads.OwnersView v) {
        if (Minecraft.getInstance().gui.screen() instanceof TownHallScreen s && s.view.desk().equals(v.desk())) {
            s.owners = v;
            s.rebuildWidgets();
        }
    }

    private void send(int action, int arg, String text) {
        ClientPlayNetworking.send(new Payloads.TownHallAction(view.desk(), action, arg, text));
    }

    @Override
    protected void init() {
        super.init();
        String[] tabs = {"overview", "people", "hire", "buildings", "defense", "owners"};
        for (int i = 0; i < tabs.length; i++) {
            int t = i;
            Button b = button(fitLabel(Component.translatable("gui.hearthholm.tab." + tabs[i]), 50), left + 8 + i * 54, top + 30, 53, 16, x -> {
                tab = t;
                lastTab = t;
                scroll = 0;
                if (t == OWNERS) send(Payloads.TownHallAction.OWNERS, 0, "");
                rebuildWidgets();
            });
            b.active = tab != i;
        }
        button(Component.literal("✎"), left + panelW - 26, top + 6, 18, 14, b -> {
            if (nameBox == null) {
                nameBox = new EditBox(font, left + 8, top + 6, 180, 14, Component.empty());
                nameBox.setMaxLength(32);
                nameBox.setValue(view.name().getString());
                addRenderableWidget(nameBox);
                setFocused(nameBox);
            } else {
                send(Payloads.TownHallAction.RENAME, 0, nameBox.getValue());
                nameBox = null;
            }
        });
        int bottom = top + panelH - 24;
        switch (tab) {
            case OVERVIEW -> {
                button(Component.translatable("gui.hearthholm.rescan"), left + 8, bottom, 160, 18,
                        b -> send(Payloads.TownHallAction.RESCAN, 0, ""));
                Button mint = button(Component.translatable("gui.hearthholm.mint"), left + 172, bottom, panelW - 180, 18,
                        b -> send(Payloads.TownHallAction.MINT, 0, ""));
                mint.setTooltip(net.minecraft.client.gui.components.Tooltip.create(view.mintInfo()));
                mint.active = view.warehouse();
                Button fest = button(Component.translatable("gui.hearthholm.festival_start", view.festivalCost()),
                        left + 178, top + 177, panelW - 188, 14, b -> send(Payloads.TownHallAction.FESTIVAL, 0, ""));
                fest.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hearthholm.festival_tip")));
                fest.active = view.festivalDays() >= 0 && view.food() >= view.festivalCost() && view.residents() > 0;
            }
            case DEFENSE -> {
                boolean on = view.stats().getOrDefault("d.alarm", 0) == 1;
                Button alarm = button(Component.translatable(on ? "gui.hearthholm.alarm_off" : "gui.hearthholm.alarm_on"), left + 8, bottom, 160, 18,
                        b -> send(Payloads.TownHallAction.ALARM, 0, ""));
                alarm.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hearthholm.alarm_tip")));
            }
            case BUILDINGS -> button(Component.translatable("gui.hearthholm.rescan"), left + 8, bottom, 160, 18,
                    b -> send(Payloads.TownHallAction.RESCAN, 0, ""));
            case HIRE -> {
                for (int i = 0; i < view.candidates().size(); i++) {
                    int idx = i;
                    Button hire = button(Component.translatable("gui.hearthholm.hire"), left + 12 + i * 108, top + 50 + 128, 96, 14,
                            b -> send(Payloads.TownHallAction.HIRE, idx, ""));
                    hire.active = view.freeBeds() > 0 && view.residents() < view.cap();
                }
                button(Component.translatable("gui.hearthholm.reroll"), left + panelW - 112, top + 50, 104, 16,
                        b -> send(Payloads.TownHallAction.REROLL, 0, ""));
            }
            case OWNERS -> {
                if (owners == null) {
                    send(Payloads.TownHallAction.OWNERS, 0, "");
                    break;
                }
                int y = top + 76;
                for (int i = 0; i < owners.coOwners().size() && i < 3; i++, y += 16) {
                    Payloads.OwnerEntry e = owners.coOwners().get(i);
                    Button rm = button(Component.translatable("gui.hearthholm.owners.remove"), left + panelW - 78, y - 2, 68, 14,
                            b -> send(Payloads.TownHallAction.REMOVE_OWNER, 0, e.id().toString()));
                    rm.active = owners.founder();
                }
                y = top + 142;
                for (int i = 0; i < owners.online().size() && i < 3; i++, y += 16) {
                    Payloads.OwnerEntry e = owners.online().get(i);
                    button(Component.translatable("gui.hearthholm.owners.add"), left + panelW - 78, y - 2, 68, 14,
                            b -> send(Payloads.TownHallAction.ADD_OWNER, 0, e.id().toString()));
                }
            }
            default -> {}
        }
    }

    private void owners(GuiGraphicsExtractor g, int mx, int my) {
        if (owners == null) return;
        int x = left + 10;
        text(g, Component.translatable("gui.hearthholm.owners.founder", owners.founderName()), x, top + 50, GOOD);
        text(g, Component.translatable("gui.hearthholm.owners.co"), x, top + 62, TITLE);
        int y = top + 76;
        if (owners.coOwners().isEmpty()) text(g, Component.translatable("gui.hearthholm.owners.none"), x, y + 2, DIM);
        for (int i = 0; i < owners.coOwners().size() && i < 3; i++, y += 16) text(g, owners.coOwners().get(i).name(), x + 4, y + 2, TEXT);
        text(g, Component.translatable("gui.hearthholm.owners.online"), x, top + 128, TITLE);
        y = top + 142;
        if (owners.online().isEmpty()) text(g, Component.translatable("gui.hearthholm.owners.no_online"), x, y + 2, DIM);
        for (int i = 0; i < owners.online().size() && i < 3; i++, y += 16) text(g, owners.online().get(i).name(), x + 4, y + 2, TEXT);
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.owners.tip"), x, top + panelH - 40, panelW - 20, DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        if (nameBox == null) text(g, view.name(), left + 8, top + 9, TITLE);
        Component tier = Component.translatable("tier.hearthholm." + view.tier());
        g.text(font, tier, left + panelW - 32 - font.width(tier), top + 9, WARN, false);
        float frac = view.nextRenown() > 0 ? view.renown() / (float) view.nextRenown() : 1f;
        bar(g, left + 8, top + 20, panelW - 16, 6, frac, 0xFFC9A04A);
        Component renown = view.nextRenown() > 0
                ? Component.translatable("gui.hearthholm.renown_next", view.renown(), view.nextRenown(), Component.translatable("tier.hearthholm." + view.nextTier()))
                : Component.translatable("gui.hearthholm.renown", view.renown());
        if (inside(mx, my, left + 8, top + 18, panelW - 16, 9)) g.setTooltipForNextFrame(font, renown, mx, my);
        switch (tab) {
            case OVERVIEW -> overview(g);
            case PEOPLE -> people(g, mx, my);
            case HIRE -> hire(g, mx, my);
            case BUILDINGS -> buildings(g);
            case DEFENSE -> defense(g);
            case OWNERS -> owners(g, mx, my);
            default -> {}
        }
    }

    private void line(GuiGraphicsExtractor g, String key, Object value, int x, int y, int color) {
        text(g, Component.translatable("gui.hearthholm." + key).append(": "), x, y, DIM);
        g.text(font, Component.literal(String.valueOf(value)), x + 112, y, color, false);
    }

    private void overview(GuiGraphicsExtractor g) {
        int x = left + 10, y = top + 52;
        line(g, "residents", view.residents() + " / " + view.cap(), x, y, TEXT);
        line(g, "beds", view.freeBeds() + " / " + view.beds(), x, y += 11, view.freeBeds() > 0 ? GOOD : WARN);
        line(g, "food", view.food(), x, y += 11, view.food() >= view.hireCost() ? GOOD : WARN);
        line(g, "marks", view.marks(), x, y += 11, WARN);
        line(g, "hall", view.hall() ? "✔" : "✖", x, y += 11, view.hall() ? GOOD : BAD);
        line(g, "warehouse", view.warehouse() ? "✔" : "✖", x, y += 11, view.warehouse() ? GOOD : BAD);
        line(g, "radius", view.radius(), x, y += 11, TEXT);
        y += 13;
        text(g, Component.translatable("gui.hearthholm.chronicle"), x, y, TITLE);
        for (String k : new String[]{"mined", "logs", "crops", "fish", "placed", "crafted"}) {
            line(g, "stat." + k, view.stats().getOrDefault(k, 0), x, y += 10, TEXT);
        }
        int hx = left + 178, hy = top + 54, hw = panelW - 188;
        inset(g, hx - 4, hy - 4, hw + 8, 100);
        text(g, Component.translatable("gui.hearthholm.next_steps"), hx, hy, TITLE);
        hy += 14;
        if (view.hints().isEmpty()) text(g, Component.translatable("gui.hearthholm.all_good"), hx, hy, GOOD);
        for (Component hint : view.hints()) {
            g.text(font, "•", hx, hy, WARN, false);
            if (hy > top + 128) break;
            hy += g.textWithWordWrap(font, hint, hx + 8, hy, hw - 8, TEXT) + 4;
        }
        inset(g, hx - 4, top + 150, hw + 8, 46);
        text(g, Component.translatable("gui.hearthholm.festival"), hx, top + 154, TITLE);
        Component fs = view.festivalDays() < 0 ? Component.translatable("gui.hearthholm.festival_active")
                : view.festivalDays() == 0 ? Component.translatable("gui.hearthholm.festival_due", view.festivalCost())
                : view.festivalDays() == 1 ? Component.translatable("gui.hearthholm.festival_day1")
                : Component.translatable("gui.hearthholm.festival_days", view.festivalDays());
        g.text(font, fs, hx, top + 165, view.festivalDays() < 0 ? GOOD : TEXT, false);
    }

    private int stat(String k) {
        return view.stats().getOrDefault("d." + k, 0);
    }

    /** Wave C: raid log, rift status and the alarm. */
    private void defense(GuiGraphicsExtractor g) {
        int x = left + 10, y = top + 52;
        text(g, Component.translatable("gui.hearthholm.raids"), x, y, TITLE);
        int eta = stat("raid_eta");
        Component next = Component.translatable("gui.hearthholm.raid_eta." + eta);
        line(g, "raid_next", next.getString(), x, y += 12, eta == 3 ? BAD : eta == 1 ? WARN : eta == 2 ? GOOD : DIM);
        line(g, "raid_strength", stat("raid_strength"), x, y += 11, TEXT);
        if (eta == 3) line(g, "raid_alive", stat("raid_alive"), x, y += 11, BAD);
        int last = stat("raid_last");
        line(g, "raid_last", Component.translatable("gui.hearthholm.raid_result." + last).getString(), x, y += 11,
                last == 1 ? GOOD : last == 2 ? BAD : DIM);
        line(g, "raid_record", stat("raid_won") + " / " + stat("raid_lost"), x, y += 11, TEXT);
        line(g, "defenders", stat("defenders"), x, y += 11, stat("defenders") > 0 ? GOOD : WARN);
        y += 15;
        text(g, Component.translatable("gui.hearthholm.rift"), x, y, TITLE);
        Component rift = stat("rift_open") == 1 ? Component.translatable("gui.hearthholm.rift_open", stat("rift_hp"))
                : stat("rift_days") < 0 ? Component.translatable("gui.hearthholm.rift_none")
                : Component.translatable("gui.hearthholm.rift_days", stat("rift_days"));
        g.text(font, rift, x, y += 12, stat("rift_open") == 1 ? 0xFFC77DFF : TEXT, false);
        line(g, "rift_closed", stat("rift_closed"), x, y += 11, TEXT);

        int hx = left + 178, hy = top + 54, hw = panelW - 188;
        inset(g, hx - 4, hy - 4, hw + 8, 138);
        text(g, Component.translatable("gui.hearthholm.defense"), hx, hy, TITLE);
        boolean on = stat("alarm") == 1;
        g.text(font, Component.translatable(on ? "gui.hearthholm.alarm_state_on" : "gui.hearthholm.alarm_state_off"), hx, hy += 13, on ? BAD : GOOD, false);
        line2(g, "bells", stat("bells"), hx, hy += 12);
        line2(g, "gates", stat("gates"), hx, hy += 11);
        g.text(font, Component.translatable(stat("trophy") == 1 ? "gui.hearthholm.trophy_yes" : "gui.hearthholm.trophy_no"), hx, hy += 12,
                stat("trophy") == 1 ? 0xFFFFD35A : DIM, false);
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.defense_tip"), hx, hy + 14, hw, DIM);
    }

    private void line2(GuiGraphicsExtractor g, String key, Object value, int x, int y) {
        text(g, Component.translatable("gui.hearthholm." + key).append(": "), x, y, DIM);
        g.text(font, Component.literal(String.valueOf(value)), x + 100, y, TEXT, false);
    }

    private int rows() {
        return (panelH - 80) / 22;
    }

    private void people(GuiGraphicsExtractor g, int mx, int my) {
        List<Views.Resident> list = view.people();
        if (list.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.hearthholm.no_people"), left + 10, top + 56, panelW - 20, DIM);
            return;
        }
        int y = top + 52;
        for (int i = scroll; i < list.size() && i < scroll + rows(); i++, y += 22) {
            Views.Resident r = list.get(i);
            boolean hover = inside(mx, my, left + 8, y, panelW - 16, 20);
            g.fill(left + 8, y, left + panelW - 8, y + 20, hover ? 0xFF3A2D20 : BG2);
            face(g, r.profile().appearance(), r.profession(), left + 11, y + 2, 16);
            text(g, fit(r.profile().listName(), 92), left + 32, y + 2, TEXT);
            Profession p = Professions.get(r.profession());
            g.text(font, fit(p.label().copy().append(" " + "I".repeat(Math.max(1, r.level()))), 92), left + 32, y + 11, 0xFF000000 | p.color(), false);
            g.text(font, moodFace(r.mood()) + " " + r.mood(), left + 128, y + 6, moodColor(r.mood()), false);
            bar(g, left + 160, y + 8, 28, 5, r.food() / 20f, 0xFFD08A3A);
            bar(g, left + 192, y + 8, 28, 5, r.maxHp() <= 0 ? 0 : r.hp() / (float) r.maxHp(), 0xFFD4504A);
            if (inside(mx, my, left + 192, y + 6, 28, 9)) g.setTooltipForNextFrame(font, Component.literal("HP " + r.hp() + "/" + r.maxHp()), mx, my);
            if (!r.bed()) g.text(font, "⚑", left + 224, y + 6, BAD, false);
            String status = font.plainSubstrByWidth(r.status().getString(), panelW - 242);
            text(g, status, left + 234, y + 6, DIM);
            if (hover) g.setTooltipForNextFrame(font, r.status(), mx, my);
        }
        if (list.size() > rows()) {
            text(g, (scroll + 1) + "-" + Math.min(list.size(), scroll + rows()) + " / " + list.size(), left + panelW - 60, top + panelH - 18, DIM);
        }
    }

    private void hire(GuiGraphicsExtractor g, int mx, int my) {
        List<SettlerProfile> cands = view.candidates();
        for (int i = 0; i < cands.size(); i++) {
            SettlerProfile p = cands.get(i);
            int x = left + 8 + i * 108, y = top + 50;
            inset(g, x, y, 104, 146);
            if (p.legendary()) g.outline(x + 1, y + 1, 102, 144, 0xFFE8B84A);
            face(g, p.appearance(), Professions.UNEMPLOYED_ID, x + 40, y + 4, 24);
            Component name = p.legendary() ? p.listName() : p.name();
            g.text(font, name, x + 52 - font.width(name) / 2, y + 30, p.legendary() ? 0xFFFFD35A : TITLE, false);
            if (p.legendary()) {
                Component title = p.titleText().copy().withStyle(net.minecraft.ChatFormatting.GOLD);
                g.text(font, title, x + 52 - font.width(title) / 2, y + 39, 0xFFFFD35A, false);
                g.text(font, "★", x + 91, y + 4, 0xFFFFD35A, false);
                if (inside(mx, my, x + 2, y + 2, 100, 12) || inside(mx, my, x + 2, y + 28, 100, 20))
                    g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.legendary.tip"), mx, my);
            }
            g.text(font, p.female() ? "♀" : "♂", x + 4, y + 4, p.female() ? 0xFFE58FB4 : 0xFF7FA8E0, false);
            int sy = y + (p.legendary() ? 50 : 41);
            int step = p.legendary() ? 9 : 10;
            for (Stat st : Stat.values()) {
                Component lbl = Component.translatable("stat.hearthholm." + st.name().toLowerCase() + ".short");
                text(g, lbl, x + 4, sy, DIM);
                bar(g, x + 32, sy + 1, 50, 6, p.stat(st) / 10f, 0xFF8BB0D8);
                text(g, String.valueOf(p.stat(st)), x + 86, sy, TEXT);
                if (inside(mx, my, x + 2, sy - 1, 100, 10)) g.setTooltipForNextFrame(font, st.label(), mx, my);
                sy += step;
            }
            int tx = x + 4;
            for (Trait t : p.traitList()) {
                Component lbl = t.label();
                text(g, lbl, tx, sy + 1, WARN);
                if (inside(mx, my, tx, sy, font.width(lbl), 10)) g.setTooltipForNextFrame(font, t.description(), mx, my);
                tx += font.width(lbl) + 6;
            }
            sy += 13;
            for (Profession pr : bestFor(p, 2)) {
                text(g, pr.label(), x + 4, sy, 0xFF000000 | pr.color());
                text(g, stars(pr.aptitude(p)), x + 66, sy, WARN);
                sy += 10;
            }
        }
        int y = top + panelH - 30;
        text(g, Component.translatable("gui.hearthholm.hire_cost", view.hireCost(), view.food()), left + 8, y, view.food() >= view.hireCost() ? TEXT : WARN);
        Component req = view.residents() >= view.cap()
                ? Component.translatable("gui.hearthholm.cap_reached", view.cap()).withStyle(ChatFormatting.RED)
                : view.freeBeds() <= 0 ? Component.translatable("gui.hearthholm.need_bed").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.hearthholm.hire_ready", view.freeBeds());
        g.text(font, req, left + 8, y + 12, TEXT, false);
    }

    private void buildings(GuiGraphicsExtractor g) {
        int y = top + 52;
        if (view.buildings().isEmpty()) text(g, Component.translatable("gui.hearthholm.no_buildings"), left + 10, y + 4, DIM);
        for (int i = scroll; i < view.buildings().size() && i < scroll + rows() - 1; i++, y += 22) {
            Views.Building b = view.buildings().get(i);
            g.fill(left + 8, y, left + panelW - 8, y + 20, BG2);
            Component title = BuildingRecord.title(b.roles());
            text(g, font.plainSubstrByWidth(title.getString(), 124), left + 12, y + 2, TITLE);
            text(g, b.anchor().getX() + ", " + b.anchor().getY() + ", " + b.anchor().getZ(), left + 12, y + 11, DIM);
            Component info = Component.translatable("gui.hearthholm.building_info", b.beds(), b.containers(), b.comfort());
            text(g, font.plainSubstrByWidth(info.getString(), panelW - 152), left + 140, y + 2, TEXT);
            if (b.problem().isEmpty()) {
                Component ok = Component.translatable("gui.hearthholm.valid");
                if ((b.roles() & (io.github.operagtx.hearthholm.crafting.Workshop.MASONRY.role() | io.github.operagtx.hearthholm.crafting.Workshop.SMITHY.role())) != 0)
                    ok = ok.copy().append(" · ").append(Component.translatable(
                            (b.features() & BuildingRecord.FURNACE) != 0 ? "gui.hearthholm.furnace_yes" : "gui.hearthholm.furnace_no"));
                text(g, ok, left + 140, y + 11, GOOD);
            }
            else text(g, Component.translatable("problem.hearthholm." + b.problem()), left + 140, y + 11, BAD);
        }
        g.textWithWordWrap(font, Component.translatable("gui.hearthholm.room_rules"), left + 176, top + panelH - 30, panelW - 186, DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (tab == PEOPLE) {
            int y = top + 52;
            for (int i = scroll; i < view.people().size() && i < scroll + rows(); i++, y += 22) {
                if (inside(e.x(), e.y(), left + 8, y, panelW - 16, 20)) {
                    send(Payloads.TownHallAction.OPEN_RESIDENT, i, "");
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int size = tab == PEOPLE ? view.people().size() : tab == BUILDINGS ? view.buildings().size() : 0;
        scroll = Math.max(0, Math.min(Math.max(0, size - rows() + 1), scroll - (int) Math.signum(sy)));
        return true;
    }
}
