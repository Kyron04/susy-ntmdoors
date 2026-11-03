Add-Type -AssemblyName System.Drawing

$tex = Join-Path $PSScriptRoot '..\assets\ntmdoors\textures\blocks'
$out1 = Join-Path $PSScriptRoot '..\motion-sensor-model-preview-final.png'

$W = 1440
$H = 1180
$sheet = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 30, 34, 40))

$titleFont = New-Object System.Drawing.Font('Segoe UI', 26, [System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font('Segoe UI', 14, [System.Drawing.FontStyle]::Regular)
$badgeFont = New-Object System.Drawing.Font('Segoe UI', 12, [System.Drawing.FontStyle]::Bold)
$labelFont = New-Object System.Drawing.Font('Segoe UI', 14, [System.Drawing.FontStyle]::Bold)
$smallFont = New-Object System.Drawing.Font('Consolas', 11, [System.Drawing.FontStyle]::Regular)
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 240, 243, 247))
$muted = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 160, 170, 182))
$cyan = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(190, 90, 210, 245), 2)
$shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
$topBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 198, 194, 183))
$sideBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 166, 162, 151))
$darkLine = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 72, 75, 80), 1)
$badgeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 20, 93, 145))

function New-Front([string]$ledName) {
    $bmp = New-Object System.Drawing.Bitmap 80, 80
    $fg = [System.Drawing.Graphics]::FromImage($bmp)
    $fg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $fg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $fg.Clear([System.Drawing.Color]::Transparent)

    $plate = [System.Drawing.Bitmap]::FromFile((Join-Path $tex 'sensor_plate.png'))
    $bezel = [System.Drawing.Bitmap]::FromFile((Join-Path $tex 'sensor_bezel.png'))
    $lens = [System.Drawing.Bitmap]::FromFile((Join-Path $tex 'sensor_lens.png'))
    $screw = [System.Drawing.Bitmap]::FromFile((Join-Path $tex 'sensor_screw.png'))
    $led = [System.Drawing.Bitmap]::FromFile((Join-Path $tex $ledName))

    $fg.DrawImage($plate, 0, 0, 80, 80)
    $fg.DrawImage($bezel, 5, 5, 70, 70)
    $fg.DrawImage($lens, 15, 15, 50, 50)
    $fg.DrawImage($led, 34, 67, 12, 6)
    $fg.DrawImage($screw, 8, 67, 5, 5)
    $fg.DrawImage($screw, 67, 67, 5, 5)
    $fg.DrawImage($screw, 8, 8, 5, 5)
    $fg.DrawImage($screw, 67, 8, 5, 5)

    $plate.Dispose(); $bezel.Dispose(); $lens.Dispose(); $screw.Dispose(); $led.Dispose()
    $fg.Dispose()
    return $bmp
}

$g.DrawString('ntmdoors:motion_sensor - final model preview', $titleFont, $white, 28, 20)
$g.DrawString('Model 1.0.36  |  8x8x1.375 wall-mounted PIR  |  four status lights  |  all textures 16x16', $subFont, $muted, 30, 62)
$g.FillRectangle($badgeBrush, 30, 94, 132, 28)
$g.DrawString('FINAL 1.0.36', $badgeFont, $white, 40, 98)

$panels = @(
    @('IDLE', 'green LED', 'sensor_led_green.png', 30, 145),
    @('AUTHORIZED', 'blue LED', 'sensor_led_blue.png', 735, 145),
    @('MAINTENANCE', 'yellow LED', 'sensor_led_yellow.png', 30, 640),
    @('MOTION', 'red LED', 'sensor_led_red.png', 735, 640)
)

$frontSize = 260
$dx = 32
$dy = -32

