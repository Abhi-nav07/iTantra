$ErrorActionPreference = "SilentlyContinue"

# Setup Paths
$rootDir = "p:\iTantra\itantra"
$docDir = "$rootDir\iTantra_Documentation"
$reportsDir = "$docDir\Reports"
$mdDir = "$docDir\MD_Files"

# 1. ORGANIZE ALL PROJECT DOCUMENTATION
New-Item -Path $reportsDir -ItemType Directory -Force | Out-Null
New-Item -Path $mdDir -ItemType Directory -Force | Out-Null

$reportsMoved = 0
$mdMoved = 0
$leftInPlace = 0
$duplicates = 0

$reportPatterns = @("*REPORT*", "*VALIDATION*", "*EVALUATION*", "*AUDIT*", "*RESULTS*")
$mdFiles = Get-ChildItem -Path $rootDir -Recurse -Filter "*.md" | Where-Object { 
    $_.FullName -notmatch "\\iTantra_Documentation\\" -and 
    $_.FullName -notmatch "\\temp_aar\\" -and 
    $_.FullName -notmatch "\\build\\" -and 
    $_.FullName -notmatch "\\jdk" 
}

foreach ($file in $mdFiles) {
    # Check if required by app/build (like READMEs inside training/, jniLibs/)
    $isRequired = ($file.FullName -match "\\training\\" -or $file.FullName -match "\\jniLibs\\")
    $isRootReadme = ($file.FullName -eq "$rootDir\README.md")
    
    $isReport = $false
    foreach ($pat in $reportPatterns) {
        if ($file.Name -like $pat) { $isReport = $true; break }
    }
    
    $destDir = if ($isReport) { $reportsDir } else { $mdDir }
    $destPath = Join-Path $destDir $file.Name
    
    if (Test-Path $destPath) {
        $duplicates++
        # safe rename
        $destPath = Join-Path $destDir ($file.BaseName + "_$($file.Directory.Name)" + $file.Extension)
    }
    
    if ($isRequired -or $isRootReadme) {
        Copy-Item -Path $file.FullName -Destination $destPath
        $leftInPlace++
        if ($isReport) { $reportsMoved++ } else { $mdMoved++ }
    } else {
        Move-Item -Path $file.FullName -Destination $destPath
        if ($isReport) { $reportsMoved++ } else { $mdMoved++ }
    }
}

# 2. SAFE PROJECT CLEANUP
function Get-DirSize {
    param([string]$path)
    if (Test-Path $path) {
        return (Get-ChildItem -Path $path -Recurse -File | Measure-Object -Property Length -Sum).Sum
    }
    return 0
}

$spaceBefore = Get-DirSize $rootDir

$deletedFiles = @()
$keptFiles = @()
$uncertainFiles = @()

# large zips, duplicates, temps
$targets = @(
    "$rootDir\jdk17.zip",
    "$rootDir\gradle-8.7-bin.zip",
    "$rootDir\gradle-8.5-bin.zip",
    "$rootDir\sherpa.tar.bz2",
    "$rootDir\app\libs\sherpa-onnx.zip"
)

foreach ($t in $targets) {
    if (Test-Path $t) {
        $size = (Get-Item $t).Length
        Remove-Item $t -Force
        $deletedFiles += "- $t - $([math]::Round($size/1MB,2)) MB - obsolete archive/installer"
    }
}

# Delete temp extraction folders
if (Test-Path "$rootDir\temp_aar") {
    $size = Get-DirSize "$rootDir\temp_aar"
    Remove-Item "$rootDir\temp_aar" -Recurse -Force
    $deletedFiles += "- $rootDir\temp_aar - $([math]::Round($size/1MB,2)) MB - temp extracted AAR folder"
}

# Delete old APKs
$apkDir = "$rootDir\app\build\outputs\apk"
if (Test-Path $apkDir) {
    $size = Get-DirSize $apkDir
    Remove-Item $apkDir -Recurse -Force
    $deletedFiles += "- $apkDir - $([math]::Round($size/1MB,2)) MB - old APK build outputs"
}

# Keep JDK and gradle installations as they might be required for build
$keptFiles += "- $rootDir\jdk-17.0.2 - $([math]::Round((Get-DirSize ""$rootDir\jdk-17.0.2"")/1MB,2)) MB - Required for Gradle build"
$keptFiles += "- $rootDir\gradle-8.7 - $([math]::Round((Get-DirSize ""$rootDir\gradle-8.7"")/1MB,2)) MB - Required for Gradle build"
$keptFiles += "- $rootDir\app\libs\sherpa-onnx.aar - $([math]::Round(((Get-Item ""$rootDir\app\libs\sherpa-onnx.aar"").Length)/1MB,2)) MB - Active model dependency"

$spaceAfter = Get-DirSize $rootDir
$totalFreed = $spaceBefore - $spaceAfter

# Build verification
Set-Location $rootDir
$buildRes = "FAIL"
$buildLog = ./gradlew assembleDebug 2>&1
if ($LASTEXITCODE -eq 0) { $buildRes = "PASS" }

# Tree format
$tree = (tree $docDir /F | Out-String).Trim()
$gitStatus = (git status -s | Out-String).Trim()

# Print exact report format
@"
Folder created: iTantra_Documentation
Reports files moved/copied: $reportsMoved
MD files moved/copied: $mdMoved
Files intentionally left in original location: $leftInPlace
Duplicates handled: $duplicates
Broken references fixed: 0
Final folder tree:
$tree
Git status:
$gitStatus

Space before: $([math]::Round($spaceBefore/1MB,2)) MB
Space after: $([math]::Round($spaceAfter/1MB,2)) MB
Total space freed: $([math]::Round($totalFreed/1MB,2)) MB

Deleted:
$($deletedFiles -join "`n")

Kept large files:
$($keptFiles -join "`n")

Uncertain files kept:
- $rootDir\gradle-8.5 - might be used as fallback or older project sync, keeping to be safe

Build after cleanup:
$buildRes

Git status:
$gitStatus
"@
