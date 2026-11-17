package io.github.operagtx.hearthholm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.settlement.Appearance;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Player-shaped settlers: the vanilla player mesh (with hat/jacket/sleeve/pants overlays as
 * child parts) driven as a humanoid, wide or slim arms, composed skin, armour and held tools.
 */
public class SettlerRenderer extends HumanoidMobRenderer<SettlerEntity, SettlerRenderState, HumanoidModel<SettlerRenderState>> {
    private final HumanoidModel<SettlerRenderState> wide;
    private final HumanoidModel<SettlerRenderState> slim;

    public SettlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.wide = this.model;
        this.slim = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM));
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
                ctx.getEquipmentRenderer()));
    }

    @Override
    public SettlerRenderState createRenderState() {
        return new SettlerRenderState();
    }

    @Override
    public void extractRenderState(SettlerEntity entity, SettlerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        int look = entity.lookBits();
        state.slim = Appearance.unpack(look).female();
        state.texture = SettlerTextures.get(look, entity.professionId());
    }

    @Override
    public Identifier getTextureLocation(SettlerRenderState state) {
        return state.texture;
    }

    @Override
    public void submit(SettlerRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.slim ? slim : wide;
        super.submit(state, pose, collector, camera);
    }
}
