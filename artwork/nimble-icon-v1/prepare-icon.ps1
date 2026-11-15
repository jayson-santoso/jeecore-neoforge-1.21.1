# Normalize the imagegen artwork to Minecraft's existing 18x18 effect texture template.
Add-Type -AssemblyName System.Drawing
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskSource = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'generated-source.png'))
$taskBounds = [System.Drawing.Rectangle]::FromLTRB(248, 428, 1000, 944)
$taskIcon = [System.Drawing.Bitmap]::new(18, 18, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$taskGraphics = [System.Drawing.Graphics]::FromImage($taskIcon)
try {
    $taskGraphics.Clear([System.Drawing.Color]::Transparent)
    $taskGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $taskGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $taskGraphics.DrawImage($taskSource, [System.Drawing.Rectangle]::new(1, 3, 16, 11), $taskBounds, [System.Drawing.GraphicsUnit]::Pixel)
} finally { $taskGraphics.Dispose(); $taskSource.Dispose() }
$taskPalette = @(
    [System.Drawing.Color]::FromArgb(233, 249, 255),
    [System.Drawing.Color]::FromArgb(213, 237, 252),
    [System.Drawing.Color]::FromArgb(188, 217, 241),
    [System.Drawing.Color]::FromArgb(156, 194, 226)
)
for ($taskY = 0; $taskY -lt 18; $taskY++) {
    for ($taskX = 0; $taskX -lt 18; $taskX++) {
        $taskPixel = $taskIcon.GetPixel($taskX, $taskY)
        if ($taskPixel.A -lt 128) { $taskIcon.SetPixel($taskX, $taskY, [System.Drawing.Color]::Transparent); continue }
        $taskBest = $taskPalette[0]
        $taskDistance = [double]::MaxValue
        foreach ($taskColor in $taskPalette) {
            $taskDifference = [Math]::Pow($taskPixel.R - $taskColor.R, 2) + [Math]::Pow($taskPixel.G - $taskColor.G, 2) + [Math]::Pow($taskPixel.B - $taskColor.B, 2)
            if ($taskDifference -lt $taskDistance) { $taskDistance = $taskDifference; $taskBest = $taskColor }
        }
        $taskIcon.SetPixel($taskX, $taskY, $taskBest)
    }
}
try {
    $taskIcon.Save((Join-Path $taskRoot 'src/main/resources/assets/jeecore/textures/mob_effect/nimble.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $taskPreview = [System.Drawing.Bitmap]::new(288, 288)
    $taskGraphics = [System.Drawing.Graphics]::FromImage($taskPreview)
    try {
        $taskGraphics.Clear([System.Drawing.Color]::FromArgb(44, 49, 56))
        $taskGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $taskGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $taskGraphics.DrawImage($taskIcon, 0, 0, 288, 288)
        $taskPreview.Save((Join-Path $PSScriptRoot 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $taskGraphics.Dispose(); $taskPreview.Dispose() }
} finally { $taskIcon.Dispose() }
