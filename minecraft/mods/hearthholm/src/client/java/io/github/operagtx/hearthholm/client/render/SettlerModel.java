package io.github.operagtx.hearthholm.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The vanilla player mesh driven as a humanoid, with two wave H3 touches for women: the trouser overlay layers are widened into a skirt
 * that flares (and swings with the legs), and the hat layer grows a little so long hair has volume.
 */
public class SettlerModel extends HumanoidModel<SettlerRenderState> {
    private final ModelPart hat, leftPants, rightPants, jacket;

    public SettlerModel(ModelPart root) {
        super(root);
        this.hat = head.getChild("hat");
        this.leftPants = leftLeg.getChild("left_pants");
        this.rightPants = rightLeg.getChild("right_pants");
        this.jacket = body.getChild("jacket");
    }

    @Override
    public void setupAnim(SettlerRenderState state) {
        super.setupAnim(state);
        float f = state.skirtFlare;
        leftPants.xScale = rightPants.xScale = f;
        leftPants.zScale = rightPants.zScale = f;
        float h = state.hairVolume;
        hat.xScale = hat.zScale = h;
        hat.yScale = 1f + (h - 1f) * 0.6f;
    }
}
