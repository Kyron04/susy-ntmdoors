Add-Type -AssemblyName System.Drawing

$modelPath = Join-Path $PSScriptRoot '..\emancipation_grill.bbmodel'
$outPath = Join-Path $PSScriptRoot '..\previews\emancipation_grill_preview.png'
$script:texRoot = Join-Path $PSScriptRoot '..\textures'
$script:texCache = @{}
$script:avgCache = @{}

function VSub([double[]]$a, [double[]]$b) {
    return [double[]]@(
        ($a[0] - $b[0]),
        ($a[1] - $b[1]),
        ($a[2] - $b[2])
    )
}
function VAdd([double[]]$a, [double[]]$b) {
    return [double[]]@(
        ($a[0] + $b[0]),
        ($a[1] + $b[1]),
        ($a[2] + $b[2])
    )
}
function VScale([double[]]$a, [double]$s) {
    return [double[]]@(
        ($a[0] * $s),
        ($a[1] * $s),
        ($a[2] * $s)
    )
}
function Dot([double[]]$a, [double[]]$b) { return ($a[0] * $b[0] + $a[1] * $b[1] + $a[2] * $b[2]) }
function Cross([double[]]$a, [double[]]$b) {
    return [double[]]@(
        ($a[1] * $b[2] - $a[2] * $b[1]),
        ($a[2] * $b[0] - $a[0] * $b[2]),
        ($a[0] * $b[1] - $a[1] * $b[0])
    )
}
function Normalize([double[]]$a) {
    $len = [Math]::Sqrt($a[0] * $a[0] + $a[1] * $a[1] + $a[2] * $a[2])
    if ($len -le 0.00001) { return [double[]]@(0, 0, 0) }
    return [double[]]@(
        ($a[0] / $len),
        ($a[1] / $len),
        ($a[2] / $len)
    )
}
function Rotate-Point([double[]]$p, [double[]]$origin, [string]$axis, [double]$angleDeg) {
    $t = $angleDeg * [Math]::PI / 180.0
    $c = [Math]::Cos($t)
    $s = [Math]::Sin($t)
    $x = $p[0] - $origin[0]
    $y = $p[1] - $origin[1]
    $z = $p[2] - $origin[2]
    $nx = $x; $ny = $y; $nz = $z
    if ($axis -eq 'x') {
        $ny = $y * $c - $z * $s
        $nz = $y * $s + $z * $c
    } elseif ($axis -eq 'y') {
        $nx = $x * $c + $z * $s
        $nz = -$x * $s + $z * $c
    } elseif ($axis -eq 'z') {
        $nx = $x * $c - $y * $s
        $ny = $x * $s + $y * $c
    }
    return [double[]]@(
        ($nx + $origin[0]),
        ($ny + $origin[1]),
        ($nz + $origin[2])
    )
}

$model = Get-Content -Raw $modelPath | ConvertFrom-Json
$script:textureNames = @($model.textures | ForEach-Object { $_.name })
$light = Normalize ([double[]]@(-0.45, 0.85, -0.35))

function Get-Texture([int]$idx) {
    if (-not $script:texCache.ContainsKey($idx)) {
        $path = Join-Path $script:texRoot $script:textureNames[$idx]
        $script:texCache[$idx] = [System.Drawing.Bitmap]::FromFile($path)
    }
    return $script:texCache[$idx]
}
function Get-AverageColor([int]$idx) {
    if (-not $script:avgCache.ContainsKey($idx)) {
        $bmp = Get-Texture $idx
        [int64]$r = 0; [int64]$g = 0; [int64]$b = 0
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $c = $bmp.GetPixel($x, $y)
                $r += $c.R; $g += $c.G; $b += $c.B
            }
        }
        $count = $bmp.Width * $bmp.Height
        $script:avgCache[$idx] = [System.Drawing.Color]::FromArgb(255, [int]($r / $count), [int]($g / $count), [int]($b / $count))
    }
    return $script:avgCache[$idx]
}

