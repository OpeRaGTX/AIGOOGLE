package io.github.operagtx.hearthholm.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.settlement.Appearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds each settler's 64x64 skin at runtime from grayscale layers (skin, eyes, hair, beard,
 * clothes) tinted with palette colours, plus an untinted profession outfit on top. Thousands
 * of combinations from a handful of small PNGs; results are cached per look + profession.
 */
public final class SettlerTextures {
    public static final int[] SKIN = {0xF3D2B6, 0xE6B48F, 0xCF9868, 0xAD774B, 0x86573A, 0x5B3A26};
    public static final int[] HAIR = {0x2A211E, 0x4A3226, 0x6E4A2F, 0x8C4527, 0xB8622F, 0xD8B467, 0xC6B79B, 0xA9A9A9};
    public static final int[] EYES = {0x6B4423, 0x3E7CC2, 0x4F8F3A, 0x7D8A93, 0x8F7A3A};
    public static final int[] SHIRT = {0xE5D8BC, 0xA33B32, 0x3D5D99, 0x4F7A3C, 0x7A5235, 0xC2973A, 0x3C8A86, 0x6E4C8A, 0x7C7C7C, 0xEDEDE6};
    public static final int[] PANTS = {0x5A4030, 0x3A3A40, 0x5E6238, 0x2E3B5C, 0xA08860, 0x6A6A6A};

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<String, Optional<NativeImage>> LAYERS = new HashMap<>();

    private SettlerTextures() {}

    public static Identifier get(int lookBits, Identifier profession) {
        String key = lookBits + "_" + profession.getNamespace() + "_" + profession.getPath();
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        Identifier id = Hearthholm.id("settler_skin/" + Integer.toHexString(lookBits) + "_" + profession.getPath());
        NativeImage img = compose(Appearance.unpack(lookBits), profession);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, img));
        CACHE.put(key, id);
        return id;
    }

    /** Wave L1: the promotions without art of their own wear the outfit of the rank they come from (until they get one). */
    private static String skinOf(Identifier profession) {
        return switch (profession.getPath()) {
            case "crossbowman" -> "archer";
            case "cavalryman" -> "warrior";
            default -> profession.getPath();
        };
    }

    /** Drop cached skins (resource reload). */
    public static void clear() {
        for (Identifier id : CACHE.values()) Minecraft.getInstance().getTextureManager().release(id);
        CACHE.clear();
        LAYERS.values().forEach(o -> o.ifPresent(NativeImage::close));
        LAYERS.clear();
    }

    /** The composed 64x64 skin (also used by the tests to compare layers). */
    public static NativeImage compose(Appearance a, Identifier profession) {
        String arms = a.female() ? "slim" : "wide";
        NativeImage out = new NativeImage(64, 64, true);
        layer(out, "skin_" + arms, SKIN[a.skin() % SKIN.length]);
        layer(out, "eyes_white", -1);
        layer(out, "eyes_iris", EYES[a.eyes() % EYES.length]);
        layer(out, "pants", PANTS[a.pants() % PANTS.length]);
        layer(out, "shirt_" + arms, SHIRT[a.shirt() % SHIRT.length]);
        if (a.outfit() > 0) {   // wave H2: dresses, skirts, pinafores, vests, tunics, robes (men's vests are tinted like trousers)
            int tint = a.outfit() == 4 ? PANTS[a.pants() % PANTS.length] : SHIRT[a.shirt() % SHIRT.length];
            layer(out, "outfit/o" + a.outfit(), tint);
            layer(out, "outfit/o" + a.outfit() + "_acc", -1);
        }
        layer(out, "shoes", -1);
        int hair = HAIR[a.hairColor() % HAIR.length];
        layer(out, "brows", hair);
        layer(out, a.female() ? "face_f" : "face_m", -1);   // wave H3: lashes, lips and blush / a squarer jaw
        if (a.elder()) layer(out, "elder", -1);
        if (!a.female() && a.beard() > 0) layer(out, "beard/b" + a.beard(), hair);
        if (a.female()) {
            layer(out, "hair/f" + a.hairStyle(), hair);
            layer(out, "hair/f" + a.hairStyle() + "_acc", -1);   // ribbons and hair bands
        }
        else if (a.hairStyle() > 0) layer(out, "hair/m" + a.hairStyle(), hair);
        if (a.headwear() > 0) layer(out, "head/h" + a.headwear(), SHIRT[(a.shirt() + 3) % SHIRT.length]);
        layer(out, "profession/" + skinOf(profession) + "_" + arms, -1);
        return out;
    }

    /** Alpha-composites a layer; grayscale layers are multiplied by {@code tint} (-1 = as is). */
    private static void layer(NativeImage out, String name, int tint) {
        NativeImage src = load(name);
        if (src == null) return;
        int tr = tint >> 16 & 255, tg = tint >> 8 & 255, tb = tint & 255;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int c = src.getPixel(x, y);
                int alpha = c >>> 24;
                if (alpha == 0) continue;
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                if (tint != -1) {
                    r = r * tr / 255;
                    g = g * tg / 255;
                    b = b * tb / 255;
                }
                if (alpha < 255) {
                    int d = out.getPixel(x, y);
                    int da = d >>> 24;
                    float k = alpha / 255f;
                    r = (int) (r * k + (d >> 16 & 255) * (1 - k));
                    g = (int) (g * k + (d >> 8 & 255) * (1 - k));
                    b = (int) (b * k + (d & 255) * (1 - k));
                    alpha = Math.max(alpha, da);
                }
                out.setPixel(x, y, alpha << 24 | r << 16 | g << 8 | b);
            }
    }

    private static @Nullable NativeImage load(String name) {
        return LAYERS.computeIfAbsent(name, n -> {
            Identifier id = Hearthholm.id("textures/entity/settler/" + n + ".png");
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return Optional.empty();
            try (InputStream in = res.get().open()) {
                return Optional.of(NativeImage.read(in));
            } catch (Exception e) {
                Hearthholm.LOG.warn("Cannot read settler layer {}", id, e);
                return Optional.empty();
            }
        }).orElse(null);
    }
}
