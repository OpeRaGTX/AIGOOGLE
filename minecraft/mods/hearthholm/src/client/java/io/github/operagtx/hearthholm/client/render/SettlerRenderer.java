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
        state.bubble = entity.hasBubble() ? entity.bubble() : null;
    }

    /** The speech bubble: the line (wrapped to at most 2 rows) above the head, over the nameplate. */
    private void submitBubble(SettlerRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        String text = state.bubble.getString();
        var style = state.bubble.getStyle();
        java.util.List<String> rows = new java.util.ArrayList<>();
        if (text.length() <= 24) rows.add(text);
        else {
            int cut = text.lastIndexOf(' ', Math.min(text.length() - 1, 26));
            if (cut < 8) cut = Math.min(text.length(), 24);
            rows.add(text.substring(0, cut).strip());
            rows.add(text.substring(cut).strip());
        }
        pose.pushPose();
        for (int i = 0; i < rows.size(); i++) {
            int y = -(16 + (rows.size() - 1 - i) * 10);
            collector.submitNameTag(pose, state.nameTagAttachment, y, net.minecraft.network.chat.Component.literal(rows.get(i)).withStyle(style),
                    !state.isDiscrete, state.lightCoords, camera);
        }
        pose.popPose();
    }

    @Override
    public Identifier getTextureLocation(SettlerRenderState state) {
        return state.texture;
    }

    @Override
    public void submit(SettlerRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.slim ? slim : wide;
        super.submit(state, pose, collector, camera);
        if (state.bubble != null && state.nameTagAttachment != null && state.distanceToCameraSq < 24 * 24 && !state.isInvisible) {
            submitBubble(state, pose, collector, camera);
        }
    }
}
