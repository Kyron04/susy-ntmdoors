package com.ntmdoors.concrete;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * "Ultra High Performance Concrete". A full cube with six grayscale variants: the original
 * texture plus five progressively darker shades. Every variant takes three real hours of
 * continuous mining to break with any tool. Creative-only: no crafting recipe, no Chisel group.
 *
 * <p>Blast handling is an explicit allow-list. Only ICBM Classic's nuclear, antimatter and red
 * matter weapons may damage the concrete; every other explosion, thermobaric included, is fully
 * resisted. The material is reported as iron so ICBM's exothermic/endothermic terrain beams skip
 * the block's rock mutation, and ICBM's flying-block ban list (config) keeps the beams from
 * tearing it out as a flying block.</p>
 */
public class BlockUltraHighPerformanceConcrete extends BlockConcreteVariant {

    /** Translation suffixes; index 0 is the original, 1-5 are the darker shades. */
    public static final String[] VARIANT_NAMES = {
            "plain", "light_gray", "gray", "dark_gray", "charcoal", "black"
    };

    /** Item model paths, matching the texture/model file names. */
    public static final String[] MODEL_NAMES = {
            "ultra_high_performance_concrete",
            "ultra_high_performance_concrete_light_gray",
            "ultra_high_performance_concrete_gray",
            "ultra_high_performance_concrete_dark_gray",
            "ultra_high_performance_concrete_charcoal",
            "ultra_high_performance_concrete_black"
    };

    private static final String ICBM_NUCLEAR =
            "icbm.classic.content.blast.threaded.BlastNuclear";
    private static final String ICBM_ANTIMATTER =
            "icbm.classic.content.blast.threaded.BlastAntimatter";

    /**
     * Effective resistance offered to the allowed weapons. ICBM subtracts this from a 60-120 power
     * ray that also spends ~2.25 per block traveled, so this yields a ~4-8 block nuclear breach.
     * Antimatter ignores resistance entirely and replaces blocks directly.
     */
    private static final float BREACH_RESISTANCE = 12F;

    /** Three real hours of continuous mining at 20 ticks per second, regardless of the tool. */
    private static final float BREAK_DAMAGE_PER_TICK = 1F / (3F * 60F * 60F * 20F);

    public BlockUltraHighPerformanceConcrete() {
        super("ultra_high_performance_concrete", VARIANT_NAMES, MODEL_NAMES, true, 150F, 1000F);
        // Hardness is a large whole-block value like the safe/security doors; the per-tick override
        // below is what actually makes mining take three hours regardless of the tool used.
        setHardness(216000F);
        // Base value only; getExplosionResistance decides per explosion type.
        setResistance(20F);
        setHarvestLevel("pickaxe", 2);
    }

    /**
     * Only nuclear and antimatter blasts are allowed through the resistance check. Every other
     * explosion (thermobaric, TNT, creepers, breaching charges, sonic, etc.) is fully resisted.
     * Red matter edits blocks directly and never asks for resistance; it is handled by
     * onBlockExploded only for completeness.
     */
    @Override
    public float getExplosionResistance(World world, BlockPos pos, @Nullable Entity exploder,
                                        Explosion explosion) {
        return isNuclearOrAntimatter(explosion) ? BREACH_RESISTANCE : Float.MAX_VALUE;
    }

    /** Safety net for blasts that call the destroy hook directly instead of reading resistance. */
    @Override
    public void onBlockExploded(World world, BlockPos pos, Explosion explosion) {
        if (isNuclearOrAntimatter(explosion) || isRedmatter(explosion)) {
            super.onBlockExploded(world, pos, explosion);
        }
    }

    /**
     * Report iron instead of rock so ICBM's exothermic/endothermic beams do not classify this
     * block as mutable stone. The block still renders, collides and mines like the other concrete.
     */
    @Override
    public Material getMaterial(IBlockState state) {
        return Material.IRON;
    }

    private static boolean isNuclearOrAntimatter(@Nullable Explosion explosion) {
        return hasClass(explosion, ICBM_NUCLEAR) || hasClass(explosion, ICBM_ANTIMATTER);
    }

    private static boolean isRedmatter(@Nullable Explosion explosion) {
        return explosion != null && explosion.getClass().getName().contains("Redmatter");
    }

    /** Matches an ICBM blast class without adding a compile-time ICBM dependency. */
    private static boolean hasClass(@Nullable Explosion explosion, String className) {
        for (Class<?> type = explosion == null ? null : explosion.getClass();
             type != null; type = type.getSuperclass()) {
            if (className.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Mining takes three real hours with any tool, matching the safe and security doors. */
    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player,
                                                World world, BlockPos pos) {
        return BREAK_DAMAGE_PER_TICK;
    }
}
