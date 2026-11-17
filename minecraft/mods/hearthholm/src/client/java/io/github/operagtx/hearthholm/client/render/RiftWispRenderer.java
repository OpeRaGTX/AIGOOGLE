package io.github.operagtx.hearthholm.client.render;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.entity.ashborn.RiftWispEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class RiftWispRenderer extends MobRenderer<RiftWispEntity, LivingEntityRenderState, RiftWispModel> {
    private static final Identifier TEXTURE = Hearthholm.id("textures/entity/rift_wisp.png");

    public RiftWispRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RiftWispModel(ctx.bakeLayer(RiftWispModel.LAYER)), 0.2f);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(RiftWispEntity entity, BlockPos pos) {
        return 15;
    }
}
