package com.ntmdoors.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared stage ids for the terminal display that is mirrored onto the physical monitor
 * screens. The ids are deliberately explicit instead of using enum ordinals so a saved desk
 * still points at the same page if the GUI order changes later.
 */
public final class MonitorDisplay {

    public static final int STAGE_ASK = 0;
    public static final int STAGE_SET = 1;
    public static final int STAGE_CONFIRM = 2;
    public static final int STAGE_ENTER = 3;
    public static final int STAGE_MENU = 4;
    public static final int STAGE_SECURITY_HOME = 5;
    public static final int STAGE_SECURITY = 6;
    public static final int STAGE_NAME_NET = 7;
    public static final int STAGE_NAME_KEY = 8;
    public static final int STAGE_DELETE_NET = 9;
    public static final int STAGE_DISPLAY = 10;
    public static final int STAGE_NOTEPAD = 11;
    public static final int STAGE_COUNT = 12;

    /** The first page a fresh desk shows before anyone has opened the terminal. */
    public static final int DEFAULT_STAGE = STAGE_MENU;

    public static boolean isValidStage(int stage) {
        return stage >= 0 && stage < STAGE_COUNT;
    }

    /** One net's data, sent once even when several screens point at the same net. */
    public static final class NetData {
        public final int id;
        public final String name;
        public final int eventMask;
        public final String keyName;
        public final List<MonitorDevice> devices;

        public NetData(int id, String name, int eventMask, String keyName,
                       List<MonitorDevice> devices) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.eventMask = eventMask;
            this.keyName = keyName == null ? "" : keyName;
            this.devices = Collections.unmodifiableList(new ArrayList<>(devices));
        }
    }

    private MonitorDisplay() {
    }
}
