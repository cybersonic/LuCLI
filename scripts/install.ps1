param(
    [string]$Version = "",
    [string]$InstallDir = "",
    [string]$Repo = "cybersonic/LuCLI"
)

$ErrorActionPreference = "Stop"

if (-not $Version -or $Version.Trim() -eq "") {
    if ($env:LUCLI_VERSION -and $env:LUCLI_VERSION.Trim() -ne "") {
        $Version = $env:LUCLI_VERSION.Trim()
    } else {
        $Version = "latest"
    }
}

if (-not $InstallDir -or $InstallDir.Trim() -eq "") {
    if ($env:LUCLI_INSTALL_DIR -and $env:LUCLI_INSTALL_DIR.Trim() -ne "") {
        $InstallDir = $env:LUCLI_INSTALL_DIR.Trim()
    }
}

function Write-Info {
    param([string]$Message)
    Write-Host $Message
}

function Resolve-ReleaseTag {
    param(
        [string]$RequestedVersion,
        [string]$Repository
    )

    if ($RequestedVersion -and $RequestedVersion -ne "latest") {
        if ($RequestedVersion.StartsWith("v")) { return $RequestedVersion }
        return "v$RequestedVersion"
    }

    $apiUrl = "https://api.github.com/repos/$Repository/releases/latest"
    $response = Invoke-RestMethod -Uri $apiUrl -Headers @{ "Accept" = "application/vnd.github+json" }
    if (-not $response.tag_name) {
        throw "Could not resolve latest release tag from GitHub API."
    }
    return [string]$response.tag_name
}

function Resolve-InstallDir {
    param([string]$RequestedDir)

    if ($RequestedDir) {
        return $RequestedDir
    }

    $defaultDir = Join-Path $env:USERPROFILE ".local\bin"
    if (-not (Test-Path $defaultDir)) {
        New-Item -ItemType Directory -Path $defaultDir -Force | Out-Null
    }
    return $defaultDir
}

function Add-ToUserPathIfMissing {
    param([string]$Dir)

    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $segments = @()
    if ($userPath) {
        $segments = $userPath.Split(";") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
    }

    $alreadyPresent = $segments | Where-Object { $_.TrimEnd('\') -ieq $Dir.TrimEnd('\') }
    if ($alreadyPresent) {
        return
    }

    $newPath = if ($userPath -and $userPath.Trim() -ne "") { "$userPath;$Dir" } else { $Dir }
    [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    Write-Info "Added $Dir to your user PATH. Restart your terminal to pick it up."
}

$tag = Resolve-ReleaseTag -RequestedVersion $Version -Repository $Repo
$versionOnly = $tag.TrimStart("v")
$asset = "lucli-$versionOnly.bat"
$downloadUrl = "https://github.com/$Repo/releases/download/$tag/$asset"

$targetDir = Resolve-InstallDir -RequestedDir $InstallDir
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$targetPath = Join-Path $targetDir "lucli.bat"
$tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("lucli-install-" + [guid]::NewGuid() + ".bat")

Write-Info "Downloading $asset from $tag..."
Invoke-WebRequest -Uri $downloadUrl -OutFile $tempPath

Move-Item -Path $tempPath -Destination $targetPath -Force
Write-Info "Installed LuCLI launcher to $targetPath"

Add-ToUserPathIfMissing -Dir $targetDir

Write-Info "Run 'lucli --version' in a new terminal to verify."
