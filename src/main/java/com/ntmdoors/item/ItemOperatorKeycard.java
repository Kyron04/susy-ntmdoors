package com.ntmdoors.item;

/**
 * Operator Keycard. A privileged card produced by the Keycard Programmer; its access rules will be
 * defined later. It shares the RFID card item model and hand display, so it is also recognised by
 * the scanner as a card.
 */
public class ItemOperatorKeycard extends ItemRfidCard {

    public ItemOperatorKeycard() {
        super("operator_keycard");
    }
}