$script:faceDefs = @{
    north = @(2, 3, 1, 0)
    south = @(7, 6, 4, 5)
    east  = @(3, 7, 5, 1)
    west  = @(6, 2, 0, 4)
    up    = @(6, 7, 3, 2)
    down  = @(0, 1, 5, 4)
}

function Project-Point([double[]]$p, [double[]]$target, [double[]]$right, [double[]]$up, [double]$scale, [double]$cx, [double]$cy) {
    $d = VSub $p $target
    $u = Dot $d $right
    $v = Dot $d $up
    return [System.Drawing.PointF]::new([float]($cx + $u * $scale), [float]($cy - $v * $scale))
}

function Render-Model($g, $model, [double[]]$eye, [double[]]$target, [double]$scale, [double]$cx, [double]$cy, [bool]$drawCage) {
    $f = Normalize (VSub $target $eye)
    $worldUp = [double[]]@(0, 1, 0)
    $right = Normalize (Cross $f $worldUp)
    $up = Cross $right $f

    $drawList = New-Object System.Collections.ArrayList

    foreach ($el in $model.elements) {
        $x1 = [double]$el.from[0]; $y1 = [double]$el.from[1]; $z1 = [double]$el.from[2]
        $x2 = [double]$el.to[0];   $y2 = [double]$el.to[1];   $z2 = [double]$el.to[2]

        $corners = [double[][]]@(
            [double[]]@($x1, $y1, $z1),
            [double[]]@($x2, $y1, $z1),
            [double[]]@($x1, $y2, $z1),
            [double[]]@($x2, $y2, $z1),
            [double[]]@($x1, $y1, $z2),
            [double[]]@($x2, $y1, $z2),
            [double[]]@($x1, $y2, $z2),
            [double[]]@($x2, $y2, $z2)
        )

        if ($null -ne $el.rotation) {
            $rx = [double]$el.rotation[0]
            $ry = [double]$el.rotation[1]
            $rz = [double]$el.rotation[2]
            if (($rx -ne 0) -or ($ry -ne 0) -or ($rz -ne 0)) {
                $origin = [double[]]@([double]$el.origin[0], [double]$el.origin[1], [double]$el.origin[2])
                for ($i = 0; $i -lt 8; $i++) {
                    if ($rx -ne 0) { $corners[$i] = Rotate-Point $corners[$i] $origin 'x' $rx }
                    if ($ry -ne 0) { $corners[$i] = Rotate-Point $corners[$i] $origin 'y' $ry }
                    if ($rz -ne 0) { $corners[$i] = Rotate-Point $corners[$i] $origin 'z' $rz }
                }
            }
        }

        foreach ($fname in @('north', 'east', 'south', 'west', 'up', 'down')) {
            $prop = $el.faces.PSObject.Properties[$fname]
            if ($null -eq $prop) { continue }
            $face = $prop.Value
            $idx = $script:faceDefs[$fname]
            $p0 = $corners[$idx[0]]
            $p1 = $corners[$idx[1]]
            $p2 = $corners[$idx[2]]
            $p3 = $corners[$idx[3]]

            $normal = Normalize (Cross (VSub $p1 $p0) (VSub $p3 $p0))
            $facing = Dot $normal (VScale $f -1)
            if ($facing -le 0.0001) { continue }

            $texIndex = [int]$face.texture
            $center = [double[]]@(
                (($p0[0] + $p1[0] + $p2[0] + $p3[0]) / 4.0),
                (($p0[1] + $p1[1] + $p2[1] + $p3[1]) / 4.0),
                (($p0[2] + $p1[2] + $p2[2] + $p3[2]) / 4.0)
            )
            $depth = Dot (VSub $center $target) $f

            $layer = 0
            if ($el.name -like 'emitter_bezel*') { $layer = 1 }
            elseif ($el.name -like 'emitter_panel*') { $layer = 2 }
            elseif ($el.name -like 'emitter_strip*') { $layer = 3 }
            elseif ($el.name -like 'emitter_top_node*' -or $el.name -like 'emitter_bottom_node*') { $layer = 4 }
            elseif ($el.name -like 'emitter_top_cap*' -or $el.name -like 'emitter_bottom_cap*') { $layer = 2 }

            [void]$drawList.Add([pscustomobject]@{
                Depth = $depth
                Layer = $layer
                P0 = $p0; P1 = $p1; P2 = $p2; P3 = $p3
                N = $normal
                Tex = $texIndex
                Uv = @([double]$face.uv[0], [double]$face.uv[1], [double]$face.uv[2], [double]$face.uv[3])
            })
        }
    }

    $outlinePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(38, 8, 10, 12), 1.0)
    $sorted = $drawList | Sort-Object @{Expression = 'Layer'; Ascending = $true}, @{Expression = 'Depth'; Descending = $true}

    foreach ($rec in $sorted) {
        $sp0 = Project-Point $rec.P0 $target $right $up $scale $cx $cy
        $sp1 = Project-Point $rec.P1 $target $right $up $scale $cx $cy
        $sp2 = Project-Point $rec.P2 $target $right $up $scale $cx $cy
        $sp3 = Project-Point $rec.P3 $target $right $up $scale $cx $cy
        $pts = [System.Drawing.PointF[]]@($sp0, $sp1, $sp2, $sp3)

        $bmp = Get-Texture $rec.Tex
        $avg = Get-AverageColor $rec.Tex
        $fillBrush = New-Object System.Drawing.SolidBrush $avg
        $g.FillPolygon($fillBrush, $pts)
        $fillBrush.Dispose()

        $dst = [System.Drawing.Point[]]@(
            [System.Drawing.Point]::new([int][Math]::Round($sp0.X), [int][Math]::Round($sp0.Y)),
            [System.Drawing.Point]::new([int][Math]::Round($sp1.X), [int][Math]::Round($sp1.Y)),
            [System.Drawing.Point]::new([int][Math]::Round($sp3.X), [int][Math]::Round($sp3.Y))
        )
        try {
            $u1 = [int][Math]::Round($rec.Uv[0])
            $v1 = [int][Math]::Round($rec.Uv[1])
            $u2 = [int][Math]::Round($rec.Uv[2])
            $v2 = [int][Math]::Round($rec.Uv[3])
            $sw = [Math]::Max(1, $u2 - $u1)
            $sh = [Math]::Max(1, $v2 - $v1)
            $srcRect = New-Object System.Drawing.Rectangle $u1, $v1, $sw, $sh
            $g.DrawImage($bmp, $dst, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
        } catch {
            try { $g.DrawImage($bmp, $dst) } catch { }
        }

        $brightness = 0.66 + 0.34 * [Math]::Max(0.0, (Dot $rec.N $light))
        $alpha = [int]((1.0 - $brightness) * 180.0)
        if ($alpha -gt 0) {
            $shadeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($alpha, 8, 10, 14))
            $g.FillPolygon($shadeBrush, $pts)
            $shadeBrush.Dispose()
        }
        $g.DrawPolygon($outlinePen, $pts)
    }

    if ($drawCage) {
        $cagePts = @(
            [double[]]@(0, 0, 0), [double[]]@(16, 0, 0), [double[]]@(16, 0, 16), [double[]]@(0, 0, 16),
            [double[]]@(0, 48, 0), [double[]]@(16, 48, 0), [double[]]@(16, 48, 16), [double[]]@(0, 48, 16)
        )
        $proj = @()
        foreach ($cp in $cagePts) { $proj += (Project-Point $cp $target $right $up $scale $cx $cy) }
        $cagePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(105, 70, 196, 235), 1.2)
        $edges = @(
            @(0, 1), @(1, 2), @(2, 3), @(3, 0),
            @(4, 5), @(5, 6), @(6, 7), @(7, 4),
            @(0, 4), @(1, 5), @(2, 6), @(3, 7)
        )
        foreach ($e in $edges) {
            $g.DrawLine($cagePen, $proj[$e[0]], $proj[$e[1]])
        }
        $cagePen.Dispose()
    }
    $outlinePen.Dispose()
}

