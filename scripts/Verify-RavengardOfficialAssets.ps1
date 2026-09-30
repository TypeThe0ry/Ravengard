$ErrorActionPreference = 'Stop'

$expectedMapHash = '3FD09A4564F8EF1033D7A65A5CCA897CB15BA574DE2D6B85057CC99C71A66A0B'
$expectedPackHash = 'a56324fc3808331dcbd6d0fa1523c1dcfe649618'
$map = Join-Path $PSScriptRoot '..\configuration\ravengard\dungeon_1.polar'
$packUrl = "http://127.0.0.1:7270/$expectedPackHash.zip"
$packOut = Join-Path ([System.IO.Path]::GetTempPath()) ("ravengard-official-pack-{0}.zip" -f [guid]::NewGuid())
$assetRoot = Join-Path $PSScriptRoot '..\configuration\resourcepacks\ravengard\assets\hypixel_ravengard'

$actualMapHash = (Get-FileHash -LiteralPath $map -Algorithm SHA256).Hash
if ($actualMapHash -ne $expectedMapHash) {
    throw "Official dungeon map hash mismatch: $actualMapHash"
}
Write-Output "PASS official map SHA-256: $actualMapHash"

New-Item -ItemType Directory -Force (Split-Path $packOut) | Out-Null
Invoke-WebRequest -Uri $packUrl -OutFile $packOut
$packBytes = (Get-Item -LiteralPath $packOut).Length
if ($packBytes -le 0) { throw 'Resource pack download is empty' }
Write-Output "PASS resource pack GET: $packUrl ($packBytes bytes)"

$required = @(
    'models/item/gameplay/portals/exit_portal.json',
    'models/item/gameplay/doors/door_1.json',
    'models/item/gameplay/doors/door_1_top.json',
    'models/item/gameplay/doors/iron_gate.json',
    'textures/item/gameplay/portals/exit_portal.png',
    'textures/item/gameplay/doors/door_1.png',
    'textures/item/gameplay/doors/door_1_top.png',
    'textures/item/gameplay/doors/iron_gate.png',
    'textures/entity/mimic_normal_texture.png',
    'textures/entity/mimic_silver.png'
)
foreach ($relative in $required) {
    $path = Join-Path $assetRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Official Resource Pack asset missing: $relative"
    }
}
Write-Output "PASS required Ravengard door/portal/Mimic assets: $($required.Count) files"

$soundsPath = Join-Path $assetRoot 'sounds.json'
$sounds = (Get-Content -LiteralPath $soundsPath -Raw -Encoding UTF8 | ConvertFrom-Json)
$soundKeys = @(
    'feature.door.interact', 'feature.door.open', 'feature.door.close',
    'ui.open', 'ui.clicks.good_click', 'feature.combat.unblockable',
    'action.impact', 'action.braam', 'feature.portal.exit.ambience',
    'feature.portal.exit.spawn', 'feature.portal.exit.close',
    'action.alert.timpani', 'music.game.start',
    'music.game.dungeon_ambience', 'action.riser', 'action.alert.dramatic'
)
$soundNames = @($sounds.PSObject.Properties.Name)
$missingSounds = @($soundKeys | Where-Object { $_ -notin $soundNames })
if ($missingSounds.Count -gt 0) {
    throw "Official Ravengard sound keys missing: $($missingSounds -join ', ')"
}
Write-Output "PASS Ravengard sound keys: $($soundKeys.Count)"
