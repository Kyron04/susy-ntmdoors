package com.ntmdoors.block;

/**
 * Biometric variant of the safe. It shares the body, facing, collision, drops and full-block
 * behaviour; only the front panel model and texture differ. The identity and card-reader logic
 * are added later.
 */
public class BlockBiometricSafe extends BlockSafe {

    public BlockBiometricSafe() {
        super("biometric_safe");
    }
}
