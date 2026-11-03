Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot '..\assets\ntmdoors\textures\blocks'
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
    return New-Object System.Drawing.Bitmap 16, 16
}

function Save-Image($bmp, [string]$name) {
    $bmp.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ---------------------------------------------------------------- body
$bmp = New-Image
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Hash-Noise $x $y 11 5
        $shade = [int]($y / 5)
        Set-Px $bmp $x $y (210 + $n - $shade) (206 + $n - $shade) (195 + $n - $shade)
    }
}
Save-Image $bmp 'sensor_body.png'

# ---------------------------------------------------------------- mounting plate
$bmp = New-Image
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Hash-Noise $x $y 23 4
        if ($x -eq 0 -or $x -eq 15 -or $y -eq 0 -or $y -eq 15) {
            Set-Px $bmp $x $y (112 + $n) (111 + $n) (106 + $n)
        } else {
            $innerShade = 0
            if ($x -eq 1 -or $y -eq 1) { $innerShade = -10 }
            Set-Px $bmp $x $y (150 + $n + $innerShade) (148 + $n + $innerShade) (142 + $n + $innerShade)
        }
    }
}
Save-Image $bmp 'sensor_plate.png'

# ---------------------------------------------------------------- front bezel
$bmp = New-Image
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Hash-Noise $x $y 41 3
        if ($x -eq 0) {
            Set-Px $bmp $x $y (52 + $n) (55 + $n) (59 + $n)
        } elseif ($x -eq 15) {
            Set-Px $bmp $x $y (62 + $n) (65 + $n) (69 + $n)
        } elseif ($y -eq 0) {
            Set-Px $bmp $x $y (62 + $n) (65 + $n) (69 + $n)
        } elseif ($y -eq 15) {
            Set-Px $bmp $x $y (20 + $n) (21 + $n) (25 + $n)
        } else {
            $shadow = 0
            if ($y -eq 14) { $shadow = -7 }
            Set-Px $bmp $x $y (39 + $n + $shadow) (41 + $n + $shadow) (44 + $n + $shadow)
        }
    }
}
Save-Image $bmp 'sensor_bezel.png'

# ---------------------------------------------------------------- final Fresnel lens
# Defined dark outline, bevelled metal ring and concentric rings with a filled centre.
$bmp = New-Image
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $dx = ($x + 0.5) - 8.0
        $dy = ($y + 0.5) - 8.0
        $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
        $mx = [Math]::Min($x, 15 - $x)
        $my = [Math]::Min($y, 15 - $y)
        $n = Hash-Noise $mx $my 307 4

        if ($r -gt 6.65) {
            Set-Px $bmp $x $y 30 30 30
        } elseif ($r -gt 6.15) {
            Set-Px $bmp $x $y 11 12 14
        } elseif ($r -gt 5.05) {
            $v = 134 + $n
            if ($r -gt 5.85) { $v -= 22 }
            if ($r -lt 5.25) { $v += 8 }
            Set-Px $bmp $x $y $v ($v + 2) ($v + 6)
        } elseif ($r -gt 4.75) {
            Set-Px $bmp $x $y 22 24 27
        } else {
            $v = 72
            if ($r -le 1.55) { $v = 124 }
            elseif ($r -le 2.45) { $v = 84 }
            elseif ($r -le 3.35) { $v = 112 }
            elseif ($r -le 4.15) { $v = 78 }
            else { $v = 58 }
            $v += $n + (Hash-Noise $mx $my 311 2)
            Set-Px $bmp $x $y $v ($v + 2) ($v + 5)
        }
    }
}
Save-Image $bmp 'sensor_lens.png'

# ---------------------------------------------------------------- status LEDs
function New-Led([string]$name, [int[]]$dark, [int[]]$outer, [int[]]$main, [int[]]$bright, [int[]]$core) {
    $bmp = New-Image
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $dx = ($x + 0.5) - 8.0
            $dy = ($y + 0.5) - 8.0
            $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
            $n = Hash-Noise $x $y 101 3
            $c = $dark
            if ($r -le 6.6) { $c = $outer }
            if ($r -le 5.5) { $c = $main }
            if ($r -le 4.1) { $c = $bright }
            if ($r -le 2.2) { $c = $core }
            $rR = $c[0] + $n
            $rG = $c[1] + $n
            $rB = $c[2] + $n
            # Small specular spot toward the upper left, like the supplied LED.
            $hx = ($x + 0.5) - 5.4
            $hy = ($y + 0.5) - 5.4
            if (($hx * $hx + $hy * $hy) -le 1.45) {
                $rR += 70
                $rG += 70
                $rB += 70
            }
            Set-Px $bmp $x $y $rR $rG $rB
        }
    }
    Save-Image $bmp $name
}

New-Led 'sensor_led_green.png' @(8, 18, 8) @(18, 70, 30) @(34, 143, 52) @(70, 220, 104) @(192, 255, 200)
New-Led 'sensor_led_blue.png' @(8, 12, 24) @(20, 52, 120) @(36, 96, 210) @(82, 160, 255) @(194, 224, 255)
New-Led 'sensor_led_yellow.png' @(24, 20, 8) @(108, 80, 16) @(198, 158, 32) @(255, 220, 76) @(255, 250, 196)
New-Led 'sensor_led_red.png' @(24, 8, 8) @(110, 22, 16) @(198, 42, 32) @(255, 82, 62) @(255, 226, 214)

# ---------------------------------------------------------------- screw head
$bmp = New-Image
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $dx = ($x + 0.5) - 8.0
        $dy = ($y + 0.5) - 8.0
        $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
        $mx = [Math]::Min($x, 15 - $x)
        $my = [Math]::Min($y, 15 - $y)
        $n = Hash-Noise $mx $my 131 3
        if ($r -le 5.8) {
            $v = [int](116 - ($r * 8.0)) + $n
            if ($r -gt 4.9) { $v -= 18 }
            if (([Math]::Abs($dx) -le 1.1 -and [Math]::Abs($dy) -le 4.2) -or
                ([Math]::Abs($dy) -le 1.1 -and [Math]::Abs($dx) -le 4.2)) {
                $v = 36 + $n
            }
            Set-Px $bmp $x $y $v $v ($v + 3)
        } else {
            Set-Px $bmp $x $y 30 32 35
        }
    }
}
Save-Image $bmp 'sensor_screw.png'

Write-Output 'Generated motion sensor textures:'
Get-ChildItem $out -Filter 'sensor_*.png' | Sort-Object Name | Select-Object Name, Length
