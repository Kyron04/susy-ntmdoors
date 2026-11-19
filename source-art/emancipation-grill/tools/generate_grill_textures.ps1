Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot '..\textures'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Clamp([int]$v) {
    if ($v -lt 0) { return 0 }
    if ($v -gt 255) { return 255 }
    return $v
}

function Set-Px($bmp, [int]$x, [int]$y, [int]$r, [int]$g, [int]$b) {
    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp $r), (Clamp $g), (Clamp $b)))
}

function Hash-Noise([int]$x, [int]$y, [int]$seed, [int]$amp) {
    [int64]$n = ([int64]$x * 374761393 + [int64]$y * 668265263 + [int64]$seed * 1442695041) -band 0x7fffffff
    $n = (($n -bxor ($n -shr 13)) * 1274126177) -band 0x7fffffff
    $v = [int]($n % (2 * $amp + 1)) - $amp
    return $v
}

function New-Image {
    return New-Object System.Drawing.Bitmap 32, 32
}

function Save-Image($bmp, [string]$name) {
    $small = New-Object System.Drawing.Bitmap 16, 16
    $g = [System.Drawing.Graphics]::FromImage($small)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($bmp, 0, 0, 16, 16)
    $g.Dispose()
    $small.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $small.Dispose()
    $bmp.Dispose()
}



function Add-Rivet($bmp, [int]$cx, [int]$cy, [int]$base) {
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            $dx = ($x + 0.5) - $cx
            $dy = ($y + 0.5) - $cy
            $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
            if ($d -le 2.2) {
                $n = Hash-Noise $x $y 1301 2
                if ($d -le 1.2) {
                    Set-Px $bmp $x $y ($base + 26 + $n) ($base + 29 + $n) ($base + 33 + $n)
                } elseif ($d -le 1.8) {
                    Set-Px $bmp $x $y ($base + 8 + $n) ($base + 10 + $n) ($base + 13 + $n)
                } else {
                    Set-Px $bmp $x $y ($base - 12 + $n) ($base - 10 + $n) ($base - 7 + $n)
                }
            }
        }
    }
}

# ---------------------------------------------------------------- housing
# Dark charcoal armour with vertical brushed grain, panel seams and corner rivets.
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $n = Hash-Noise $x $y 1401 1
        $streak = Hash-Noise 0 $x 1411 2
        $base = 41 + $streak + $n
        if ($x -eq 0 -or $x -eq 31) { $base -= 6 }
        if ($y -eq 0) { $base += 10 }
        if ($y -eq 31) { $base -= 9 }
        Set-Px $bmp $x $y $base ($base + 3) ($base + 7)
    }
}
Save-Image $bmp 'grill_housing.png'

# ---------------------------------------------------------------- wall plate
# Slightly lighter steel plate with a border, seams and rivets.
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $n = Hash-Noise $x $y 1421 2
        $grain = Hash-Noise 0 $y 1433 2
        $base = 55 + $n + $grain
        if ($x -eq 0 -or $x -eq 31 -or $y -eq 0 -or $y -eq 31) { $base -= 12 }
        Set-Px $bmp $x $y $base ($base + 2) ($base + 5)
    }
}
Save-Image $bmp 'grill_plate.png'

# ---------------------------------------------------------------- steel trim
# Brushed steel for the bezel, rails and caps.
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $n = Hash-Noise $x $y 1441 3
        $streak = Hash-Noise 0 $x 1451 5
        $base = 128 + $streak + $n
        if ($x -eq 0) { $base += 26 }
        if ($x -eq 1) { $base += 12 }
        if ($x -eq 31) { $base -= 28 }
        if ($x -eq 30) { $base -= 12 }
        if ($y -eq 0) { $base += 14 }
        if ($y -eq 31) { $base -= 16 }
        if ($y -eq 8 -or $y -eq 16 -or $y -eq 24) { $base -= 8 }
        Set-Px $bmp $x $y $base ($base + 3) ($base + 7)
    }
}
Save-Image $bmp 'grill_trim.png'

# ---------------------------------------------------------------- emitter strip
# Thin cyan line on a uniform dark-blue field with noise.
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $n = Hash-Noise $x $y 1461 3
        $r = 17 + $n
        $g = 27 + $n
        $b = 40 + $n

        $d = [Math]::Abs(($x + 0.5) - 16.0)
        if ($d -le 0.6) { $r = 235; $g = 250; $b = 255 }
        elseif ($d -le 1.6) { $r = 100; $g = 210; $b = 245 }
        elseif ($d -le 2.6) { $r = 30; $g = 60; $b = 90 }

        # segment gaps: let the uniform field show through
        if (($y % 6) -eq 0 -and $d -le 2.6) {
            $r = 17 + $n
            $g = 27 + $n
            $b = 40 + $n
        }

        Set-Px $bmp $x $y $r $g $b
    }
}
Save-Image $bmp 'grill_emitter.png'

# ---------------------------------------------------------------- recess
# Uniform dark-blue channel with fine noise, no gradient bands.
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $n = Hash-Noise $x $y 1471 3
        Set-Px $bmp $x $y (17 + $n) (27 + $n) (40 + $n)
    }
}
Save-Image $bmp 'grill_dark.png'

# ---------------------------------------------------------------- screw
$bmp = New-Image
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $dx = ($x + 0.5) - 16.0
        $dy = ($y + 0.5) - 16.0
        $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
        $n = Hash-Noise $x $y 1481 3
        if ($r -le 12.4) {
            $v = [int](122 - ($r * 3.2)) + $n
            if ($r -gt 10.6) { $v -= 18 }
            if (([Math]::Abs($dx) -le 1.4 -and [Math]::Abs($dy) -le 7.2) -or
                ([Math]::Abs($dy) -le 1.4 -and [Math]::Abs($dx) -le 7.2)) {
                $v = 34 + $n
            }
            $hx = $dx + 4.2
            $hy = $dy + 4.2
            if (($hx * $hx + $hy * $hy) -le 2.2) { $v += 52 }
            Set-Px $bmp $x $y $v ($v + 2) ($v + 6)
        } else {
            Set-Px $bmp $x $y (28 + $n) (30 + $n) (34 + $n)
        }
    }
}
Save-Image $bmp 'grill_screw.png'


Write-Output 'Generated emancipation grill textures:'
Get-ChildItem $out -Filter 'grill_*.png' | Sort-Object Name | Select-Object Name, Length
