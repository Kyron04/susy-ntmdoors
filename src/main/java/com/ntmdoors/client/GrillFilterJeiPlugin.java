// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.gui.IGuiProperties;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional JEI/HEI bridge: drag an item onto a filter slot to set its pattern. The plugin only
 * loads when the JEI API is present, so JEI is not a runtime dependency.
 */
@SideOnly(Side.CLIENT)
@JEIPlugin
public class GrillFilterJeiPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        registry.addGuiScreenHandler(GuiEmancipationGrill.class,
                GrillFilterJeiPlugin::guiProperties);
        registry.addGhostIngredientHandler(GuiEmancipationGrill.class, new FilterGhostHandler());
    }

    private static IGuiProperties guiProperties(GuiEmancipationGrill gui) {
        return gui.isFilterPage() ? new GrillGuiProperties(gui) : null;
    }

    private static final class GrillGuiProperties implements IGuiProperties {

        private final GuiEmancipationGrill gui;

        private GrillGuiProperties(GuiEmancipationGrill gui) {
            this.gui = gui;
        }

        @Override
        public Class<? extends GuiScreen> getGuiClass() {
            return GuiEmancipationGrill.class;
        }

        @Override
        public int getGuiLeft() {
            return gui.getPanelLeft();
        }

        @Override
        public int getGuiTop() {
            return gui.getPanelTop();
        }

        @Override
        public int getGuiXSize() {
            return gui.getPanelWidth();
        }

        @Override
        public int getGuiYSize() {
            return gui.getPanelHeight();
        }

        @Override
        public int getScreenWidth() {
            return gui.width;
        }

        @Override
        public int getScreenHeight() {
            return gui.height;
        }
    }

    private static final class FilterGhostHandler
            implements IGhostIngredientHandler<GuiEmancipationGrill> {

        @Override
        public <I> List<Target<I>> getTargets(GuiEmancipationGrill gui, I ingredient,
                                               boolean doStart) {
            if (!(ingredient instanceof ItemStack)) return Collections.emptyList();
            List<Target<I>> targets = new ArrayList<>();
            for (GuiEmancipationGrill.FilterTarget slot : gui.getFilterTargets()) {
                targets.add(new Target<I>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.x, slot.y, slot.width, slot.height);
                    }

                    @Override
                    public void accept(I value) {
                        if (value instanceof ItemStack) {
                            gui.setFilterFromJei(slot.whitelist, slot.slot, (ItemStack) value);
                        }
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;
        }
    }
}
