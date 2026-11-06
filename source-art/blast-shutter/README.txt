ntmdoors:blast_shutter - 3x3x1 heavy-duty vertical blast shutter
================================================================

CONTENTS
--------
assets\ntmdoors\models\block\blast_shutter.json
    Unsliced 48x48x16 blast shutter model (88 elements, tagged with name="frame" or "plate"). Front faces north.

assets\ntmdoors\models\block\blast_shutter_{bottom,middle,top}_{left,center,right}.json
    Nine sliced 16x16x16 block models for a 3x3x1 multi-block setup.

assets\ntmdoors\models\block\blast_shutter_item.json
    1/3-scale item model fitting inside a single 16x16x16 block.

assets\ntmdoors\textures\blocks\*.png
    Six 16x16 textures:
      shutter_plate, shutter_rib, shutter_post,
      shutter_piston, shutter_hazard, shutter_seal

blast-shutter-preview.png
    Assembled preview render.


DIMENSIONS
----------
Model space: x 0..48, y 0..48, z 5.5..10.5 (5px thick, centered in 0..16).
Fits a 3 blocks wide x 3 high x 1 deep (3x3x1) envelope.
Front faces north.