# ------------------------------------------------------------------ sheet
$W = 900
$H = 1500
$S = 2
$sheet = New-Object System.Drawing.Bitmap ($W * $S), ($H * $S)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.Color]::FromArgb(255, 24, 28, 34))

function Px([double]$v) { return [int]([Math]::Round($v * $S)) }

$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 240, 243, 247))
$muted = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 158, 169, 182))
$cyanText = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 104, 211, 238))
$dim = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 108, 119, 134))

$titleFont = New-Object System.Drawing.Font('Segoe UI', (25 * $S), [System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font('Segoe UI', (12.5 * $S), [System.Drawing.FontStyle]::Regular)
$labelFont = New-Object System.Drawing.Font('Segoe UI', (10.5 * $S), [System.Drawing.FontStyle]::Bold)
$smallFont = New-Object System.Drawing.Font('Consolas', (9 * $S), [System.Drawing.FontStyle]::Regular)

$g.DrawString('Aperture Science Material Emancipation Grill', $titleFont, $white, (Px 32), (Px 24))
$g.DrawString('Wall-mounted emitter unit  |  blockout 01  |  1 x 3 x 1  |  16x16 textures', $subFont, $muted, (Px 34), (Px 66))

# subtle vertical backdrop
$panelRect = New-Object System.Drawing.Rectangle (Px 24), (Px 110), (Px 852), (Px 1260)
$panelBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $panelRect, ([System.Drawing.Color]::FromArgb(255, 42, 48, 58)), ([System.Drawing.Color]::FromArgb(255, 18, 22, 28)), 90
$g.FillRectangle($panelBrush, $panelRect)
$panelBrush.Dispose()
$borderPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(80, 84, 96, 112), 1.0)
$g.DrawRectangle($borderPen, $panelRect)

$g.SetClip($panelRect)
for ($gy = 110; $gy -lt 1370; $gy += 40) {
    $gridPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(12, 140, 160, 185), 1.0)
    $g.DrawLine($gridPen, (Px 24), (Px $gy), (Px 876), (Px $gy))
    $gridPen.Dispose()
}
for ($gx = 24; $gx -lt 876; $gx += 40) {
    $gridPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(12, 140, 160, 185), 1.0)
    $g.DrawLine($gridPen, (Px $gx), (Px 110), (Px $gx), (Px 1370))
    $gridPen.Dispose()
}

