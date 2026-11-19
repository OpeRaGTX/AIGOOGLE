package io.github.operagtx.hearthholm.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Wave F: the Founder's Chronicle book. Nine chapters (list on the left), two pages each; arrows flip through the whole
 * book. Text lives in the language files as {@code guide.hearthholm.chN.title / p1 / p2}.
 */
public final class ChronicleScreen extends HhScreen {
    /** Nine text chapters (two pages each) and, wave K1, chapter 10 with the picture pages of every mod recipe. */
    public static final int CHAPTERS = 9, PAGES_PER_CHAPTER = 2, LIST_W = 112, PANEL_W = 360, RECIPE_CHAPTER = 10, PER_PAGE = 4;
    /** One recipe picture: grid of ingredient ids ("ns:item" or "#ns:tag", null = empty). */
    public record Entry(String id, int count, String kind, int w, int h, List<String> grid) {}
    private static List<Entry> recipes;
    private static int lastPage;
    private int page;

    /** Recipes of the mod, read from {@code assets/hearthholm/recipe_book.json} (generated next to the data recipes). */
    public static List<Entry> recipes() {
        if (recipes != null) return recipes;
        List<Entry> out = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(Hearthholm.id("recipe_book.json"));
            if (res.isPresent()) try (Reader r = res.get().openAsReader()) {
                JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    List<String> grid = new ArrayList<>();
                    for (JsonElement g : o.getAsJsonArray("grid")) grid.add(g.isJsonNull() ? null : g.getAsString());
                    out.add(new Entry(o.get("id").getAsString(), o.get("count").getAsInt(), o.get("kind").getAsString(),
                            o.get("w").getAsInt(), o.get("h").getAsInt(), grid));
                }
            }
        } catch (Exception e) {
            Hearthholm.LOG.warn("Hearthholm: recipe book list unreadable", e);
        }
        if (!out.isEmpty()) recipes = out;
        return out;
    }

    public static int recipePages() { return Math.max(1, (recipes().size() + PER_PAGE - 1) / PER_PAGE); }

    public static int totalPages() { return CHAPTERS * PAGES_PER_CHAPTER + recipePages(); }

    /** Chapter number (1-based) of a page. */
    public static int chapterOf(int page) { return page >= CHAPTERS * PAGES_PER_CHAPTER ? RECIPE_CHAPTER : page / PAGES_PER_CHAPTER + 1; }

    /** First page of the recipe chapter. */
    public static int recipeStart() { return CHAPTERS * PAGES_PER_CHAPTER; }

    public ChronicleScreen(int page) {
        super(Component.translatable("gui.hearthholm.guide"), PANEL_W, 212);
        this.page = Math.max(0, Math.min(totalPages() - 1, page));
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new ChronicleScreen(lastPage));
    }

    public static void open(int chapter, int pageInChapter) {
        lastPage = chapter * PAGES_PER_CHAPTER + pageInChapter;
        open();
    }

    public int page() { return page; }

    private void go(int p) {
        page = Math.max(0, Math.min(totalPages() - 1, p));
        lastPage = page;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int chapter = chapterOf(page) - 1;
        for (int i = 0; i < RECIPE_CHAPTER; i++) {
            int target = i * PAGES_PER_CHAPTER;
            var b = button(Component.literal((i + 1) + ". ").append(Component.translatable("guide.hearthholm.ch" + (i + 1) + ".title")),
                    left + 8, top + 26 + i * 17, LIST_W, 15, x -> go(target));
            b.active = i != chapter;
        }
        int ny = top + panelH - 24;
        var prev = button(Component.literal("<"), left + LIST_W + 44, ny, 24, 16, b -> go(page - 1));
        var next = button(Component.literal(">"), left + panelW - 32, ny, 24, 16, b -> go(page + 1));
        prev.active = page > 0;
        next.active = page < totalPages() - 1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        text(g, Component.translatable("gui.hearthholm.guide"), left + 8, top + 8, TITLE);
        int chapter = chapterOf(page), sub = page % PAGES_PER_CHAPTER + 1;
        int tx = left + LIST_W + 20, tw = panelW - LIST_W - 30;
        inset(g, tx - 6, top + 22, tw + 12, panelH - 52);
        g.fill(left + LIST_W + 14, top + 8, left + LIST_W + 15, top + panelH - 8, TRIM_DARK);
        Component title = Component.translatable("guide.hearthholm.ch" + chapter + ".title");
        text(g, title, tx, top + 28, TITLE);
        g.fill(tx, top + 39, tx + tw, top + 40, TRIM_DARK);
        if (chapter == RECIPE_CHAPTER) recipePage(g, tx, tw, mx, my);
        else g.textWithWordWrap(font, Component.translatable("guide.hearthholm.ch" + chapter + ".p" + sub), tx, top + 45, tw, TEXT);
        Component pageNo = Component.translatable("gui.hearthholm.guide_page", page + 1, totalPages());
        text(g, pageNo, left + LIST_W + 20 + (tw - font.width(pageNo)) / 2 + 2, top + panelH - 20, DIM);
    }

    /** Wave K1: four recipes per page, each as its crafting grid, an arrow and the result; tags cycle through their items. */
    private void recipePage(GuiGraphicsExtractor g, int tx, int tw, int mx, int my) {
        List<Entry> all = recipes();
        int first = (page - recipeStart()) * PER_PAGE;
        int colW = tw / 2;
        for (int k = 0; k < PER_PAGE && first + k < all.size(); k++) {
            Entry e = all.get(first + k);
            int ox = tx + (k % 2) * colW, oy = top + 44 + (k / 2) * 66;
            int gx = ox, gy = oy;
            for (int r = 0; r < e.h(); r++)
                for (int c = 0; c < e.w(); c++) {
                    int sx = gx + c * 17, sy = gy + r * 17 + (e.h() < 3 ? 0 : 0);
                    g.fill(sx, sy, sx + 17, sy + 17, 0xFF3A2D20);
                    g.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0xFF5C4A36);
                    int idx = r * e.w() + c;
                    String ing = idx < e.grid().size() ? e.grid().get(idx) : null;
                    if (ing == null) continue;
                    ItemStack st = stackFor(ing);
                    if (st.isEmpty()) continue;
                    g.item(st, sx + 1, sy + 1);
                    if (inside(mx, my, sx, sy, 17, 17)) g.setTooltipForNextFrame(font, ing.startsWith("#") ? Component.literal("#" + ing.substring(ing.indexOf(':') + 1)) : st.getHoverName(), mx, my);
                }
            int ax = ox + 3 * 17 + 4, ay = oy + 17 + 4;
            text(g, Component.literal("→"), ax, ay, TITLE);
            Item result = BuiltInRegistries.ITEM.getValue(Hearthholm.id(e.id()));
            ItemStack out = new ItemStack(result, e.count());
            int rx = ax + 14;
            g.fill(rx - 1, oy + 17 - 1, rx + 18, oy + 17 + 18, 0xFF3A2D20);
            g.item(out, rx, oy + 17);
            g.itemDecorations(font, out, rx, oy + 17);
            if (inside(mx, my, rx, oy + 17, 16, 16)) g.setTooltipForNextFrame(font, out.getHoverName(), mx, my);
            String name = fit(out.getHoverName().getString(), colW - 6);
            g.text(font, name, ox, oy + 53, TEXT, false);
        }
    }

    private static ItemStack stackFor(String ing) {
        if (ing.startsWith("#")) {
            Identifier id = Identifier.tryParse(ing.substring(1));
            if (id == null) return ItemStack.EMPTY;
            List<Item> items = new ArrayList<>();
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) items.add(h.value());
            if (items.isEmpty()) return ItemStack.EMPTY;
            return new ItemStack(items.get((int) ((System.currentTimeMillis() / 1000) % items.size())));
        }
        Identifier id = Identifier.tryParse(ing);
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }
}
