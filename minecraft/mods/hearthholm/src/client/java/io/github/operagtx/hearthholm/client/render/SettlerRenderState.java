package io.github.operagtx.hearthholm.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Own state type: extending AvatarRenderState would route rendering to the vanilla player renderer. */
public class SettlerRenderState extends HumanoidRenderState {
    public Identifier texture;
    public boolean slim;
    /** Wave H3: skirt width factor of the trouser layer (1 = plain trousers) and hat-layer (hair) growth factor. */
    public float skirtFlare = 1f, hairVolume = 1f;
    /** Speech bubble text (null when silent) and whether it is a legendary settler (gold text). */
    public net.minecraft.network.chat.Component bubble;
}
