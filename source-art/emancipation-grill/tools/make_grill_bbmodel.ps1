# Builds the Blockbench project for one wall-mounted emancipation grill emitter.
# Model space: 16 wide, 48 tall (3 blocks), 16 deep. Front = north, wall plate = south.
param(
    [string]$TextureDir = (Join-Path $PSScriptRoot '..\textures'),
    [string]$OutPath = (Join-Path $PSScriptRoot '..\emancipation_grill.bbmodel')
)

$textureDefs = @(
    @('housing', 'grill_housing.png'),
    @('plate', 'grill_plate.png'),
    @('trim', 'grill_trim.png'),
    @('emitter', 'grill_emitter.png'),
    @('dark', 'grill_dark.png'),
    @('screw', 'grill_screw.png'),
    @('label', 'grill_label.png')
)

$textureIndex = @{}
$textures = @()
for ($i = 0; $i -lt $textureDefs.Count; $i++) {
    $key = $textureDefs[$i][0]
    $file = $textureDefs[$i][1]
    $textureIndex[$key] = $i
    $bytes = [System.IO.File]::ReadAllBytes((Join-Path $TextureDir $file))
    $dataUrl = 'data:image/png;base64,' + [Convert]::ToBase64String($bytes)
    $textures += [ordered]@{
        path                = ''
        name                = $file
        folder              = 'block'
        namespace           = ''
        id                  = [string]$i
        group               = ''
        width               = 16
        height              = 16
        uv_width            = 16
        uv_height           = 16
        particle            = ($key -eq 'housing')
        use_as_default      = ($key -eq 'housing')
        layers_enabled      = $false
        sync_to_project     = ''
        render_mode         = 'default'
        render_sides        = 'auto'
        frame_time          = 1
        frame_order_type    = 'loop'
        frame_order         = ''
        frame_interpolate   = $false
        visible             = $true
        internal            = $true
        saved               = $false
        uuid                = [guid]::NewGuid().ToString()
        relative_path       = ''
        source              = $dataUrl
    }
}

$elementDefs = @(
    # wall plate, one continuous panel
    @{ name = 'wall_plate';        from = @(1, 0.5, 14.4);   to = @(15, 47.5, 16);    tex = 'plate' },

    # main armour, one continuous shell
    @{ name = 'housing';           from = @(2, 1.5, 10);     to = @(14, 45.5, 15);    tex = 'housing' },

    # continuous emitter stack on the inner (right) edge
    @{ name = 'emitter_bezel';     from = @(9.6, 2, 8.75);   to = @(14, 46, 9.95);     tex = 'trim' },
    @{ name = 'emitter_panel';     from = @(10.6, 2.5, 8.48);to = @(13.4, 45.5, 8.78);  tex = 'dark' },
    @{ name = 'emitter_strip';     from = @(11.5, 3, 8.25);  to = @(12.5, 45, 8.55);   tex = 'emitter'; uv = 'full'; east = 'dark'; west = 'dark'; up = 'dark'; down = 'dark' },
    @{ name = 'emitter_top_cap';   from = @(9.4, 45.43, 8.6);to = @(14.2, 46.6, 10.02); tex = 'trim' },
    @{ name = 'emitter_bottom_cap';from = @(9.4, 1.4, 8.6);  to = @(14.2, 2.57, 10.02); tex = 'trim' },

    # top and bottom caps
    @{ name = 'top_cap_lower';     from = @(1.5, 45.45, 8.5);to = @(14.5, 46.8, 14.98); tex = 'housing' },
    @{ name = 'top_cap_upper';     from = @(2.5, 46.75, 9.2);to = @(13.5, 47.8, 14.96); tex = 'housing' },
    @{ name = 'top_cap_edge';      from = @(1.52, 46.78, 8.48);to = @(14.48, 47.18, 8.98);tex = 'trim' },
    @{ name = 'bottom_cap_lower';  from = @(1.5, 0.2, 8.5);  to = @(14.5, 1.55, 14.98);  tex = 'housing' },
    @{ name = 'bottom_cap_upper';  from = @(2.5, 1.45, 9.2); to = @(13.5, 2.2, 14.96);   tex = 'housing' },

    # warning sign (supplied 16x16 art)
    @{ name = 'warning_label';     from = @(4.3, 39, 9.9);   to = @(7.3, 42, 10.05);   tex = 'dark'; north = 'label' }
)

$elements = @()
$outliner = @()
$faceNames = @('north', 'east', 'south', 'west', 'up', 'down')

foreach ($def in $elementDefs) {
    $from = @([double]$def.from[0], [double]$def.from[1], [double]$def.from[2])
    $to = @([double]$def.to[0], [double]$def.to[1], [double]$def.to[2])
    $dx = $to[0] - $from[0]
    $dy = $to[1] - $from[1]
    $dz = $to[2] - $from[2]

    $faces = [ordered]@{}
    foreach ($faceName in $faceNames) {
        $texKey = $def[$faceName]
        if ($null -eq $texKey) { $texKey = $def.tex }

        $w = 0.0
        $h = 0.0
        if ($faceName -eq 'north' -or $faceName -eq 'south') { $w = $dx; $h = $dy }
        elseif ($faceName -eq 'east' -or $faceName -eq 'west') { $w = $dz; $h = $dy }
        else { $w = $dx; $h = $dz }

        $uv = @(0.0, 0.0, 16.0, 16.0)
        if (-not ($def.uv -eq 'full' -and ($faceName -eq 'north' -or $faceName -eq 'south'))) {
            $maxDim = [Math]::Max($w, $h)
            if ($maxDim -gt 0) {
                $uvScale = 16.0 / $maxDim
                $uv = @(0.0, 0.0, ($w * $uvScale), ($h * $uvScale))
            }
        }

        $faces[$faceName] = [ordered]@{
            uv      = $uv
            texture = $textureIndex[$texKey]
        }
    }
    $origin = @(
        (($from[0] + $to[0]) / 2.0),
        (($from[1] + $to[1]) / 2.0),
        (($from[2] + $to[2]) / 2.0)
    )
    $uuid = [guid]::NewGuid().ToString()

    $elements += [ordered]@{
        name                    = $def.name
        box_uv                  = $false
        rescale                 = $false
        locked                  = $false
        light_emission          = 0
        render_order            = 'default'
        allow_mirror_modeling   = $true
        from                    = $from
        to                      = $to
        autouv                  = 0
        color                   = 0
        origin                  = $origin
        rotation                = @(0.0, 0.0, 0.0)
        faces                   = $faces
        type                    = 'cube'
        uuid                    = $uuid
    }
    $outliner += $uuid
}

$project = [ordered]@{
    meta                        = [ordered]@{
        format_version          = '5.0'
        model_format            = 'free'
        box_uv                  = $false
    }
    name                        = 'emancipation_grill'
    model_identifier            = ''
    visible_box                 = @(1, 1, 0)
    variable_placeholders       = ''
    variable_placeholder_buttons = @()
    resolution                  = [ordered]@{
        width                   = 16
        height                  = 16
    }
    elements                    = $elements
    groups                      = @()
    outliner                    = $outliner
    textures                    = $textures
}

$json = $project | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($OutPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output $OutPath
Write-Output ("elements: {0}  textures: {1}  bytes: {2}" -f $elements.Count, $textures.Count, (Get-Item $OutPath).Length)
