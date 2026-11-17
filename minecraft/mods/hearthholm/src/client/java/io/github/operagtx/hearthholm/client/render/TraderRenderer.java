package io.github.operagtx.hearthholm.client.render;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.entity.trader.TraderEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** The caravan trader: the player mesh with an original hooded-cloak skin. */
public class TraderRenderer extends HumanoidMobRenderer<TraderEntity, AshbornRenderState, HumanoidModel<AshbornRenderState>> {
    private static final Identifier TEX = Hearthholm.id("textures/entity/trader.png");

    public TraderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public AshbornRenderState createRenderState() {
        return new AshbornRenderState();
    }

    @Override
    public Identifier getTextureLocation(AshbornRenderState state) {
        return TEX;
    }
}
