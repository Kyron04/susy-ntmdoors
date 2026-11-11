package com.ntmdoors.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles wireless RF Redstone Gate links and pulse dispatch for keypads, scanners, and safes.
 */
public class RedstoneBinding {

    private final List<BlockPos> gates = new ArrayList<>();
    private boolean onFail = true;
    private boolean onSuccess = true;

    public List<BlockPos> getGates() {
        return gates;
    }

    public boolean isOnFail() {
        return onFail;
    }

    public void setOnFail(boolean onFail) {
        this.onFail = onFail;
    }

    public boolean isOnSuccess() {
        return onSuccess;
    }

    public void setOnSuccess(boolean onSuccess) {
        this.onSuccess = onSuccess;
    }

    public void toggle(PlayerEntity player) {
        player.sendMessage(Text.translatable("ntmdoors.msg.redstone_binding_on"), true);
    }

    public void fireSuccess(World world, BlockPos sourcePos) {
        if (!onSuccess) return;
        fire(world, sourcePos);
    }

    public void fireFail(World world, BlockPos sourcePos) {
        if (!onFail) return;
        fire(world, sourcePos);
    }

    public void fire(World world, BlockPos sourcePos) {
        if (world == null || world.isClient) return;
        // In M4, linked RedstoneGateBlockEntity instances will be pulsed here.
    }

    public void clearGates() {
        gates.clear();
    }

    public void writeNbt(NbtCompound tag) {
        tag.putBoolean("rsOnFail", onFail);
        tag.putBoolean("rsOnSuccess", onSuccess);
        NbtList list = new NbtList();
        for (BlockPos p : gates) {
            NbtCompound c = new NbtCompound();
            c.putLong("pos", p.asLong());
            list.add(c);
        }
        tag.put("rsGates", list);
    }

    public void readNbt(NbtCompound tag) {
        if (tag.contains("rsOnFail")) onFail = tag.getBoolean("rsOnFail");
        if (tag.contains("rsOnSuccess")) onSuccess = tag.getBoolean("rsOnSuccess");
        gates.clear();
        if (tag.contains("rsGates", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("rsGates", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                gates.add(BlockPos.fromLong(list.getCompound(i).getLong("pos")));
            }
        }
    }
}
