Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot '..'
$repoRoot = Join-Path $root '..\..'
if (-not (Test-Path (Join-Path $repoRoot 'src\main\resources'))) {
    throw "Could not find the repository root at $repoRoot"
}
$mod = Join-Path $repoRoot 'src\main\resources\assets\ntmdoors'
$bbPath = Join-Path $root 'emancipation_grill.bbmodel'
$bb = Get-Content -Raw $bbPath | ConvertFrom-Json

$nameToKey = @{
    'grill_housing.png' = 'housing'
    'grill_plate.png'   = 'plate'
    'grill_trim.png'    = 'trim'
    'grill_emitter.png' = 'emitter'
    'grill_dark.png'    = 'dark'
    'grill_screw.png'   = 'screw'
    'grill_label.png'   = 'label'
}
$indexToKey = @()
foreach ($texture in $bb.textures) { $indexToKey += $nameToKey[$texture.name] }

$textures = [ordered]@{
    particle = 'ntmdoors:blocks/emancipation_grill_housing'
    housing  = 'ntmdoors:blocks/emancipation_grill_housing'
    plate    = 'ntmdoors:blocks/emancipation_grill_plate'
    trim     = 'ntmdoors:blocks/emancipation_grill_trim'
    emitter  = 'ntmdoors:blocks/emancipation_grill_emitter'
    dark     = 'ntmdoors:blocks/emancipation_grill_dark'
    screw    = 'ntmdoors:blocks/emancipation_grill_screw'
    label    = 'ntmdoors:blocks/emancipation_grill_label'
}

$faceMap = @{
    north = 'north'
    south = 'south'
    east  = 'down'
    west  = 'up'
    up    = 'east'
    down  = 'west'
}

$segments = @(
    [pscustomobject]@{ Name = 'left';   Base = 0.0;  Model = 'emancipation_grill_horizontal_left';   File = 'emancipation_grill_horizontal_left' },
    [pscustomobject]@{ Name = 'middle'; Base = 16.0; Model = 'emancipation_grill_horizontal';        File = 'emancipation_grill_horizontal' },
    [pscustomobject]@{ Name = 'right';  Base = 32.0; Model = 'emancipation_grill_horizontal_right';  File = 'emancipation_grill_horizontal_right' }
)
$elements = @{}
foreach ($segment in $segments) {
    $elements[$segment.Name] = New-Object System.Collections.ArrayList
}

function Round4([double]$v) { return [Math]::Round($v, 4) }

foreach ($el in $bb.elements) {
    # Rotate the 16x48x16 column 90 degrees around Z. The three vertical layers become
    # the three horizontal cells: bottom -> left, middle -> centre, top -> right.
    $x1 = [double]$el.from[1]
    $x2 = [double]$el.to[1]
    $y1 = 16.0 - [double]$el.to[0]
    $y2 = 16.0 - [double]$el.from[0]
    $z1 = [double]$el.from[2]
    $z2 = [double]$el.to[2]

    foreach ($segment in $segments) {
        $base = [double]$segment.Base
        $top = $base + 16.0
        $xa = [Math]::Max($x1, $base)
        $xb = [Math]::Min($x2, $top)
        if ($xb -le $xa) { continue }

        $faces = [ordered]@{}
        foreach ($sourceFace in @('north', 'east', 'south', 'west', 'up', 'down')) {
            $prop = $el.faces.PSObject.Properties[$sourceFace]
            if ($null -eq $prop) { continue }
            $face = $prop.Value
            $newName = $faceMap[$sourceFace]

            $u1 = [double]$face.uv[0]
            $v1 = [double]$face.uv[1]
            $u2 = [double]$face.uv[2]
            $v2 = [double]$face.uv[3]

            if ($newName -eq 'east' -or $newName -eq 'west') {
                # A side cap exists only where the original element actually ends.
                $plane = if ($newName -eq 'east') { $x2 } else { $x1 }
                if ($plane -lt $base -or $plane -gt $top) { continue }
            } else {
                # Faces whose texture U axis is now X: slice the U range with the cell.
                $width = $x2 - $x1
                if ($width -gt 0) {
                    $t0 = ($xa - $x1) / $width
                    $t1 = ($xb - $x1) / $width
                    $u1 = [double]$face.uv[0] + $t0 * ([double]$face.uv[2] - [double]$face.uv[0])
                    $u2 = [double]$face.uv[0] + $t1 * ([double]$face.uv[2] - [double]$face.uv[0])
                }
            }

            $key = $indexToKey[[int]$face.texture]
            $faces[$newName] = [ordered]@{
                uv      = @((Round4 $u1), (Round4 $v1), (Round4 $u2), (Round4 $v2))
                texture = "#$key"
            }
        }

        if ($faces.Count -eq 0) { continue }
        [void]$elements[$segment.Name].Add([ordered]@{
            from  = @((Round4 ($xa - $base)), (Round4 $y1), (Round4 $z1))
            to    = @((Round4 ($xb - $base)), (Round4 $y2), (Round4 $z2))
            faces = $faces
        })
    }
}

