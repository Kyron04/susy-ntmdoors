package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

/**
 * A full cube whose look is chosen by metadata, mirroring the NTM concrete blocks that pack
 * several variants into one block id. The metadata range is fixed at 0-15 like HBM's blocks so the
 * blockstate keys stay {@code meta=N} and match the model layout used by NTM resource packs.
 */
public class BlockConcreteVariant extends Block {

    public static final PropertyInteger META = PropertyInteger.create("meta", 0, 15);

    private final String[] variantNames;
    private final String[] modelNames;
    private final boolean separateNames;

    public BlockConcreteVariant(String name, String[] variantNames, String[] modelNames,
                                boolean separateNames, float hardness, float resistance) {
        super(Material.ROCK);
        this.variantNames = variantNames;
        this.modelNames = modelNames;
        this.separateNames = separateNames;
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(ConcreteSet.TAB);
        setHardness(hardness);
        setResistance(resistance);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(META, 0));
    }

    public int variantCount() {
        return variantNames.length;
    }

    /** Translation suffix for a metadata value; only used when {@link #hasSeparateNames()}. */
    public String variantName(int meta) {
        return variantNames[clamp(meta)];
    }

    /** Item model path for a metadata value, without the namespace. */
    public String modelName(int meta) {
        return modelNames[clamp(meta)];
    }

    /** False for blocks like Über Concrete where every stage shares one display name. */
    public boolean hasSeparateNames() {
        return separateNames;
    }

    /**
     * How many metadata values are listed as separate creative/HEI entries. Blocks that share one
     * display name (Uber Concrete's sixteen decay stages) only list the first stage; the rest are
     * still placeable by metadata but do not clutter the item panel.
     */
    public int creativeVariantCount() {
        return separateNames ? variantNames.length : 1;
    }

    private int clamp(int meta) {
        return MathHelper.clamp(meta, 0, variantNames.length - 1);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, META);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(META, clamp(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(META);
    }

    /** Keep the variant in the dropped stack, otherwise every broken block turns into meta 0. */
    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess world, BlockPos pos,
                                    SpawnPlacementType type) {
        return false;
    }
}
