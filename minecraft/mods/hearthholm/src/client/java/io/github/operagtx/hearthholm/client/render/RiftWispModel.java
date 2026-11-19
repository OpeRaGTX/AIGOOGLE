package io.github.operagtx.hearthholm.client.render;

import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** A glowing core with a shell and three orbiting motes. */
public class RiftWispModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Hearthholm.id("rift_wisp"), "main");
    private final ModelPart core, shell, orbit;

    public RiftWispModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucentEmissive);
        this.core = root.getChild("core");
        this.shell = root.getChild("shell");
        this.orbit = root.getChild("orbit");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(0, 20, 0));
        root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 8).addBox(-3, -3, -3, 6, 6, 6), PartPose.offset(0, 20, 0));
        PartDefinition orbit = root.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(0, 20, 0));
        orbit.addOrReplaceChild("m0", CubeListBuilder.create().texOffs(16, 0).addBox(-1, -1, -1, 2, 2, 2), PartPose.offset(5, 0, 0));
        orbit.addOrReplaceChild("m1", CubeListBuilder.create().texOffs(16, 0).addBox(-1, -1, -1, 2, 2, 2), PartPose.offset(-2.5f, 1.5f, 4.3f));
        orbit.addOrReplaceChild("m2", CubeListBuilder.create().texOffs(16, 0).addBox(-1, -1, -1, 2, 2, 2), PartPose.offset(-2.5f, -1.5f, -4.3f));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        core.yRot = t * 0.15f;
        core.xRot = t * 0.1f;
        shell.yRot = -t * 0.07f;
        shell.zRot = t * 0.05f;
        orbit.yRot = t * 0.25f;
        float bob = (float) Math.sin(t * 0.2f) * 1.2f;
        core.y = 20 + bob;
        shell.y = 20 + bob;
        orbit.y = 20 + bob;
    }
}