foreach ($p in $panels) {
    $name = $p[0]; $ledLabel = $p[1]; $ledFile = $p[2]; $px = [int]$p[3]; $py = [int]$p[4]
    $panelW = 675
    $panelH = 465

    # Wall panel background.
    $wallRect = New-Object System.Drawing.Rectangle $px, $py, $panelW, $panelH
    $wallBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $wallRect, ([System.Drawing.Color]::FromArgb(255, 110, 121, 134)), ([System.Drawing.Color]::FromArgb(255, 76, 86, 98)), 90
    $g.FillRectangle($wallBrush, $wallRect)
    $wallBrush.Dispose()

    $fx = $px + 190
    $fy = $py + 90

    # Shadow on the wall.
    $g.FillEllipse($shadowBrush, $fx + 55, $fy + 225, $frontSize + 30, 52)

    # Extruded top and right sides.
    $topPts = @(
        (New-Object System.Drawing.Point $fx, $fy),
        (New-Object System.Drawing.Point ($fx + $frontSize), $fy),
        (New-Object System.Drawing.Point ($fx + $frontSize + $dx), ($fy + $dy)),
        (New-Object System.Drawing.Point ($fx + $dx), ($fy + $dy))
    )
    $sidePts = @(
        (New-Object System.Drawing.Point ($fx + $frontSize), $fy),
        (New-Object System.Drawing.Point ($fx + $frontSize), ($fy + $frontSize)),
        (New-Object System.Drawing.Point ($fx + $frontSize + $dx), ($fy + $frontSize + $dy)),
        (New-Object System.Drawing.Point ($fx + $frontSize + $dx), ($fy + $dy))
    )
    $g.FillPolygon($topBrush, $topPts)
    $g.FillPolygon($sideBrush, $sidePts)
    $g.DrawLine($darkLine, $topPts[3], $topPts[2])
    $g.DrawLine($darkLine, $sidePts[2], $sidePts[3])
    $g.DrawLine($darkLine, $topPts[0], $topPts[3])
    $g.DrawLine($darkLine, $sidePts[1], $sidePts[2])

    # Front face.
    $front = New-Front $ledFile
    $g.DrawImage($front, $fx, $fy, $frontSize, $frontSize)
    $front.Dispose()

    # Cyan bounds cage.
    $g.DrawRectangle($cyan, $fx, $fy, $frontSize, $frontSize)
    $g.DrawRectangle($cyan, ($fx + $dx), ($fy + $dy), $frontSize, $frontSize)
    $g.DrawLine($cyan, $fx, $fy, ($fx + $dx), ($fy + $dy))
    $g.DrawLine($cyan, ($fx + $frontSize), $fy, ($fx + $frontSize + $dx), ($fy + $dy))
    $g.DrawLine($cyan, $fx, ($fy + $frontSize), ($fx + $dx), ($fy + $frontSize + $dy))
    $g.DrawLine($cyan, ($fx + $frontSize), ($fy + $frontSize), ($fx + $frontSize + $dx), ($fy + $frontSize + $dy))

    # Labels.
    $dotColor = switch ($ledFile) {
        'sensor_led_green.png' { [System.Drawing.Color]::FromArgb(255, 60, 210, 90) }
        'sensor_led_blue.png' { [System.Drawing.Color]::FromArgb(255, 70, 150, 255) }
        'sensor_led_yellow.png' { [System.Drawing.Color]::FromArgb(255, 250, 205, 50) }
        default { [System.Drawing.Color]::FromArgb(255, 240, 65, 55) }
    }
    $dotBrush = New-Object System.Drawing.SolidBrush $dotColor
    $g.FillEllipse($dotBrush, $px + 34, $py + 414, 14, 14)
    $g.DrawString($name, $labelFont, $white, $px + 58, $py + 408)
    $g.DrawString($ledLabel, $smallFont, $muted, $px + 250, $py + 414)
    $dotBrush.Dispose()
}

$g.DrawString('Front faces north  |  cyan cage = 8x8x1.375 sensor bounds  |  generated from the final assets', $smallFont, $muted, 30, $H - 38)

$g.Dispose()
$sheet.Save($out1, [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()

Write-Output $out1
