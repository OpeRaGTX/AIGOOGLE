package io.github.operagtx.hearthholm.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.party.Party;
import io.github.operagtx.hearthholm.party.PartyRoster;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.github.operagtx.hearthholm.test.HearthholmClientTest.*;

/** Wave G: retroactive guide book, balance config, party members in unloaded chunks and across dimensions, reshot screenshots. */
final class WaveGTest {
    private static UUID far;
    private static int farCx, farCz;
    private static BlockPos farPos;
    private static int forcedBefore;

    private WaveGTest() {}

    private static int books(ServerPlayer p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.FOUNDERS_CHRONICLE)) n += inv.getItem(i).getCount();
        return n;
    }

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - (y + 1.62);
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, false);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("time set 6000");

        // ---- 1. guide book for an old save: flag false + owner opens the town hall -> the book arrives once
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            Settlement st = town(s);
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (p.getInventory().getItem(i).is(ModItems.FOUNDERS_CHRONICLE)) p.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            st.civic.guideGiven = false;   // what an old save loads as
            SettlementService.openTownHall(p, DESK);
            int first = books(p);
            SettlementService.openTownHall(p, DESK);
            log("GAPG guide retroactive: books after 1st desk visit=" + first + ", after 2nd=" + books(p) + ", flag=" + st.civic.guideGiven);
            if (first != 1 || books(p) != 1 || !st.civic.guideGiven) throw new AssertionError("retroactive guide book");
        });
        ctx.waitTicks(5);
        ctx.setScreen(() -> null);

        // ---- 2. balance config file
        sp.getServer().runOnServer(s -> {
            JsonObject before = Balance.toJson();
            int hire = Balance.HIRE_BASE;
            int[] pop = Balance.TIER_POP.clone();
            JsonObject o = new JsonObject();
            o.addProperty("HIRE_BASE", hire + 7);
            JsonArray bad = new JsonArray();
            bad.add(1);
            o.add("TIER_POP", bad);           // wrong length: ignored
            o.addProperty("UNKNOWN_KEY", 5);  // unknown: ignored
            int changed = Balance.apply(o);
            boolean ok = changed == 1 && Balance.HIRE_BASE == hire + 7 && java.util.Arrays.equals(pop, Balance.TIER_POP);
            JsonObject arr = new JsonObject();
            JsonArray a = new JsonArray();
            for (int v : pop) a.add(v + 1);
            arr.add("TIER_POP", a);
            ok &= Balance.apply(arr) == 1 && Balance.TIER_POP[0] == pop[0] + 1;
            Balance.apply(before);
            boolean restored = Balance.HIRE_BASE == hire && java.util.Arrays.equals(pop, Balance.TIER_POP);
            java.nio.file.Path f = FabricLoader.getInstance().getConfigDir().resolve("hearthholm.json");
            boolean file = false;
            try {
                file = Files.exists(f) && JsonParser.parseString(Files.readString(f)).getAsJsonObject().has("HIRE_BASE");
            } catch (Exception e) {
                log("GAPG balance file error " + e);
            }
            log("GAPG balance config: file=" + f.getFileName() + " exists+keys=" + file + " overrideApplied=" + ok + " restored=" + restored + " keys=" + before.size());
            if (!ok || !restored || !file) throw new AssertionError("balance config");
        });

        // ---- 3. a party member left in an unloaded chunk is force-loaded and brought to the leader
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            p.teleportTo(l, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 2.5, Set.of(), 0f, 0f, false);
            for (SettlerEntity e : Party.members(s, p.getUUID())) e.leaveParty();
            int called = Party.call(l, p);
            List<SettlerEntity> ms = Party.members(s, p.getUUID());
            for (SettlerEntity e : settlers(s)) {
                if (ms.size() >= 2) break;
                if (!e.inParty() && e.isAlive()) { e.joinParty(p.getUUID()); ms = Party.members(s, p.getUUID()); }
            }
            SettlerEntity m = ms.getFirst();
            far = m.getUUID();
            int fx = DESK.getX() + 600, fz = DESK.getZ();
            farCx = fx >> 4;
            farCz = fz >> 4;
            l.setChunkForced(farCx, farCz, true);
            l.getChunk(farCx, farCz);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, fx, fz);
            farPos = new BlockPos(fx, y, fz);
            m.setNoAi(true);   // test only: stops the party activity from walking it home while it is loaded
            m.teleportTo(fx + 0.5, y, fz + 0.5);
            Party.remember(m);
            forcedBefore = Party.forcedLoads;
            log("GAPG party: horn called=" + called + " members=" + ms.size() + ", " + m.getName().getString() + " parked at " + farPos.toShortString() + " (600 blocks away)");
        });
        ctx.waitTicks(50);
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity m = (SettlerEntity) l.getEntity(far);
            PartyRoster.Entry re = PartyRoster.get(s).get(far);
            log("GAPG party: member still parked=" + (m != null && m.blockPosition().distSqr(farPos) < 100) + " roster=" + (re == null ? "none" : re.pos.toShortString()));
            if (m == null || m.blockPosition().distSqr(farPos) > 100 || re == null) throw new AssertionError("parking failed");
            l.setChunkForced(farCx, farCz, false);   // release: the chunk unloads, the member goes with it
        });
        boolean[] unloaded = {false};
        for (int i = 0; i < 12 && !unloaded[0]; i++) {
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> unloaded[0] = s.overworld().getEntity(far) == null && Party.forcedLoads == forcedBefore);
        }
        // the scan runs every 5 s: force-load, find, teleport, release
        boolean[] back = {false};
        for (int i = 0; i < 20 && !back[0]; i++) {
            ctx.waitTicks(20);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getEntity(far) instanceof SettlerEntity m) back[0] = m.distanceToSqr(player(s)) < 12 * 12;
            });
        }
        boolean wasUnloaded = unloaded[0];
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            SettlerEntity m = (SettlerEntity) l.getEntity(far);
            if (m != null) m.setNoAi(false);
            boolean stillForced = l.getForceLoadedChunks().contains(ChunkPos.pack(farCx, farCz));
            int loads = Party.forcedLoads - forcedBefore;
            log("GAPG party: member was unloaded=" + wasUnloaded + " rescued to leader=" + back[0] + " forcedLoads=" + loads + " rescuedTotal=" + Party.rescued + " chunkStillForced=" + stillForced);
            if (!wasUnloaded) throw new AssertionError("member never unloaded (test setup)");
            if (!back[0] || stillForced || loads < 1 || loads > 3) throw new AssertionError("unloaded party member not recovered / forced chunk leaked");
        });

        // ---- 4. the party follows the leader through a dimension change and back
        BlockPos[] nether = new BlockPos[1];
        sp.getServer().runOnServer(s -> {
            ServerLevel nl = s.getLevel(Level.NETHER);
            ServerPlayer p = player(s);
            if (nl == null) throw new AssertionError("no nether level in the test world");
            BlockPos at = new BlockPos(40, 100, 40);
            nl.getChunkAt(at);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                nl.setBlock(at.offset(x, -1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                for (int y = 0; y < 4; y++) nl.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            nether[0] = at;
            p.teleportTo(nl, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0f, 0f, false);
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            List<SettlerEntity> ms = Party.members(s, p.getUUID());
            int in = 0;
            for (SettlerEntity e : ms) if (e.level().dimension().equals(Level.NETHER) && e.distanceToSqr(p) < 16 * 16) in++;
            log("GAPG dimension: leader in " + p.level().dimension().identifier() + ", members=" + ms.size() + " followed into the nether=" + in);
            if (ms.isEmpty() || in != ms.size()) throw new AssertionError("party did not follow into the nether");
            ServerLevel o = s.overworld();
            p.teleportTo(o, DESK.getX() + 0.5, DESK.getY(), DESK.getZ() - 2.5, Set.of(), 0f, 0f, false);
        });
        ctx.waitTicks(80);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            List<SettlerEntity> ms = Party.members(s, p.getUUID());
            int in = 0;
            for (SettlerEntity e : ms) if (e.level().dimension().equals(Level.OVERWORLD) && e.distanceToSqr(p) < 16 * 16) in++;
            log("GAPG dimension: back in the overworld, followed=" + in + "/" + ms.size() + " roster=" + PartyRoster.get(s).all().size());
            if (ms.isEmpty() || in != ms.size()) throw new AssertionError("party did not follow back");
            // the horn with everybody close dismisses the party
            Party.toggle(p.level(), p, true);
            log("GAPG horn dismiss: members left=" + Party.members(s, p.getUUID()).size() + " roster=" + PartyRoster.get(s).all().size());
            if (!Party.members(s, p.getUUID()).isEmpty()) throw new AssertionError("dismiss failed");
        });

        // ---- 5. reshoot: elevated daytime overview of the whole settlement, no GUI
        sp.getServer().runCommand("time set 5000");
        sp.getServer().runOnServer(s -> {
            ServerLevel l = s.overworld();
            ServerPlayer p = player(s);
            List<SettlerEntity> ss = settlers(s);
            for (int i = 0; i < ss.size(); i++) {
                int x = O.getX() + 3 + (i * 2) % 14, z = O.getZ() - 2 + (i % 3) * 2;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                ss.get(i).teleportTo(x + 0.5, y, z + 0.5);
            }
            look(p, O.getX() + 8.5, O.getY() + 30, O.getZ() - 22, O.getX() + 8.5, O.getY(), O.getZ() + 4);
            p.getInventory().setSelectedSlot(8);
        });
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        ctx.waitTicks(10);
        sp.getConnection().waitForChunksRender();
        ctx.takeScreenshot("hh_g1_town_overview");
        ctx.getInput().pressKey(o -> o.keyToggleGui);
        log("GAPG screenshots: hh_g1_town_overview taken, GUI hidden");
        log("WAVE G OK");
    }
}