$target = [double[]]@(8, 24, 9)
$dir = Normalize ([double[]]@(-0.28, 0.18, -1.0))
$eye = VAdd $target (VScale $dir 70)
Render-Model $g $model $eye $target 30.0 (Px 450) (Px 760) $true
$g.ResetClip()

$g.DrawString('EMITTER FACE  /  NORTH', $labelFont, $cyanText, (Px 40), (Px 1390))
$g.DrawString('Cyan cage = 1 x 3 x 1 block volume', $smallFont, $muted, (Px 40), (Px 1414))
$g.DrawString('mirror one unit across a 3-block gap to form the grill', $smallFont, $dim, (Px 40), (Px 1438))
$g.DrawString('13 parts  |  wall plate, housing, emitter bezel/panel/strip, caps, warning sign', $smallFont, $dim, (Px 40), (Px 1462))

$g.Dispose()

$final = New-Object System.Drawing.Bitmap $W, $H
$fg = [System.Drawing.Graphics]::FromImage($final)
$fg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$fg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$fg.DrawImage($sheet, 0, 0, $W, $H)
$fg.Dispose()
$sheet.Dispose()

$final.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$final.Dispose()

foreach ($key in $script:texCache.Keys) { $script:texCache[$key].Dispose() }

Write-Output $outPath
