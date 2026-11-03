NTM Doors - Motion Sensor final model materials
Minecraft 1.12.2 / Forge 14.23.5.2847
Model version: 1.0.36
=================================================

CONTENTS
--------
assets\ntmdoors\blockstates\motion_sensor.json
    Six-way facing x four status variants (24 blockstates).

assets\ntmdoors\models\block\motion_sensor.json
    In-world model. Front faces north; the back plate sits at z=15..16.

assets\ntmdoors\models\block\motion_sensor_idle.json
assets\ntmdoors\models\block\motion_sensor_authorized.json
assets\ntmdoors\models\block\motion_sensor_maintenance.json
assets\ntmdoors\models\block\motion_sensor_motion.json
    Status-light children. Each parents the base model and overrides only the
    LED texture.

assets\ntmdoors\models\block\motion_sensor_item.json
    Centred copy of the model used only for the inventory/GUI icon.

assets\ntmdoors\models\item\motion_sensor.json
    Item model with display transforms; parents motion_sensor_item.json.

assets\ntmdoors\textures\blocks\sensor_*.png
    Nine 16x16 textures (listed below).

scripts\generate_motion_sensor_textures.ps1
    Rebuilds all nine 16x16 textures. Run with Windows PowerShell:
        powershell -ExecutionPolicy Bypass -File generate_motion_sensor_textures.ps1
    Output is written to ..\assets\ntmdoors\textures\blocks.

scripts\generate_motion_sensor_preview.ps1
    Rebuilds the four-panel model preview from the final textures.

motion-sensor-model-preview-final.png
    Canonical final model preview. Shows the 8x8x1.375 cage and all four status
    lights: IDLE green, AUTHORIZED blue, MAINTENANCE yellow, MOTION red.

motion-sensor-textures-16x16-final.png
    Labelled 3x3 texture sheet for quick reference.


DIMENSIONS
----------
Footprint: 8 x 8 texture pixels (x=4..12, y=4..12)
Depth:     1.375 texture pixels (front z=14.625, back z=16)
Front:     north in model space
Back plate: 1 pixel thick at z=15..16

The model is deliberately thin: the bezel and lens are pressed almost flat
against the mounting plate. The in-world hitbox uses the same 8 x 8 x 1.375
bounds.


TEXTURES (all 16x16)
--------------------
sensor_body.png         off-white ABS housing
sensor_plate.png        grey mounting plate
sensor_bezel.png        charcoal front bezel
sensor_lens.png         ringed concentric Fresnel lens
sensor_screw.png        screw heads
sensor_led_green.png    idle / no motion
sensor_led_blue.png     authorised personnel
sensor_led_yellow.png   maintenance
sensor_led_red.png      motion detected

The lens has a dark outline, a bevelled light metal ring and concentric
Fresnel rings with a filled centre. It has no central cross.


STATUS LIGHT
------------
The block state stores the six-way facing only. The four status values live on
the tile entity and are applied through getActualState, so all four light
textures work in every orientation:

    status=idle         green
    status=authorized   blue
    status=maintenance  yellow
    status=motion       red

For preview, creative right-click cycles the light. Detection logic is not
implemented yet.


INSTALLATION
------------
Copy the contents of assets\ into a Minecraft 1.12.2 resource pack, or merge
them into a mod's src\main\resources\assets\ntmdoors directory.


NOTES
-----
- All textures are 16x16 PNGs.
- The item model is centred separately so the thin housing does not appear
  off-centre in the GUI.
- The blockstate assumes the block has a "facing" property and a "status"
  property supplied by the tile entity actual state.
