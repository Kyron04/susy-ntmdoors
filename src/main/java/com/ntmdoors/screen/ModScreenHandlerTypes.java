package com.ntmdoors.screen;

import com.ntmdoors.NTMDoors;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * Registry of ScreenHandlerTypes for Milestone M3.
 */
public final class ModScreenHandlerTypes {

    public static final ScreenHandlerType<KeycardProgrammerScreenHandler> KEYCARD_PROGRAMMER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(NTMDoors.MOD_ID, "keycard_programmer"),
            new ExtendedScreenHandlerType<>(KeycardProgrammerScreenHandler::new)
    );

    public static final ScreenHandlerType<SafeScreenHandler> SAFE = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(NTMDoors.MOD_ID, "safe"),
            new ExtendedScreenHandlerType<>(SafeScreenHandler::new)
    );

    public static final ScreenHandlerType<TerminalBackupScreenHandler> TERMINAL_BACKUP = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(NTMDoors.MOD_ID, "terminal_backup"),
            new ExtendedScreenHandlerType<>(TerminalBackupScreenHandler::new)
    );

    public static final ScreenHandlerType<KeycardHolderScreenHandler> KEYCARD_HOLDER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(NTMDoors.MOD_ID, "keycard_holder"),
            new ExtendedScreenHandlerType<>(KeycardHolderScreenHandler::new)
    );

    public static void registerScreenHandlers() {
        // Triggers static initialization
    }

    private ModScreenHandlerTypes() {}
}
