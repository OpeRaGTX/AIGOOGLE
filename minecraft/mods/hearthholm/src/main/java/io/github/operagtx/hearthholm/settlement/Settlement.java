package io.github.operagtx.hearthholm.settlement;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.operagtx.hearthholm.block.WarehouseLedgerBlock;
import io.github.operagtx.hearthholm.block.WorkshopBlock;
import io.github.operagtx.hearthholm.crafting.Workshop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** One player settlement. Mutable; persisted by {@link SettlementManager}. */
public final class Settlement {
    public record SiteRecord(BlockPos pos, Identifier type) {
        public static final Codec<SiteRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(SiteRecord::pos),
                Identifier.CODEC.fieldOf("type").forGetter(SiteRecord::type)
        ).apply(i, SiteRecord::new));
    }

    /** "Keep {@code amount} of {@code id} in the warehouse" (order board). Ids keep unknown items harmless. */
    public record Order(Identifier id, int amount) {
        public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("item").forGetter(Order::id),
                Codec.INT.fieldOf("amount").forGetter(Order::amount)
        ).apply(i, Order::new));

        public Order(Item item, int amount) {
            this(BuiltInRegistries.ITEM.getKey(item), amount);
        }

        public Item item() {
            return BuiltInRegistries.ITEM.getValue(id);
        }
    }

    /** Fields added after the first release (kept in a second map codec: a record codec holds 16 fields). */
    private record Extra(List<BlockPos> workshops, List<Order> orders, int marksMinted) {
        static final MapCodec<Extra> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.listOf().optionalFieldOf("workshops", List.of()).forGetter(Extra::workshops),
                Order.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(Extra::orders),
                Codec.INT.optionalFieldOf("marks_minted", 0).forGetter(Extra::marksMinted)
        ).apply(i, Extra::new));
    }

    private static final MapCodec<Settlement> BASE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(s -> s.id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(s -> s.name),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(s -> s.owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(s -> s.ownerName),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(s -> s.dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
            Codec.BOOL.optionalFieldOf("desk_present", true).forGetter(s -> s.deskPresent),
            Codec.INT.optionalFieldOf("renown", 0).forGetter(s -> s.renown),
            ResidentRecord.CODEC.listOf().optionalFieldOf("residents", List.of()).forGetter(s -> s.residents),
            BuildingRecord.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(s -> s.buildings),
            BlockPos.CODEC.listOf().optionalFieldOf("ledgers", List.of()).forGetter(s -> s.ledgers),
            SiteRecord.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(s -> s.sites),
            SettlerProfile.CODEC.listOf().optionalFieldOf("candidates", List.of()).forGetter(s -> s.candidates),
            Codec.LONG.optionalFieldOf("candidates_day", -1L).forGetter(s -> s.candidatesDay),
            Codec.LONG.listOf().optionalFieldOf("awarded", List.of()).forGetter(s -> List.copyOf(s.awarded)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(s -> s.stats)
    ).apply(i, (id, name, owner, ownerName, dim, center, desk, renown, residents, buildings, ledgers, sites,
                candidates, cday, awarded, stats) -> {
        Settlement s = new Settlement(id, owner, dim, center);
        s.name = name;
        s.ownerName = ownerName;
        s.deskPresent = desk;
        s.renown = renown;
        s.residents.addAll(residents);
        s.buildings.addAll(buildings);
        s.ledgers.addAll(ledgers);
        s.sites.addAll(sites);
        s.candidates.addAll(candidates);
        s.candidatesDay = cday;
        s.awarded.addAll(awarded);
        s.stats.putAll(stats);
        return s;
    }));

    public static final Codec<Settlement> CODEC = Codec.mapPair(BASE_CODEC, Extra.CODEC).xmap(p -> {
        Settlement s = p.getFirst();
        s.workshops.addAll(p.getSecond().workshops());
        s.orders.addAll(p.getSecond().orders());
        s.marksMinted = p.getSecond().marksMinted();
        return s;
    }, s -> Pair.of(s, new Extra(List.copyOf(s.workshops), List.copyOf(s.orders), s.marksMinted))).codec();

    public final UUID id;
    public final UUID owner;
    public final ResourceKey<Level> dimension;
    public String name = "";
    public String ownerName = "";
    public BlockPos center;
    public boolean deskPresent = true;
    public int renown;
    public final List<ResidentRecord> residents = new ArrayList<>();
    public final List<BuildingRecord> buildings = new ArrayList<>();
    public final List<BlockPos> ledgers = new ArrayList<>();
    public final List<SiteRecord> sites = new ArrayList<>();
    public final List<SettlerProfile> candidates = new ArrayList<>();
    public long candidatesDay = -1;
    public final Set<Long> awarded = new HashSet<>();
    public final Map<String, Integer> stats = new HashMap<>();
    public final List<BlockPos> workshops = new ArrayList<>();
    public final List<Order> orders = new ArrayList<>();
    public int marksMinted;
    /** Not saved: which crafter is making which item right now (order board status). */
    public final Map<Item, String> crafting = new HashMap<>();
    /** Not saved: game time of the last room scan. */
    public long lastScan = -100000;

    public Settlement(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos center) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.center = center;
    }

    public Component displayName() {
        return name.isBlank() ? Component.translatable("settlement.hearthholm.default_name") : Component.literal(name);
    }

    // ---- growth ---------------------------------------------------------------------------

    public SettlementTier tier() {
        SettlementTier best = SettlementTier.TIERS.getFirst();
        for (SettlementTier t : SettlementTier.TIERS) {
            if (renown >= t.renown() && (!t.needsBuildings() || (hallValid() && warehouseValid()))) best = t;
        }
        return best;
    }

    public @Nullable SettlementTier nextTier() {
        int i = SettlementTier.TIERS.indexOf(tier());
        return i + 1 < SettlementTier.TIERS.size() ? SettlementTier.TIERS.get(i + 1) : null;
    }

    public int radius() { return tier().radius(); }
    public int popCap() { return tier().popCap(); }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        if (!dim.equals(dimension)) return false;
        long dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
        int r = radius();
        return dx * dx + dz * dz <= (long) r * r && Math.abs(pos.getY() - center.getY()) <= 128;
    }

    public void addRenown(int amount) {
        renown = Math.max(0, renown + amount);
    }

    public void count(String stat, int amount) {
        stats.merge(stat, amount, Integer::sum);
    }

    public int hireCost() {
        return 10 + 6 * residents.size();
    }

    // ---- buildings ------------------------------------------------------------------------

    public boolean hallValid() {
        return buildings.stream().anyMatch(b -> b.is(BuildingRecord.HALL) && b.valid());
    }

    public boolean warehouseValid() {
        return buildings.stream().anyMatch(b -> b.is(BuildingRecord.WAREHOUSE) && b.valid());
    }

    public List<BlockPos> storagePositions() {
        List<BlockPos> out = new ArrayList<>();
        for (BuildingRecord b : buildings) if (b.is(BuildingRecord.WAREHOUSE) && b.valid()) out.addAll(b.containers());
        return out;
    }

    public List<BlockPos> housingBeds() {
        List<BlockPos> out = new ArrayList<>();
        for (BuildingRecord b : buildings) if (b.is(BuildingRecord.HOUSING) && b.valid()) out.addAll(b.beds());
        return out;
    }

    public int freeBeds() {
        int used = 0;
        for (ResidentRecord r : residents) if (r.bed != null) used++;
        return Math.max(0, housingBeds().size() - used);
    }

    public @Nullable BuildingRecord buildingWithBed(BlockPos bed) {
        for (BuildingRecord b : buildings) if (b.beds().contains(bed)) return b;
        return null;
    }

    /** Re-detects every room of the settlement. Returns new building messages for the owner. */
    public List<Component> rescan(ServerLevel level) {
        lastScan = level.getGameTime();
        List<Component> news = new ArrayList<>();
        List<RoomScanner.Room> rooms = new ArrayList<>();
        List<int[]> roles = new ArrayList<>();
        List<BlockPos> anchors = new ArrayList<>();

        if (deskPresent && level.isLoaded(center)) {
            rooms.add(RoomScanner.scan(level, center));
            roles.add(new int[]{BuildingRecord.HALL});
            anchors.add(center);
        }
        ledgers.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof WarehouseLedgerBlock));
        for (BlockPos ledger : ledgers) {
            int idx = roomIndex(rooms, ledger);
            if (idx >= 0) {
                roles.get(idx)[0] |= BuildingRecord.WAREHOUSE;
            } else if (level.isLoaded(ledger)) {
                rooms.add(RoomScanner.scan(level, ledger));
                roles.add(new int[]{BuildingRecord.WAREHOUSE});
                anchors.add(ledger);
            }
        }
        List<List<BlockPos>> stations = new ArrayList<>();
        for (int k = 0; k < rooms.size(); k++) stations.add(new ArrayList<>());
        workshops.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof WorkshopBlock));
        for (BlockPos ws : workshops) {
            if (!level.isLoaded(ws) || !(level.getBlockState(ws).getBlock() instanceof WorkshopBlock wb)) continue;
            int idx = roomIndex(rooms, ws);
            if (idx < 0) {
                rooms.add(RoomScanner.scan(level, ws));
                roles.add(new int[]{0});
                anchors.add(ws);
                stations.add(new ArrayList<>());
                idx = rooms.size() - 1;
            }
            roles.get(idx)[0] |= wb.kind().role();
            stations.get(idx).add(ws);
        }
        int r = radius();
        int minCx = (center.getX() - r) >> 4, maxCx = (center.getX() + r) >> 4;
        int minCz = (center.getZ() - r) >> 4, maxCz = (center.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos p : bedHeads(chunk)) {
                    if (!contains(level.dimension(), p) || roomIndex(rooms, p) >= 0) continue;
                    rooms.add(RoomScanner.scan(level, p));
                    roles.add(new int[]{BuildingRecord.HOUSING});
                    anchors.add(p);
                    stations.add(List.of());
                }
            }

        buildings.clear();
        for (int i = 0; i < rooms.size(); i++) {
            RoomScanner.Room room = rooms.get(i);
            int role = roles.get(i)[0];
            if (!room.beds().isEmpty()) role |= BuildingRecord.HOUSING;
            String problem = room.problem();
            if (problem.isEmpty() && (role & BuildingRecord.WAREHOUSE) != 0 && room.containers().isEmpty()) problem = "no_containers";
            BuildingRecord rec = new BuildingRecord(role, anchors.get(i), room.beds(), room.containers(),
                    room.comfort(), room.interior().size(), problem, List.copyOf(stations.get(i)),
                    room.furnace() ? BuildingRecord.FURNACE : 0);
            buildings.add(rec);
            if (rec.valid()) {
                int baseRole = role & ~BuildingRecord.WORKSHOPS;
                long key = anchors.get(i).asLong() * 31 + baseRole;
                if (baseRole != 0 && awarded.add(key)) {
                    int reward = (rec.is(BuildingRecord.HALL) ? 40 : 0) + (rec.is(BuildingRecord.WAREHOUSE) ? 30 : 0)
                            + (rec.is(BuildingRecord.HOUSING) ? 10 + 5 * rec.beds().size() : 0);
                    addRenown(reward);
                    news.add(Component.translatable("message.hearthholm.building_found", BuildingRecord.title(baseRole), reward));
                }
                for (Workshop w : Workshop.values()) {
                    if (!rec.is(w.role()) || !awarded.add(anchors.get(i).asLong() * 31 + 1000 + w.role())) continue;
                    addRenown(15);
                    news.add(Component.translatable("message.hearthholm.building_found", w.label(), 15));
                }
            }
        }
        assignBeds();
        return news;
    }

    /** Bed heads in a chunk; sections whose palette has no bed are skipped cheaply. */
    private static List<BlockPos> bedHeads(LevelChunk chunk) {
        List<BlockPos> out = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.getBlock() instanceof BedBlock)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) {
                            out.add(new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
                        }
                    }
        }
        return out;
    }

    private static int roomIndex(List<RoomScanner.Room> rooms, BlockPos pos) {
        for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).contains(pos)) return i;
        return -1;
    }

    public void assignBeds() {
        List<BlockPos> beds = housingBeds();
        Set<BlockPos> used = new HashSet<>();
        for (ResidentRecord r : residents) {
            if (r.bed != null && (!beds.contains(r.bed) || !used.add(r.bed))) r.bed = null;
        }
        beds.sort(Comparator.comparingDouble(b -> b.distSqr(center)));
        for (ResidentRecord r : residents) {
            if (r.bed != null) continue;
            for (BlockPos b : beds) {
                if (used.add(b)) { r.bed = b; break; }
            }
        }
    }

    // ---- residents and hiring -------------------------------------------------------------

    public @Nullable ResidentRecord resident(UUID uuid) {
        for (ResidentRecord r : residents) if (r.uuid.equals(uuid)) return r;
        return null;
    }

    public void removeResident(UUID uuid) {
        residents.removeIf(r -> r.uuid.equals(uuid));
    }

    public void ensureCandidates(long day, RandomSource random) {
        if (candidatesDay == day && !candidates.isEmpty()) return;
        candidates.clear();
        for (int i = 0; i < 3; i++) candidates.add(SettlerProfile.random(random));
        candidatesDay = day;
    }

    public List<SiteRecord> sitesOf(Identifier type) {
        List<SiteRecord> out = new ArrayList<>();
        for (SiteRecord s : sites) if (s.type().equals(type)) out.add(s);
        return out;
    }
}
