// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Pending contraband-container binding stored on an Emancipation Relay Key. The final binding is
 * written to the Grill only after the player confirms at the original Grill.
 */
public final class GrillContainmentData {

    public static final int AWAIT_CONTAINER = 1;
    public static final int AWAIT_CONFIRM = 2;
    public static final long TIMEOUT_MS = 5L * 60L * 1000L;

    private static final String ROOT = "GrillContainment";
    private static final String STAGE = "Stage";
    private static final String GRILL = "Grill";
    private static final String GRILL_DIM = "GrillDim";
    private static final String CONTAINER = "Container";
    private static final String CONTAINER_DIM = "ContainerDim";
    private static final String CONTAINER_SIDE = "ContainerSide";
    private static final String CONTAINER_NAME = "ContainerName";
    private static final String OWNER = "Owner";
    private static final String EXPIRES = "Expires";

    public static final class Session {

        public final int stage;
        public final BlockPos grill;
        public final int grillDim;
        @Nullable
        public final BlockPos container;
        public final int containerDim;
        public final int containerSide;
        public final String containerName;
        public final UUID owner;
        public final long expires;

        private Session(int stage, BlockPos grill, int grillDim, @Nullable BlockPos container,
                        int containerDim, int containerSide, String containerName, UUID owner,
                        long expires) {
            this.stage = stage;
            this.grill = grill;
            this.grillDim = grillDim;
            this.container = container;
            this.containerDim = containerDim;
            this.containerSide = containerSide;
            this.containerName = containerName == null ? "" : containerName;
            this.owner = owner;
            this.expires = expires;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expires;
        }

        public boolean hasContainer() {
            return stage == AWAIT_CONFIRM && container != null;
        }
    }

    @Nullable
    public static Session getSession(ItemStack stack) {
        NBTTagCompound tag = root(stack, false);
        if (tag == null || !tag.hasKey(STAGE) || !tag.hasKey(GRILL)
                || !tag.hasKey(OWNER) || !tag.hasKey(EXPIRES)) {
            return null;
        }

        UUID owner;
        try {
            owner = UUID.fromString(tag.getString(OWNER));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        BlockPos grill = BlockPos.fromLong(tag.getLong(GRILL));
        BlockPos container = tag.hasKey(CONTAINER)
                ? BlockPos.fromLong(tag.getLong(CONTAINER)) : null;
        int stage = tag.getByte(STAGE);
        if (stage != AWAIT_CONTAINER && stage != AWAIT_CONFIRM) return null;
        return new Session(
                stage, grill, tag.getInteger(GRILL_DIM), container,
                tag.getInteger(CONTAINER_DIM), tag.getByte(CONTAINER_SIDE),
                tag.getString(CONTAINER_NAME), owner, tag.getLong(EXPIRES));
    }

    public static void start(ItemStack stack, BlockPos grill, int grillDim, UUID owner) {
        NBTTagCompound tag = root(stack, true);
        if (tag == null) return;
        tag.setByte(STAGE, (byte) AWAIT_CONTAINER);
        tag.setLong(GRILL, grill.toLong());
        tag.setInteger(GRILL_DIM, grillDim);
        tag.setString(OWNER, owner.toString());
        tag.setLong(EXPIRES, System.currentTimeMillis() + TIMEOUT_MS);
        tag.removeTag(CONTAINER);
        tag.removeTag(CONTAINER_DIM);
        tag.removeTag(CONTAINER_SIDE);
        tag.removeTag(CONTAINER_NAME);
    }

    public static void setContainer(ItemStack stack, BlockPos container, int dimension, int side,
                                    String name) {
        NBTTagCompound tag = root(stack, true);
        if (tag == null) return;
        tag.setByte(STAGE, (byte) AWAIT_CONFIRM);
        tag.setLong(CONTAINER, container.toLong());
        tag.setInteger(CONTAINER_DIM, dimension);
        tag.setByte(CONTAINER_SIDE, (byte) side);
        tag.setString(CONTAINER_NAME, name == null ? "" : name);
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        tag.removeTag(ROOT);
        if (tag.getSize() == 0) stack.setTagCompound(null);
    }

    @Nullable
    private static NBTTagCompound root(ItemStack stack, boolean create) {
        if (stack.isEmpty()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            if (!create) return null;
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (!tag.hasKey(ROOT, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
            if (!create) return null;
            tag.setTag(ROOT, new NBTTagCompound());
        }
        return tag.getCompoundTag(ROOT);
    }

    private GrillContainmentData() {
    }
}
