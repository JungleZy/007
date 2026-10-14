[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RustArtifact,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][string]$PlaywrightDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows -or $env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Native Windows x64 is required' }
$artifact = (Resolve-Path -LiteralPath $RustArtifact).Path
$playwright = (Resolve-Path -LiteralPath $PlaywrightDirectory).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output | Out-Null
Start-Transcript -LiteralPath (Join-Path $output 'orchestrator.log') -Force
try {
    # windows-2022 documents MySQL under Database tools. Reuse binaries ONLY:
    # https://github.com/actions/runner-images/blob/main/images/windows/Windows2022-Readme.md#database-tools
    # Never start/stop a service, use its data directory, or connect to port 3306.
    $server = Get-Command mysqld.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    $mysqld = if ($server) { $server.Source } else { $null }
    if (-not $mysqld) {
        $roots = @('C:\tools', 'C:\Program Files\MySQL', 'C:\ProgramData\chocolatey\lib\mysql')
        $candidates = @($roots | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object {
            Get-ChildItem -LiteralPath $_ -Filter mysqld.exe -Recurse -File -ErrorAction SilentlyContinue
        } | Where-Object { Test-Path -LiteralPath (Join-Path $_.DirectoryName 'mysql.exe') } | Sort-Object FullName)
        if ($candidates.Count -eq 0) { throw 'Documented preinstalled MySQL server/client binaries are unavailable; refusing shared service or unpinned download fallback' }
        $mysqld = $candidates[0].FullName
    }
    & $mysqld --version
    if ($LASTEXITCODE -ne 0) { throw 'MySQL executable failed' }
    $env:DESKTOP_ACCEPTANCE_POWERSHELL = Join-Path $PSHOME 'pwsh.exe'
    & node (Join-Path $PSScriptRoot 'windows-desktop-acceptance.cjs') $artifact $output $mysqld $playwright
    if ($LASTEXITCODE -ne 0) { throw "Native desktop acceptance exited $LASTEXITCODE" }
} finally {
    Stop-Transcript
}
