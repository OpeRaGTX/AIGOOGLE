package io.github.operagtx.hearthholm.client.render;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.entity.ashborn.AshbornEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/** Ashborn: the player mesh with an original 64x64 skin per kind (textures/entity/ashborn/<kind>.png). */
public class AshbornRenderer<T extends AshbornEntity> extends HumanoidMobRenderer<T, AshbornRenderState, HumanoidModel<AshbornRenderState>> {
    public AshbornRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public AshbornRenderState createRenderState() {
        return new AshbornRenderState();
    }

    @Override
    public void extractRenderState(T entity, AshbornRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        String kind = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        state.texture = Hearthholm.id("textures/entity/ashborn/" + kind + ".png");
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
        if (arm == mob.getMainArm() && mob.isAggressive() && mob.getMainHandItem().is(Items.BOW)) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return super.getArmPose(mob, arm);
    }

    @Override
    public Identifier getTextureLocation(AshbornRenderState state) {
        return state.texture;
    }
}
