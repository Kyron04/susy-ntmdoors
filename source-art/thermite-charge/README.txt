ntmdoors:thermite_charge - source materials
============================================

CONTENTS
--------
thermite-charge-preview.png
    3D preview render of the placed charge.

audio\short-beep-countdown.mp3, audio\bomb-explosion.mp3
    Supplied source audio for the fuse countdown and detonation.

audio\thermite_beep.ogg, audio\thermite_explosion.ogg
    Mono Ogg Vorbis conversions shipped under assets/ntmdoors/sounds/thermite/.

The active block/item models and textures are packaged under
src/main/resources/assets/ntmdoors/. This folder keeps only the source
preview and the supplied audio; the copied model and texture archives were
removed to avoid duplicating the live assets.

The blockstate uses the base thermite_charge_mounted model plus sixteen
generated thermite_charge_mounted_0..15 variants that shift it toward the
clicked door hitbox. The shipped item form uses the flat 16x16 sprite.
