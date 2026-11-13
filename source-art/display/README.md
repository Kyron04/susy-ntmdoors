# Monitor display wallpapers

Drop the source images for the cosmetic monitor Display module here. The build copies
`*.png`, `*.jpg` and `*.jpeg` from this directory into
`assets/ntmdoors/textures/display/`; spaces are replaced with underscores and names are
lowercased.

The built-in display grid expects these file names:

| File | Display label |
|---|---|
| `supersymmetry.png` | SUPERSYMMETRY |
| `black_mesa.png` | BLACK MESA |
| `goog.png` | GOOG... |
| `world_map.png` | WORLD MAP |
| `windows_error.png` | WINDOWS ERROR |

The five source images are included. Only packaged images are supported; the monitor does not
download custom URLs. Keep the originals reasonably small because they are packaged directly into
the mod jar.
