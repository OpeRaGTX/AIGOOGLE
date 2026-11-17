package io.github.operagtx.hearthholm.core;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.api.KeyedRegistry;
import io.github.operagtx.hearthholm.entity.ai.jobs.*;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Professions {
    public static final KeyedRegistry<Profession> REGISTRY = new KeyedRegistry<>("profession");

    public static final Identifier UNEMPLOYED_ID = Hearthholm.id("unemployed");
    public static Profession UNEMPLOYED;
    public static Profession BUILDER;
    public static Profession MINER;
    public static Profession LUMBERJACK;
    public static Profession FARMER;
    public static Profession FISHER;
    public static Profession GUARD;
    public static Profession WARRIOR;

    private Professions() {}

    public static Profession get(Identifier id) {
        return REGISTRY.getOr(id, UNEMPLOYED);
    }

    private static Profession reg(Profession p) {
        return REGISTRY.register(p.id(), p);
    }

    public static void bootstrap() {
        UNEMPLOYED = reg(Profession.builder(UNEMPLOYED_ID)
                .color(0xB0A890).icon(() -> new ItemStack(Items.BREAD))
                .job(UnemployedJob::new).build());
        BUILDER = reg(Profession.builder(Hearthholm.id("builder"))
                .weight(Stat.WITS, 3).weight(Stat.DEXTERITY, 2).weight(Stat.ENDURANCE, 1)
                .tool(s -> s.is(ItemTags.SHOVELS)).site(WorkSiteTypes.CONSTRUCTION_ID)
                .color(0xF2B33D).icon(() -> new ItemStack(Items.BRICKS))
                .job(BuilderJob::new).build());
        MINER = reg(Profession.builder(Hearthholm.id("miner"))
                .weight(Stat.STRENGTH, 3).weight(Stat.ENDURANCE, 2).weight(Stat.PERCEPTION, 1)
                .tool(s -> s.is(ItemTags.PICKAXES)).site(WorkSiteTypes.MINE_ID)
                .color(0x9AA3AD).icon(() -> new ItemStack(Items.IRON_PICKAXE))
                .job(MinerJob::new).build());
        LUMBERJACK = reg(Profession.builder(Hearthholm.id("lumberjack"))
                .weight(Stat.STRENGTH, 3).weight(Stat.DEXTERITY, 1).weight(Stat.ENDURANCE, 2)
                .tool(s -> s.is(ItemTags.AXES)).site(WorkSiteTypes.FOREST_ID)
                .color(0xB5653A).icon(() -> new ItemStack(Items.IRON_AXE))
                .job(LumberjackJob::new).build());
        FARMER = reg(Profession.builder(Hearthholm.id("farmer"))
                .weight(Stat.ENDURANCE, 2).weight(Stat.WITS, 2).weight(Stat.DEXTERITY, 1)
                .tool(s -> s.is(ItemTags.HOES)).site(WorkSiteTypes.FARM_ID)
                .color(0x8DBF4A).icon(() -> new ItemStack(Items.WHEAT))
                .job(FarmerJob::new).build());
        FISHER = reg(Profession.builder(Hearthholm.id("fisher"))
                .weight(Stat.PERCEPTION, 3).weight(Stat.WITS, 1).weight(Stat.DEXTERITY, 1)
                .tool(s -> s.is(Items.FISHING_ROD)).site(WorkSiteTypes.FISHING_ID)
                .color(0x4FA3C7).icon(() -> new ItemStack(Items.FISHING_ROD))
                .job(FisherJob::new).build());
        GUARD = reg(Profession.builder(Hearthholm.id("guard"))
                .weight(Stat.PERCEPTION, 3).weight(Stat.DEXTERITY, 2).weight(Stat.ENDURANCE, 1)
                .tool(s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES)).site(WorkSiteTypes.PATROL_ID)
                .combat().nightShift().color(0x6C7FD8).icon(() -> new ItemStack(Items.IRON_SWORD))
                .job(GuardJob::new).build());
        WARRIOR = reg(Profession.builder(Hearthholm.id("warrior"))
                .weight(Stat.STRENGTH, 3).weight(Stat.ENDURANCE, 2).weight(Stat.DEXTERITY, 1)
                .tool(s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES)).site(WorkSiteTypes.TERRITORY_ID)
                .combat().color(0xC9483C).icon(() -> new ItemStack(Items.SHIELD))
                .job(WarriorJob::new).build());
    }
}
