# Office door texture sources

`64x128/` contains the original 64x128 office-door atlases used by the colour-variant masks.

The runtime textures in `src/main/resources/assets/ntmdoors/textures/models/doors/` are
downscaled to **32x64**. They are the ones packed into the jar.

If you regenerate colour variants, work from the 64x128 files in this folder. The
mask coordinates are expressed in the original 64x128 pixel space; running the same masks
against the 32x64 runtime textures would recolour the wrong areas.

The 3x3 double-door OBJ files are exact 1.5x scaled copies of the 2x2 double-door models.
