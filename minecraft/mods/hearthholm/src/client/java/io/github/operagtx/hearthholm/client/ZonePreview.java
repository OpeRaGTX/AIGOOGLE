package io.github.operagtx.hearthholm.client;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** While the player holds any Hearthholm item, work zones nearby are outlined with dust. */
public final class ZonePreview {
    private ZonePreview() {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || level.getGameTime() % 10 != 0) return;
        if (!holdsModItem(player.getMainHandItem()) && !holdsModItem(player.getOffhandItem())) return;
        int pcx = player.blockPosition().getX() >> 4, pcz = player.blockPosition().getZ() >> 4;
        for (int cx = pcx - 2; cx <= pcx + 2; cx++)
            for (int cz = pcz - 2; cz <= pcz + 2; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof WorkSiteBlockEntity w)) continue;
                    AABB box;
                    try {
                        box = w.type().preview().apply(w);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (box != null) outline(level, box, w.type().color());
                }
            }
    }

    private static boolean holdsModItem(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Hearthholm.MOD_ID);
    }

    private static void outline(ClientLevel level, AABB b, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.2f);
        double step = Math.max(1.0, (b.getXsize() + b.getYsize() + b.getZsize()) / 60.0);
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) for (double x = b.minX; x <= b.maxX; x += step) level.addParticle(dust, x, y, z, 0, 0, 0);
        for (double x : xs) for (double z : zs) for (double y = b.minY; y <= b.maxY; y += step) level.addParticle(dust, x, y, z, 0, 0, 0);
        for (double x : xs) for (double y : ys) for (double z = b.minZ; z <= b.maxZ; z += step) level.addParticle(dust, x, y, z, 0, 0, 0);
    }
}
