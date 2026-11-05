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

The five source images are included. Custom URL images selected in-game do not go here;
they are downloaded by each client.

Keep the originals reasonably small (the client rejects downloads over 8 MB and images larger
than 4096 pixels on either axis). PNG or JPG is supported; animated GIFs and video are not.
