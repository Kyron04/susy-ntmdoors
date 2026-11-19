# Quick structural audit for the emancipation grill .bbmodel.
# Reports coplanar face overlaps (Z-fighting risk) and UV aspect mismatches.
param(
    [string]$ModelPath = (Join-Path $PSScriptRoot '..\emancipation_grill.bbmodel')
)

$model = Get-Content -Raw $ModelPath | ConvertFrom-Json
$faces = @()

foreach ($el in $model.elements) {
    $f = @([double]$el.from[0], [double]$el.from[1], [double]$el.from[2])
    $t = @([double]$el.to[0], [double]$el.to[1], [double]$el.to[2])

    $faces += [pscustomobject]@{ El = $el.name; Face = 'north'; Axis = 'z'; Coord = $f[2]; UMin = $f[0]; UMax = $t[0]; VMin = $f[1]; VMax = $t[1]; Dir = -1 }
    $faces += [pscustomobject]@{ El = $el.name; Face = 'south'; Axis = 'z'; Coord = $t[2]; UMin = $f[0]; UMax = $t[0]; VMin = $f[1]; VMax = $t[1]; Dir = 1 }
    $faces += [pscustomobject]@{ El = $el.name; Face = 'west';  Axis = 'x'; Coord = $f[0]; UMin = $f[2]; UMax = $t[2]; VMin = $f[1]; VMax = $t[1]; Dir = -1 }
    $faces += [pscustomobject]@{ El = $el.name; Face = 'east';  Axis = 'x'; Coord = $t[0]; UMin = $f[2]; UMax = $t[2]; VMin = $f[1]; VMax = $t[1]; Dir = 1 }
    $faces += [pscustomobject]@{ El = $el.name; Face = 'down';  Axis = 'y'; Coord = $f[1]; UMin = $f[0]; UMax = $t[0]; VMin = $f[2]; VMax = $t[2]; Dir = -1 }
    $faces += [pscustomobject]@{ El = $el.name; Face = 'up';    Axis = 'y'; Coord = $t[1]; UMin = $f[0]; UMax = $t[0]; VMin = $f[2]; VMax = $t[2]; Dir = 1 }
}

Write-Output ("elements: {0}  textures: {1}" -f $model.elements.Count, $model.textures.Count)
Write-Output ''
Write-Output 'Coplanar face overlaps:'

$found = 0
for ($i = 0; $i -lt $faces.Count; $i++) {
    for ($j = $i + 1; $j -lt $faces.Count; $j++) {
        $a = $faces[$i]
        $b = $faces[$j]
        if ($a.Axis -ne $b.Axis) { continue }
        if ([Math]::Abs($a.Coord - $b.Coord) -gt 0.001) { continue }

        $uOverlap = [Math]::Min($a.UMax, $b.UMax) - [Math]::Max($a.UMin, $b.UMin)
        $vOverlap = [Math]::Min($a.VMax, $b.VMax) - [Math]::Max($a.VMin, $b.VMin)
        if ($uOverlap -le 0.001 -or $vOverlap -le 0.001) { continue }

        $kind = if ($a.Dir -eq $b.Dir) { 'SAME-FACING' } else { 'back-to-back' }
        if ($kind -eq 'SAME-FACING' -or ($uOverlap * $vOverlap) -gt 0.01) {
            $found++
            "{0,-13} {1}:{2} ({3}) x {4}:{5} ({6})  area {7:N3} x {8:N3}" -f $kind, $a.El, $a.Face, $a.Coord, $b.El, $b.Face, $b.Coord, $uOverlap, $vOverlap
        }
    }
}
if ($found -eq 0) { 'none' }

Write-Output ''
Write-Output 'UV aspect mismatches (>8%):'
$stretched = 0
foreach ($el in $model.elements) {
    $dx = [double]$el.to[0] - [double]$el.from[0]
    $dy = [double]$el.to[1] - [double]$el.from[1]
    $dz = [double]$el.to[2] - [double]$el.from[2]
    foreach ($faceName in @('north', 'south', 'east', 'west', 'up', 'down')) {
        $prop = $el.faces.PSObject.Properties[$faceName]
        if ($null -eq $prop) { continue }
        $face = $prop.Value
        $w = 0.0; $h = 0.0
        if ($faceName -eq 'north' -or $faceName -eq 'south') { $w = $dx; $h = $dy }
        elseif ($faceName -eq 'east' -or $faceName -eq 'west') { $w = $dz; $h = $dy }
        else { $w = $dx; $h = $dz }
        if ($w -le 0 -or $h -le 0) { continue }

        $uw = [double]$face.uv[2] - [double]$face.uv[0]
        $vh = [double]$face.uv[3] - [double]$face.uv[1]
        if ($uw -le 0 -or $vh -le 0) { continue }

        $faceAspect = $w / $h
        $uvAspect = $uw / $vh
        $ratio = [Math]::Max($faceAspect, $uvAspect) / [Math]::Min($faceAspect, $uvAspect)
        if ($ratio -gt 1.08) {
            $stretched++
            "{0,-18} {1,-5} face {2:N2}x{3:N2}  uv {4:N2}x{5:N2}  ratio {6:N2}" -f $el.name, $faceName, $w, $h, $uw, $vh, $ratio
        }
    }
}
if ($stretched -eq 0) { 'none' }