$blockModels = Join-Path $mod 'models\block'
$blockstates = Join-Path $mod 'blockstates'
$itemModels = Join-Path $mod 'models\item'
$itemTex = Join-Path $mod 'textures\items'
New-Item -ItemType Directory -Force -Path $blockModels, $blockstates, $itemModels, $itemTex | Out-Null

foreach ($segment in $segments) {
    $model = [ordered]@{
        parent    = 'block/block'
        gui_light = 'front'
        textures  = $textures
        elements  = $elements[$segment.Name]
    }
    $json = $model | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText(
        (Join-Path $blockModels ($segment.Model + '.json')),
        $json,
        (New-Object System.Text.UTF8Encoding($false))
    )

    $variants = @()
    foreach ($facing in @(@('north', 0), @('east', 90), @('south', 180), @('west', 270))) {
        foreach ($mirror in @('false', 'true')) {
            $rotation = if ([int]$facing[1] -eq 0) { '' } else { ", `"y`": $($facing[1])" }
            $variants += "  `"facing=$($facing[0]),mirrored=$mirror`": { `"model`": `"ntmdoors:$($segment.Model)`"$rotation },"
        }
    }
    $variants[$variants.Count - 1] = $variants[$variants.Count - 1].TrimEnd(',')
    $state = "{`r`n  `"variants`": {`r`n$($variants -join "`r`n")`r`n  }`r`n}`r`n"
    [System.IO.File]::WriteAllText(
        (Join-Path $blockstates ($segment.File + '.json')),
        $state,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

$itemModel = @'
{
  "parent": "item/generated",
  "textures": {
    "layer0": "ntmdoors:items/emancipation_grill_horizontal"
  }
}
'@
[System.IO.File]::WriteAllText((Join-Path $itemModels 'emancipation_grill_horizontal.json'),
        $itemModel, (New-Object System.Text.UTF8Encoding($false)))

$sourceIcon = Join-Path $mod 'textures\items\emancipation_grill.png'
$icon = [System.Drawing.Image]::FromFile($sourceIcon)
$icon.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone)
$icon.Save((Join-Path $itemTex 'emancipation_grill_horizontal.png'),
        [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Dispose()

Write-Output 'Generated horizontal grill assets:'
Get-ChildItem $blockModels -Filter 'emancipation_grill_horizontal*.json' | Select-Object Name, Length
Get-ChildItem $blockstates -Filter 'emancipation_grill_horizontal*.json' | Select-Object Name, Length
Get-ChildItem $itemModels -Filter 'emancipation_grill_horizontal*.json' | Select-Object Name, Length
Get-ChildItem $itemTex -Filter 'emancipation_grill_horizontal*.png' | Select-Object Name, Length
