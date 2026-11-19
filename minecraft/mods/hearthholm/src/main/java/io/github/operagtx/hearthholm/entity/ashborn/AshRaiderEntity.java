package io.github.operagtx.hearthholm.entity.ashborn;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The plain raider: torch or axe, medium health. */
public class AshRaiderEntity extends AshbornEntity {
    public AshRaiderEntity(EntityType<? extends AshRaiderEntity> type, Level level) {
        super(type, level);
    }
}
