param(
    [ValidateSet("auto", "release", "beta", "alpha")]
    [string]$Channel = "auto",

    [switch]$DryRun,

    [string]$PythonExecutable = "python"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage Exit code: $LASTEXITCODE"
    }
}

function Get-CheckedOutput {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )
    $output = & $FilePath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $details = ($output | Out-String).Trim()
        throw ($FailureMessage + $(if ($details) { "`n$details" } else { "" }))
    }
    return ($output | Out-String).Trim()
}

function Test-GitCommand {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    & git @Arguments *> $null
    return $LASTEXITCODE -eq 0
}

function Read-Properties {
    param([Parameter(Mandatory = $true)][string]$Path)
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $result[$trimmed.Substring(0, $separator).Trim()] =
            $trimmed.Substring($separator + 1).Trim()
    }
    return $result
}

function Require-Property {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Properties,
        [Parameter(Mandatory = $true)][string]$Name
    )
    if (-not $Properties.ContainsKey($Name) -or
        [string]::IsNullOrWhiteSpace([string]$Properties[$Name])) {
        throw "gradle.properties does not define required property '$Name'."
    }
    return [string]$Properties[$Name]
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is not available in PATH."
}

$repositoryRoot = Get-CheckedOutput git @("rev-parse", "--show-toplevel") `
    "This command must be run inside a Git repository."
$repositoryRoot = [System.IO.Path]::GetFullPath($repositoryRoot)
$currentDirectory = [System.IO.Path]::GetFullPath((Get-Location).Path)
if ($currentDirectory.TrimEnd("\") -ne $repositoryRoot.TrimEnd("\")) {
    throw "Run this script from the repository root: $repositoryRoot"
}

$propertiesPath = Join-Path $repositoryRoot "gradle.properties"
$wrapper = Join-Path $repositoryRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "gradle.properties was not found in the repository root."
}
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw "Gradle Wrapper was not found: $wrapper"
}

$branch = Get-CheckedOutput git @("branch", "--show-current") "Unable to determine current branch."
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw "Current HEAD is detached. Check out a branch before releasing."
}

$status = Get-CheckedOutput git @("status", "--porcelain=v1", "--untracked-files=all") `
    "Unable to inspect working tree state."
if (-not [string]::IsNullOrWhiteSpace($status)) {
    throw "Working tree is not clean. Commit, stash, or remove changes before releasing.`n$status"
}

$properties = Read-Properties $propertiesPath
$modVersion = Require-Property $properties "mod_version"
$minecraftVersion = Require-Property $properties "minecraft_version"
$modName = Require-Property $properties "mod_name"
$uApiVersion = Require-Property $properties "u_api_version"
$uApiRange = Require-Property $properties "u_api_version_range"
$releaseRemote = Require-Property $properties "release_remote"

if ($modVersion -notmatch "^\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$") {
    throw "mod_version '$modVersion' must be a SemVer-like value such as 1.0.0 or 1.0.0-beta.1."
}
if ($minecraftVersion -notmatch "^\d+\.\d+\.\d+$") {
    throw "minecraft_version '$minecraftVersion' is invalid."
}
if ($modName -ne "DEDICATED DUNGEONS") {
    throw "mod_name must be 'DEDICATED DUNGEONS', found '$modName'."
}
if ($uApiVersion -notmatch "^\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$") {
    throw "u_api_version '$uApiVersion' is invalid."
}
if ($uApiRange -notmatch "^\[[^\s,]+,(?:[^\s,]+)?\)$") {
    throw "u_api_version_range '$uApiRange' must look like [2.0.0,3.0.0)."
}
if ($releaseRemote -notmatch "^[A-Za-z0-9._-]+$") {
    throw "release_remote '$releaseRemote' is invalid."
}

Invoke-Checked git @("fetch", $releaseRemote, $branch, "--tags", "--prune") "git fetch failed."
$remoteBranch = "$releaseRemote/$branch"
if (-not (Test-GitCommand @("rev-parse", "--verify", $remoteBranch))) {
    throw "Current branch '$branch' does not exist on remote '$releaseRemote'."
}
$head = Get-CheckedOutput git @("rev-parse", "HEAD") "Unable to read local HEAD."
$remoteHead = Get-CheckedOutput git @("rev-parse", $remoteBranch) "Unable to read remote HEAD."
if ($head -ne $remoteHead) {
    throw "Local HEAD does not match $remoteBranch.`nLocal:  $head`nRemote: $remoteHead"
}
$aheadBehind = Get-CheckedOutput git @(
    "rev-list", "--left-right", "--count", "$branch...$remoteBranch") `
    "Unable to compare local and remote branches."
$parts = $aheadBehind -split "\s+"
if ($parts.Count -lt 2 -or $parts[0] -ne "0" -or $parts[1] -ne "0") {
    throw "Branch is not synchronized. Ahead: $($parts[0]); behind: $($parts[1])."
}

$inferredChannel = if ($modVersion -match "-alpha(?:[.-]|$)") {
    "alpha"
} elseif ($modVersion -match "-(?:beta|rc)(?:[.-]|$)") {
    "beta"
} else {
    "release"
}
if ($Channel -ne "auto" -and $Channel -ne $inferredChannel) {
    throw "Requested channel '$Channel' does not match mod_version '$modVersion' (inferred '$inferredChannel')."
}
$Channel = $inferredChannel
$tag = "v$modVersion+mc$minecraftVersion"
if (Test-GitCommand @("show-ref", "--tags", "--verify", "--quiet", "refs/tags/$tag")) {
    throw "Tag '$tag' already exists locally."
}
$remoteTag = Get-CheckedOutput git @("ls-remote", "--tags", $releaseRemote, "refs/tags/$tag") `
    "Unable to check remote tags."
if (-not [string]::IsNullOrWhiteSpace($remoteTag)) {
    throw "Tag '$tag' already exists on remote '$releaseRemote'."
}

Write-Host "Project:           $modName"
Write-Host "Branch:            $branch"
Write-Host "Remote:            $releaseRemote"
Write-Host "Version:           $modVersion"
Write-Host "Minecraft:         $minecraftVersion"
Write-Host "Required U-API:    $uApiRange"
Write-Host "Channel:           $Channel"
Write-Host "Tag:               $tag"
Write-Host "Commit:            $head"
Write-Host ""
Write-Host "Running clean build..."
Invoke-Checked $wrapper @(
    "--no-daemon",
    "-Puse_local_u_api=true",
    "-PpythonExecutable=$PythonExecutable",
    "clean",
    "build"
) "Gradle build failed. No tag was created."

$jars = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "build/libs") `
    -Filter "dedicated-dungeons-*.jar" |
    Where-Object { $_.Name -notmatch "-(?:sources|javadoc|dev)\.jar$" })
if ($jars.Count -ne 1 -or $jars[0].Length -le 0) {
    throw "Expected exactly one non-empty distribution JAR, found $($jars.Count)."
}
Write-Host "Artifact:          $($jars[0].Name)"

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry-run completed successfully. Tag '$tag' was not created or pushed."
    exit 0
}

$tagCreated = $false
try {
    Invoke-Checked git @("tag", "-a", $tag, "-m",
        "$modName $modVersion for Minecraft $minecraftVersion") "Failed to create annotated tag."
    $tagCreated = $true
    Invoke-Checked git @("push", $releaseRemote, $tag) "Failed to push tag '$tag'."
    Write-Host "Release tag pushed. GitHub Actions will perform publication."
} catch {
    if ($tagCreated) {
        Write-Warning "Tag push failed; removing local tag '$tag'."
        & git tag -d $tag *> $null
    }
    throw
}
