$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backup = 'D:\Projects\AdventureServer-backups\ravengard-server-20260921-222048'
$zh = 'D:\Projects\AdventureServer-ravengard-zh_CN'

function Assert-Path([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "$Label missing: $Path" }
    Write-Output "PASS ${Label}: $Path"
}

Assert-Path (Join-Path $backup 'MANIFEST.json') 'backup manifest'
Assert-Path (Join-Path $backup 'SHA256.csv') 'backup hashes'
Assert-Path (Join-Path $zh 'source') 'isolated localization source'
Assert-Path (Join-Path $zh 'zh_CN') 'isolated localization draft'

$hashFile = Import-Csv (Join-Path $backup 'SHA256.csv')
$hashFailures = @(
    foreach ($entry in $hashFile) {
        if (-not (Test-Path -LiteralPath $entry.Path)) {
            [pscustomobject]@{ Path = $entry.Path; Reason = 'missing' }
            continue
        }
        $actual = (Get-FileHash -LiteralPath $entry.Path -Algorithm $entry.Algorithm).Hash.ToUpperInvariant()
        if ($actual -ne $entry.Hash.ToUpperInvariant()) {
            [pscustomobject]@{ Path = $entry.Path; Reason = "hash mismatch: $actual" }
        }
    }
)
if ($hashFailures.Count -gt 0) {
    $hashFailures | Format-Table | Out-String | Write-Error
    throw 'backup hash verification failed'
}
Write-Output "PASS backup file hashes: $($hashFile.Count) files"

$inspect = docker inspect ravengard_dungeon | ConvertFrom-Json
$mounts = @($inspect[0].Mounts | ForEach-Object { $_.Source })
if (-not ($mounts -contains (Join-Path $root 'configuration'))) {
    throw 'ravengard_dungeon does not mount the expected configuration directory'
}
if ($mounts -contains $zh) { throw 'isolated localization workspace is mounted into the server' }
Write-Output 'PASS docker localization isolation'

$packHash = (Select-String -Path (Join-Path $root 'logs\*') -Pattern 'Hash: ([0-9a-f]{40})' -AllMatches -ErrorAction SilentlyContinue |
    ForEach-Object { $_.Matches.Value } | Select-Object -Last 1)
if ($packHash) { Write-Output "INFO latest resource pack log: $packHash" }

foreach ($port in 25565, 7270) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(2000)) { throw "port $port timed out" }
        $client.EndConnect($async)
        Write-Output "PASS localhost port $port"
    } finally { $client.Dispose() }
}

Write-Output 'Ravengard isolation verification completed.'
