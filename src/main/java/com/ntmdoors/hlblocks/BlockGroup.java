package com.ntmdoors.hlblocks;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;

import java.util.List;

/**
 * An HL block that keeps its original textures as block states, so one item can be cycled through
 * the whole family. The variant property and list are static in every subclass because
 * {@link net.minecraft.block.Block}'s constructor calls {@link #createBlockState()} before any
 * instance field has been assigned.
 */
public abstract class BlockGroup extends HLSoundBlock {

    public static final String VARIANT = "variant";

    protected BlockGroup(Material material, SoundType sound) {
        super(material, sound);
        setDefaultState(this.blockState.getBaseState()
                .withProperty(getVariantProperty(), getVariants().get(0)));
    }

    public abstract PropertyEnum<Variant> getVariantProperty();

    public abstract List<Variant> getVariants();

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, getVariantProperty());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        List<Variant> variants = getVariants();
        return getDefaultState().withProperty(
                getVariantProperty(), variants.get(Math.floorMod(meta, variants.size())));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return getVariants().indexOf(state.getValue(getVariantProperty()));
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    public static final class Freezer extends BlockGroup {

        private static final List<Variant> VARIANTS = Variant.of("freezerc2", "freezerc3");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Freezer() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Fifties extends BlockGroup {

        private static final List<Variant> VARIANTS =
                Variant.of("fifties_cch1", "fifties_ceil1", "fifties_ceil5");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Fifties() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Crete extends BlockGroup {

        private static final List<Variant> VARIANTS =
                Variant.of("crete2_wall01", "crete3_ceil01", "crete3_flr04");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Crete() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Duct extends BlockGroup {

        private static final List<Variant> VARIANTS = Variant.of("duct_wall01");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Duct() {
            super(Material.IRON, SoundType.METAL);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Lab extends BlockGroup {

        private static final List<Variant> VARIANTS =
                Variant.of("lab1_c4003", "lab1_floor4", "lab1_flr4c");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Lab() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Tunnel extends BlockGroup {

        private static final List<Variant> VARIANTS =
                Variant.of("tnnl_flr4b", "tnnl_flr6", "tnnl_flr6b", "tnnl_plt1a");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Tunnel() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }

    public static final class Generic extends BlockGroup {

        private static final List<Variant> VARIANTS =
                Variant.of("generic015t", "generic015v", "generic80");
        private static final PropertyEnum<Variant> PROPERTY =
                PropertyEnum.create(VARIANT, Variant.class, VARIANTS);

        public Generic() {
            super(Material.ROCK, SoundType.STONE);
        }

        @Override
        public PropertyEnum<Variant> getVariantProperty() {
            return PROPERTY;
        }

        @Override
        public List<Variant> getVariants() {
            return VARIANTS;
        }
    }
}
