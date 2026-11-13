# Packs the generated ember-pulse reference into a native Minecraft animation while
# preserving the user's original 16x16 pixel geometry, alpha, and unlit coal colors.
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$artworkRoot = Join-Path $projectRoot 'artwork'
$sourcePath = Join-Path $artworkRoot 'item-originals/powered_coal-original.png'
$referencePath = Join-Path $artworkRoot 'animation-references/powered-coal-ember-pulse.png'
$outputPath = Join-Path $projectRoot 'src/main/resources/assets/jeecore/textures/item/powered_coal.png'
$sourceImage = [System.Drawing.Bitmap]::new($sourcePath)
$referenceImage = [System.Drawing.Bitmap]::new($referencePath)
$stripImage = [System.Drawing.Bitmap]::new($sourceImage.Width, $sourceImage.Height * 3)
try {
    if ($sourceImage.Width -ne 16 -or $sourceImage.Height -ne 16) { throw 'Expected a 16x16 original.' }
    # Extract the generated reference's relative ember intensities, without importing
    # its enlarged geometry or background. Frame zero remains the exact original.
    $intensities = @()
    $cellHeight = [int]($referenceImage.Height / 3)
    for ($frame = 0; $frame -lt 3; $frame++) {
        $sum = 0.0
        $count = 0
        for ($y = $frame * $cellHeight; $y -lt ($frame + 1) * $cellHeight; $y += 4) {
            for ($x = 0; $x -lt $referenceImage.Width; $x += 4) {
                $color = $referenceImage.GetPixel($x, $y)
                if ($color.A -gt 0 -and $color.R -gt 150 -and $color.R -gt $color.B * 1.4) {
                    $sum += $color.G + $color.B * 0.5
                    $count++
                }
            }
        }
        if ($count -eq 0) { throw 'No ember colors found in reference.' }
        $intensities += $sum / $count
    }
    $range = ($intensities | Measure-Object -Maximum).Maximum - $intensities[0]
    if ($range -le 0) { throw 'Reference does not contain a brightening pulse.' }
    for ($frame = 0; $frame -lt 3; $frame++) {
        $strength = [Math]::Clamp(($intensities[$frame] - $intensities[0]) / $range, 0.0, 1.0) * 0.45
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $color = $sourceImage.GetPixel($x, $y)
                if ($frame -gt 0 -and $color.A -gt 0 -and $color.R -gt 100 -and
                    $color.R -ge $color.G * 0.85 -and $color.G -gt $color.B * 1.2) {
                    $red = [int][Math]::Round($color.R + (255 - $color.R) * $strength)
                    $green = [int][Math]::Round($color.G + ([Math]::Max(235, $color.G) - $color.G) * $strength)
                    $blue = [int][Math]::Round($color.B + ([Math]::Max(130, $color.B) - $color.B) * $strength)
                    $color = [System.Drawing.Color]::FromArgb($color.A, $red, $green, $blue)
                }
                $stripImage.SetPixel($x, $y + $frame * 16, $color)
            }
        }
    }
    $stripImage.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output 'Saved powered_coal.png as three 16x16 frames (16x48 strip).'
} finally {
    $sourceImage.Dispose()
    $referenceImage.Dispose()
    $stripImage.Dispose()
}
