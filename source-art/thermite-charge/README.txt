ntmdoors:thermite_charge - 3D Extrapolated Thermite Breaching Charge
====================================================================

CONTENTS
--------
assets\ntmdoors\models\block\thermite_charge.json
    31-element 3D Minecraft block model extrapolated 1:1 from the 16x16 2D texture.

assets\ntmdoors\models\block\thermite_charge_mounted.json
    The same model translated +6.824 px on Z so its back sits flush against the south face of
    its cell. The blockstate rotates this variant onto whichever face the charge is clicked on.

    The shipped blockstate also uses 16 generated variants, thermite_charge_mounted_0..15, which
    shift this model another 0-15 px toward the clicked door hitbox. Only the base mounted model
    is kept in source-art; the numbered variants are generated from it.

assets\ntmdoors\models\item\thermite_charge.json
    3D item model with GUI/hand display transforms. Kept for reference only: the shipped item
    form uses thermite_charge.png as a flat generated sprite.

assets\ntmdoors\textures\blocks\*.png
    Four 16x16 textures:
      thermite_charge (original 2D front sprite mapped 1:1 onto all front faces)
      thermite_frame  (outer steel mounting rim)
      thermite_casing (dark charcoal chassis, corner clamps, and igniter housing)
      thermite_pack   (crimson-red thermite charge bricks)

thermite-charge-preview.png
    3D preview render.

audio\short-beep-countdown.mp3, audio\bomb-explosion.mp3
    Supplied source audio for the fuse countdown and detonation.

audio\thermite_beep.ogg, audio\thermite_explosion.ogg
    Mono Ogg Vorbis conversions shipped under assets/ntmdoors/sounds/thermite/.