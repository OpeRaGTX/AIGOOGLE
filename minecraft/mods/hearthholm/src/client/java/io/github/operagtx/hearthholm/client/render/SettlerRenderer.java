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
public class SettlerRenderer extends HumanoidMobRenderer<SettlerEntity, SettlerRenderState, SettlerModel> {
    private final SettlerModel wide;
    private final SettlerModel slim;

    public SettlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SettlerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.wide = this.model;
        this.slim = new SettlerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM));
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
        Appearance ap = Appearance.unpack(look);
        state.slim = ap.female();
        state.skirtFlare = ap.female() && ap.outfit() >= 1 && ap.outfit() <= 3 ? (ap.outfit() == 2 ? 1.3f : 1.4f) : ap.outfit() == 6 ? 1.2f : 1f;
        state.hairVolume = ap.female() && ap.headwear() == 0 ? 1.1f : 1f;
        state.texture = SettlerTextures.get(look, entity.professionId());
        state.bubble = entity.hasBubble() ? entity.bubble() : null;
        if (entity.isUsingItem() && entity.getMainHandItem().is(net.minecraft.world.item.Items.BOW)) {
            // archers drawing the bow
            if (state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            else state.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        // vanilla only fills the name-tag anchor for the entity under the crosshair; bubbles need it for everyone
        if (state.bubble != null && state.nameTagAttachment == null) {
            state.nameTagAttachment = entity.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
        }
    }

    /** Wraps a line into rows of at most {@code width} characters (at most 3 rows, the last one cut with "…"). */
    static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        String rest = text.strip();
        while (!rest.isEmpty() && rows.size() < 3) {
            if (rest.length() <= width) { rows.add(rest); break; }
            int cut = rest.lastIndexOf(' ', width);
            if (cut < width / 2) cut = width;
            if (rows.size() == 2) { rows.add(rest.substring(0, Math.min(rest.length(), width - 1)).strip() + "…"); break; }
            rows.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        return rows;
    }

    /**
     * The speech bubble: the line in narrow rows, drawn smaller than a name tag above the head so the bubbles
     * of settlers standing side by side do not run into each other.
     */
    private void submitBubble(SettlerRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var style = state.bubble.getStyle();
        java.util.List<String> rows = wrap(state.bubble.getString(), 18);
        var at = state.nameTagAttachment;
        pose.pushPose();
        pose.translate(at.x, at.y + 0.32, at.z);
        pose.scale(0.7f, 0.7f, 0.7f);
        for (int i = 0; i < rows.size(); i++) {
            int y = -(rows.size() - 1 - i) * 10;
            collector.submitNameTag(pose, net.minecraft.world.phys.Vec3.ZERO, y, net.minecraft.network.chat.Component.literal(rows.get(i)).withStyle(style),
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
