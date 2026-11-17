package io.github.operagtx.hearthholm.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Own state type: extending AvatarRenderState would route rendering to the vanilla player renderer. */
public class SettlerRenderState extends HumanoidRenderState {
    public Identifier texture;
    public boolean slim;
}
