# NTM Doors (Minecraft 1.20.1 / Fabric)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-blue.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-orange.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-17-green.svg)](https://adoptium.net/)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)

A standalone port of the iconic multiblock doors and high-tech security apparatus from **HBM's Nuclear Tech Mod** to **Minecraft 1.20.1** on the **Fabric Loader**.

Originally built for 1.12.2 Forge, this version is a complete ground-up rewrite for modern Fabric, featuring a pure Fabric OBJ software-clipping renderer, full animation collision tracking, a complete security and terminal network system, and 104 balanced vanilla crafting recipes for survival mode.

---

## Requirements
- **Minecraft**: `1.20.1`
- **Fabric Loader**: `>= 0.15.11`
- **Fabric API**: Required
- **Java**: `17+`

---

## Features

### 🚪 Multiblock Doors (22 Types / 66 Blocks)
- **One-Click Placement & Removal**: Places the full multiblock structure in one click oriented to the player's facing, and dismantles atomically.
- **Continuous Collision Boxes**: State-dependent collision boxes dynamically follow opening and closing animations. Walk through open doors and click through gaps without phantom barriers.
- **Three Tiers for Every Door**:
  - **Normal**: Opens via hand right-click or redstone signals.
  - **Locked**: Refuses direct hand clicks; requires a Keypad, Keycard Scanner, or remote pulse.
  - **Security**: Heavily reinforced, explosion-resistant door owned by the player who placed it. Refuses standard redstone unless explicitly enabled by the owner via the Redstone Monocle.
- **Included Door Types**:
  - *Vault Door, Sliding Blast Door, QE Sliding Door, QE Sliding Panel, Fire Door, Secure Access Door, Sliding Seal Door, Round Airlock Door, Silo Hatch (Standard & Large), Cargo Door, Large Vehicle Door, QE Containment Door, Roller Blast Door, Water Door, Transition Seal, Blast Shutter, Black Mesa Door (Office 1 & Double), Office Door 2 (& Double), and Test Door.*

### 🛡️ Security Apparatus
- **Keypads**:
  - Wall-mounted code panel with 1–6 digit security codes.
  - Set or reset codes using the Screwdriver.
  - Supports distance binding and multi-door clustering.
  - Wireless RF linking to Redstone Gates via the Redstone Relay Key.
- **Keycard Scanners**:
  - Configurable access clearance (clearance levels, assigned serials, and biometric IDs).
  - 10-second auto-close dispatch for bound doors upon granted access.
  - Lockout protection on repeated failed attempts.
- **Keycard Programmer**:
  - Interactive terminal GUI for naming cards, viewing cryptographic keys, configuring clearance levels, and backing up keys.
- **Safes & Biometric Safes**:
  - Blast-proof 1x1x1 steel storage blocks with keypad/biometric authentication and wireless gate linking.
- **Wireless Redstone Gates**:
  - Configurable wireless activation window (from 1 second up to 5 minutes).
  - Normal, inverted, and `stayActive` latching modes.
  - Built-in wire settling guards to eliminate redstone feedback loops.
- **Speakers & Motion Sensors**:
  - Speakers with 32-block spatial listener attenuation and 4 facility alert tones.
  - Motion Sensors with configurable bounding box detection (Presence / Vacancy modes).

### 💳 Keycards & Tools
- **RFID Keycards**: Blank cards, 7 dyed color variants, and numbered Access Keycards (Levels 1–12).
- **Biometric Keycards**: Keycards bound to a player's UUID and biometrics.
- **Relay Keys**: Special RF tools for wireless linking (Redstone, Terminal, Safe, Motion, Weapons, and Biometric).
- **Keycard Holder & Documents Folder**: Handheld storage items for organizing cards and writing multi-page security documentation.
- **Screwdriver**: Used to configure keypads, cycle door skins, and initiate reset sequences.
- **Redstone Monocle**: Visualizes loaded wireless links through walls and allows owners to toggle security door redstone access.

### 🖥️ Terminal OS & Monitor Desks
- Modular desk setups ranging from **Single Monitor** up to **6-Monitor Desks**.
- Terminal features:
  - First-time master password setup with cryptographic hashing.
  - **Security Net**: Group devices into named networks, view live device power states, and toggle systems remotely.
  - **Notepad**: Shared desk documentation with real-time text editing mirrored onto physical monitor screens.
  - **Wallpaper Customizer**: Customize physical monitor desktop wallpapers from built-in presets or custom resource paths.

### 💥 Breaching & Countermeasures
- **Thermite Charge**: Mounts flush to any door surface to burn through locks and breach secured areas.
- **Spatial Disrupter**: Defense countermeasure that deflects and neutralizes breaches.

### 🛠️ Vanilla Crafting Recipes
- 104 standard vanilla crafting recipes added in `data/ntmdoors/recipes/`, allowing full progression in survival mode using iron, copper, redstone, quartz, glass, and pistons without external mod dependencies.
- Tiered door progression:
  - `Base Door + Keypad` $\to$ `Locked Door`
  - `Locked Door + Obsidian` $\to$ `Security Door`

---

## Building from Source

Ensure you have **JDK 17** installed:

```bash
# Clone the repository
git clone https://github.com/Kyron04/susy-ntmdoors.git
cd susy-ntmdoors

# Switch to the Fabric 1.20.1 branch
git checkout fabric-1.20.1

# Run tests
./gradlew test

# Build the mod JAR
./gradlew build
```

The compiled mod JAR will be placed in `build/libs/ntmdoors-1.0.0.jar`.

---

## License & Credits

- **Original Door System & Assets**: Based on [HBM's Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT) by **Hbm** and NTM Contributors.
- **1.12.2 Port**: Developed by **Kyron04** (`susy-ntmdoors`).
- **Fabric 1.20.1 Port & Rewrite**: Developed by **Jakub Lukeš** (`KubLuk24`).
- **License**: Licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**. See [`LICENSE.LESSER`](LICENSE.LESSER) and [`NOTICE`](NOTICE) for details.
