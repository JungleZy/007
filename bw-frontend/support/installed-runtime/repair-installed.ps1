param([string]$InstallDir)
$ErrorActionPreference = 'Stop'
$toolDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$fixedHash = 'fa24d77e0fc78443ecb360acf8d9af263f9ed6a461d530fd7bb6379288aa1774'
$oldHash = '832ed71e668ae1984a7a6dbc23d11f5e559cca09349aa9e2307e9d3361a77e6a'
$script:lines = @()
$replaced = $false
$stage = $null
$backup = $null
$exitCode = 1

function Log([string]$message) {
    $script:lines += $message
    Write-Host $message
}

function FileHash([string]$file) {
    $stream = [IO.File]::OpenRead($file)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    # .NET 2.0/3.5 exposes HashAlgorithm cleanup through Clear(), not public Dispose().
    finally { $stream.Close(); $algorithm.Clear() }
}

try {
    $payload = Join-Path $toolDirectory 'payload\app.asar'
    if ((FileHash $payload) -ne $fixedHash) { throw 'Repair payload checksum mismatch. No installed file was changed.' }
    if (!$InstallDir) { $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\nip-traffic-system' }
    if ([IO.Path]::GetExtension($InstallDir) -eq '.exe') { $InstallDir = Split-Path -Parent $InstallDir }
    $archive = Join-Path $InstallDir 'resources\app.asar'
    if (!(Test-Path -LiteralPath $archive)) { throw 'Installation not found. Drag the installed application EXE onto repair-installed.cmd.' }
    $actual = FileHash $archive
    Log "Installation: $InstallDir"
    Log "Archive before repair: $actual"
    if ($actual -ne $oldHash -and $actual -ne $fixedHash) { throw 'This is not the known affected release. No installed file was changed; run check-installed.cmd and send its report.' }
    $executables = @(Get-ChildItem -LiteralPath $InstallDir -Filter '*.exe' | Where-Object { !$_.PSIsContainer -and $_.Name -notlike 'Uninstall*' } | Sort-Object Length -Descending)
    if (!$executables.Count) { throw 'Application executable not found.' }
    $executable = $executables[0]
    foreach ($running in @(Get-Process -Name $executable.BaseName -ErrorAction SilentlyContinue)) {
        try { $runningPath = $running.MainModule.FileName }
        catch { throw 'Close the application and its error dialogs before repairing.' }
        if ($runningPath -eq $executable.FullName) { throw "Close the application and its error dialogs first (process $($running.Id))." }
    }
    if ($actual -eq $oldHash) {
        $suffix = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $stage = "$archive.$suffix.tmp"
        $backup = "$archive.before-fix1-$suffix.bak"
        [IO.File]::Copy($payload, $stage, $false)
        if ((FileHash $stage) -ne $fixedHash) { throw 'Staged repair file checksum mismatch.' }
        # Same-directory replacement keeps a backup and switches the file atomically.
        [IO.File]::Replace($stage, $archive, $backup)
        $replaced = $true
        Log "Original file backup: $backup"
        if ((FileHash $archive) -ne $fixedHash) { throw 'Installed file checksum mismatch after replacement.' }
    } else {
        Log 'The installed file already matches fix1; verifying dependency loading.'
    }

    $details = Join-Path $toolDirectory 'installed-runtime-report.json'
    if (Test-Path -LiteralPath $details) { [IO.File]::Delete($details) }
    $probe = Join-Path $toolDirectory 'probe-installed.cjs'
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $executable.FullName
    $info.Arguments = '"' + $probe + '" "' + $archive + '" "' + $details + '" --quiet'
    $info.WorkingDirectory = $toolDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['ELECTRON_RUN_AS_NODE'] = '1'
    $info.EnvironmentVariables['NODE_PATH'] = ''
    $info.EnvironmentVariables['NODE_OPTIONS'] = ''
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        if (!$process.Start()) { throw 'Could not start the installed runtime check.' }
        if (!$process.WaitForExit(30000)) {
            $process.Kill()
            $process.WaitForExit()
            throw 'Installed runtime check timed out.'
        }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        if ($process.ExitCode -ne 0 -or !$stdout.Contains('INSTALLED_RUNTIME_OK')) {
            if ($stderr) { Log $stderr }
            throw 'Installed dependency loading check failed. See installed-runtime-report.json.'
        }
    } finally { $process.Dispose() }
    Log "Archive after repair: $(FileHash $archive)"
    Log 'REPAIR_OK - Dependency loading verified. You can now start the application.'
    $exitCode = 0
} catch {
    Log ("REPAIR_FAILED: " + $_.Exception.Message)
    if ($replaced -and $backup -and (Test-Path -LiteralPath $backup)) {
        try {
            $rejected = "$archive.rejected-fix1-$([Guid]::NewGuid().ToString('N')).bak"
            [IO.File]::Replace($backup, $archive, $rejected)
            Log 'The original installed file has been restored.'
        } catch {
            Log "Automatic rollback failed. The original file remains at: $backup"
            Log $_.Exception.Message
        }
    }
    Log 'Database and user configuration directories were not changed.'
} finally {
    if ($stage -and (Test-Path -LiteralPath $stage)) {
        try { [IO.File]::Delete($stage) } catch { Log "Temporary file retained: $stage" }
    }
    [IO.File]::WriteAllLines((Join-Path $toolDirectory 'repair-result.txt'), [string[]]$script:lines, (New-Object System.Text.UTF8Encoding($false)))
}
exit $exitCode
