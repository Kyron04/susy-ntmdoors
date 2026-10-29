package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockKeycardScanner;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the keycard scanner model. The transform mirrors {@link BlockKeycardScanner#plate}:
 * the model is authored facing +Z (like the keypad) and its back plate sits on the block's
 * wall-side face.
 */
@SideOnly(Side.CLIENT)
public class RenderKeycardScanner extends TileEntitySpecialRenderer<TileEntityKeycardScanner> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keycard_scanner.png");
    public static final ResourceLocation MODEL = new ResourceLocation(NTMDoors.MODID, "models/doors/keycard_scanner.obj");

    /** Drawn in this order; each group is a separate display list. */
    private static final String[] GROUPS = {
            "Base", "Trim", "Screen", "LightGreen", "LightYellow", "LightRed", "Slot", "Vents"
    };

    @Override
    public void render(TileEntityKeycardScanner te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockKeycardScanner)) return;
        EnumFacing facing = state.getValue(BlockKeycardScanner.FACING);

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(Facings.yaw(facing), 0F, 1F, 0F);
        GlStateManager.translate(0, BlockKeycardScanner.MODEL_OFFSET_Y, BlockKeycardScanner.MODEL_OFFSET_Z);
        GlStateManager.scale(BlockKeycardScanner.MODEL_SCALE_X, BlockKeycardScanner.MODEL_SCALE_Y, BlockKeycardScanner.MODEL_SCALE_Z);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        bindTexture(TEXTURE);
        for (String group : GROUPS) model.draw(group);

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }
}
