[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Backup','Check','Restore')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$InstallDirectory,
    [Parameter(Mandatory=$true)][string]$TargetDirectory,
    [Parameter(Mandatory=$true)][string]$Receipt,
    [Parameter(Mandatory=$true)][string]$ApplicationExecutable
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# NSIS uses the Windows built-in host, even if launched by a PowerShell 7 deployer.
$env:PSModulePath = Join-Path $PSHOME 'Modules'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$names = @('nip.db', 'nip.db~', 'config.json', 'file')
$source = [IO.Path]::GetFullPath($InstallDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
$target = [IO.Path]::GetFullPath($TargetDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
$sourceBin = Join-Path $source 'bin'
$backupParent = "$source.nip-data-backups"

function Assert-PlainPath([string]$filename) {
    if (Test-Path -LiteralPath $filename) {
        $item = Get-Item -LiteralPath $filename -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Refusing a symlink or junction in legacy data: $filename"
        }
    }
}
function Assert-Stopped {
    foreach ($process in @(Get-CimInstance Win32_Process)) {
        $image = [string]$process.ExecutablePath
        if (($image -and ($image.StartsWith("$source\", [StringComparison]::OrdinalIgnoreCase) -or
                         $image.StartsWith("$target\", [StringComparison]::OrdinalIgnoreCase))) -or
            (!$image -and $process.Name -eq $ApplicationExecutable)) {
            throw "Close the application and its services before installing (PID $($process.ProcessId)). No process was killed."
        }
    }
}
function Get-Hash([string]$filename) {
    $stream = [IO.File]::Open($filename, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Get-Entry([string]$root, [string]$relative) {
    $filename = Join-Path $root $relative
    Assert-PlainPath $filename
    $item = Get-Item -LiteralPath $filename -Force
    if ($item.PSIsContainer) {
        [pscustomobject][ordered]@{path=$relative; directory=$true; bytes=0; sha256=$null}
        foreach ($child in @(Get-ChildItem -LiteralPath $filename -Force | Sort-Object Name)) {
            Get-Entry $root (Join-Path $relative $child.Name)
        }
    } else {
        [pscustomobject][ordered]@{path=$relative; directory=$false; bytes=$item.Length; sha256=(Get-Hash $filename)}
    }
}
function Get-Inventory([string]$root) {
    Assert-PlainPath $root
    foreach ($name in $names) {
        $filename = Join-Path $root $name
        if (Test-Path -LiteralPath $filename) {
            Assert-PlainPath $filename
            $item = Get-Item -LiteralPath $filename -Force
            if (($name -eq 'file') -ne [bool]$item.PSIsContainer) { throw "Unexpected legacy data type: $filename" }
            Get-Entry $root $name
        }
    }
}
function Assert-Same($expected, $actual) {
    $before = ConvertTo-Json -InputObject @($expected) -Depth 5 -Compress
    $after = ConvertTo-Json -InputObject @($actual) -Depth 5 -Compress
    if ($before -cne $after) { throw 'Legacy data changed or the backup did not preserve every byte. Old installation must not be removed.' }
}
function Copy-Entries([string]$from, [string]$to, $entries) {
    Assert-PlainPath $to
    [IO.Directory]::CreateDirectory($to) | Out-Null
    foreach ($entry in $entries) {
        $parts = @($entry.path -split '[\\/]')
        if ($parts.Count -eq 0 -or $parts[0] -notin $names -or
            @($parts | Where-Object { $_ -eq '' -or $_ -eq '.' -or $_ -eq '..' -or $_.Contains(':') }).Count) {
            throw 'Invalid relative path in backup manifest'
        }
        $destination = Join-Path $to $entry.path
        Assert-PlainPath $destination
        if ($entry.directory) {
            [IO.Directory]::CreateDirectory($destination) | Out-Null
        } else {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
            [IO.File]::Copy((Join-Path $from $entry.path), $destination, $true)
            if ((Get-Item -LiteralPath $destination).Length -ne $entry.bytes -or (Get-Hash $destination) -cne $entry.sha256) {
                throw "Restored file does not match the snapshot: $destination"
            }
        }
    }
}
function Read-Snapshot {
    $receiptData = Get-Content -LiteralPath $Receipt -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($receiptData.source -cne $source -or $receiptData.target -cne $target) { throw 'Installer receipt source/target mismatch' }
    if (!$receiptData.backup) { return $null }
    $backup = [IO.Path]::GetFullPath([string]$receiptData.backup)
    if (!$backup.StartsWith("$backupParent\", [StringComparison]::OrdinalIgnoreCase)) { throw 'Backup is not beside the original installation' }
    Assert-PlainPath $backupParent
    Assert-PlainPath $backup
    $manifest = Get-Content -LiteralPath (Join-Path $backup 'complete.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.source -cne $source -or $manifest.schema -ne 1) { throw 'Backup source/schema mismatch' }
    Assert-Same @($manifest.files) @(Get-Inventory (Join-Path $backup 'bin'))
    return @{root=$backup; files=@($manifest.files)}
}

try {
    Assert-PlainPath $source
    Assert-PlainPath $target
    Assert-Stopped
    if ($Mode -eq 'Backup') {
        $entries = @(Get-Inventory $sourceBin)
        $backup = $null
        if ($entries.Count) {
            Assert-PlainPath $backupParent
            $backup = Join-Path $backupParent ([Guid]::NewGuid().ToString('N'))
            [IO.Directory]::CreateDirectory($backup) | Out-Null
            Copy-Entries $sourceBin (Join-Path $backup 'bin') $entries
            Assert-Same $entries @(Get-Inventory (Join-Path $backup 'bin'))
            Assert-Same $entries @(Get-Inventory $sourceBin)
            Assert-Stopped
            $manifest = @{schema=1; source=$source; files=$entries}
            [IO.File]::WriteAllText((Join-Path $backup 'complete.json'), (ConvertTo-Json $manifest -Depth 6), $utf8)
            Write-Output "Verified legacy snapshot retained: $backup"
        }
        [IO.File]::WriteAllText($Receipt, (ConvertTo-Json @{source=$source; target=$target; backup=$backup}), $utf8)
    } elseif ($Mode -eq 'Check') {
        $snapshot = Read-Snapshot
        $entries = @()
        if ($snapshot) { $entries = @($snapshot.files) }
        Assert-Same $entries @(Get-Inventory $sourceBin)
    } else {
        $snapshot = Read-Snapshot
        if ($snapshot) {
            # Replace only the saved mutable entries, never binaries or app.asar.
            Copy-Entries (Join-Path $snapshot.root 'bin') (Join-Path $target 'bin') $snapshot.files
            Write-Output "Legacy data restored; recovery snapshot retained: $($snapshot.root)"
        }
    }
    exit 0
} catch {
    [IO.File]::WriteAllText("$Receipt.error.log", $_.Exception.ToString(), $utf8)
    Write-Error $_
    exit 1
}
