<#
.SYNOPSIS
    iTantra Final Validation Script - Complete 14-Gate Repository Release Gate
.DESCRIPTION
    One-command validation that executes real software gates:
    1. git diff --check
    2. Model manifests (10 language manifests + model_manifest.json)
    3. Benchmark assets (10 benchmark JSONs + tts_sentences.json)
    4. host_mt_test.py
    5. compileDebugKotlin
    6. testDebugUnitTest
    7. compileDebugAndroidTestKotlin
    8. assembleDebugAndroidTest
    9. assembleDebug
    10. lintDebug
    11. APK SHA256 & size check
    12. JNI presence (lib/arm64-v8a/libitantra_mt_jni.so)
    13. Tracked model/cache/build artifact audit
    14. Stale documentation scan
#>

param(
    [switch]$SkipBuild,
    [switch]$Verbose
)

$ErrorActionPreference = "Continue"
$script:failures = @()
$script:warnings = @()
$script:passes = @()

function Report-Pass {
    param([string]$Check)
    $script:passes += $Check
    Write-Host "  [PASS] $Check" -ForegroundColor Green
}

function Report-Fail {
    param([string]$Check, [string]$Detail)
    $script:failures += "${Check}: ${Detail}"
    Write-Host "  [FAIL] $Check - $Detail" -ForegroundColor Red
}

function Report-Warn {
    param([string]$Check, [string]$Detail)
    $script:warnings += "${Check}: ${Detail}"
    Write-Host "  [WARN] $Check - $Detail" -ForegroundColor Yellow
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  iTantra Final Validation (14 Gates)  " -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# -----------------------------------------------------------
# GATE 1: GIT STATUS & DIFF CHECK
# -----------------------------------------------------------
Write-Host "[Gate 1/14] Git Status & Diff Check" -ForegroundColor White
$gitBranch = git rev-parse --abbrev-ref HEAD 2>$null
$gitHead = git rev-parse --short HEAD 2>$null
Write-Host "  Branch: $gitBranch"
Write-Host "  HEAD:   $gitHead"

$gitDiffCheck = git diff --check 2>&1 | Where-Object { $_ -notlike "warning:*" }
if ($gitDiffCheck) {
    Report-Fail "Gate 1 - git diff --check" "Trailing whitespace or conflict marker errors found"
} else {
    Report-Pass "Gate 1 - git diff --check clean"
}

# -----------------------------------------------------------
# GATE 2: MODEL MANIFESTS
# -----------------------------------------------------------
Write-Host "`n[Gate 2/14] Model Manifests" -ForegroundColor White
if (Test-Path "tools/model_manifest.json") {
    Report-Pass "Gate 2 - Central model_manifest.json exists"
} else {
    Report-Fail "Gate 2 - Central model_manifest.json" "tools/model_manifest.json missing"
}

$languages = @("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or")
foreach ($lang in $languages) {
    $manifest = "app/src/main/assets/language_packs/${lang}_dev_manifest.json"
    if (Test-Path $manifest) {
        Report-Pass "Gate 2 - MANIFEST: $lang"
    } else {
        Report-Fail "Gate 2 - MANIFEST" "$lang manifest missing at $manifest"
    }
}

# -----------------------------------------------------------
# GATE 3: BENCHMARK ASSETS
# -----------------------------------------------------------
Write-Host "`n[Gate 3/14] Benchmark Assets" -ForegroundColor White
foreach ($lang in $languages) {
    $benchmark = "app/src/main/assets/benchmark/${lang}_benchmark.json"
    if (Test-Path $benchmark) {
        Report-Pass "Gate 3 - BENCHMARK: $lang"
    } else {
        Report-Fail "Gate 3 - BENCHMARK" "$lang benchmark missing at $benchmark"
    }
}

if (Test-Path "app/src/main/assets/benchmark/tts_sentences.json") {
    Report-Pass "Gate 3 - TTS sentences asset exists"
} else {
    Report-Fail "Gate 3 - TTS sentences" "tts_sentences.json missing"
}

# -----------------------------------------------------------
# GATE 4: HOST MACHINE TRANSLATION TEST
# -----------------------------------------------------------
Write-Host "`n[Gate 4/14] Host Machine Translation Test (host_mt_test.py)" -ForegroundColor White
if (Test-Path "tools/host_mt_test.py") {
    Write-Host "  Executing python tools/host_mt_test.py..." -ForegroundColor Gray
    $hostMtOutput = python tools/host_mt_test.py 2>&1
    $mtExit = $LASTEXITCODE
    if ($Verbose -or $mtExit -ne 0) {
        $hostMtOutput | ForEach-Object { Write-Host "    $_" }
    }
    if ($mtExit -eq 0) {
        Report-Pass "Gate 4 - host_mt_test.py passed (HOST_REAL_MT = PASS)"
    } else {
        Report-Fail "Gate 4 - host_mt_test.py" "Inference tests failed with exit code $mtExit"
    }
} else {
    Report-Fail "Gate 4 - host_mt_test.py" "Script tools/host_mt_test.py not found"
}

# -----------------------------------------------------------
# GATE 5: KOTLIN DEBUG COMPILATION
# -----------------------------------------------------------
Write-Host "`n[Gate 5/14] compileDebugKotlin" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 5 - compileDebugKotlin" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:compileDebugKotlin..." -ForegroundColor Gray
    & .\gradlew.bat :app:compileDebugKotlin 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 5 - compileDebugKotlin"
    } else {
        Report-Fail "Gate 5 - compileDebugKotlin" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# GATE 6: UNIT TESTS
# -----------------------------------------------------------
Write-Host "`n[Gate 6/14] testDebugUnitTest" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 6 - testDebugUnitTest" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:testDebugUnitTest..." -ForegroundColor Gray
    & .\gradlew.bat :app:testDebugUnitTest 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 6 - testDebugUnitTest"
    } else {
        Report-Fail "Gate 6 - testDebugUnitTest" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# GATE 7: ANDROIDTEST KOTLIN COMPILATION
# -----------------------------------------------------------
Write-Host "`n[Gate 7/14] compileDebugAndroidTestKotlin" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 7 - compileDebugAndroidTestKotlin" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:compileDebugAndroidTestKotlin..." -ForegroundColor Gray
    & .\gradlew.bat :app:compileDebugAndroidTestKotlin 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 7 - compileDebugAndroidTestKotlin"
    } else {
        Report-Fail "Gate 7 - compileDebugAndroidTestKotlin" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# GATE 8: ASSEMBLE ANDROIDTEST APK
# -----------------------------------------------------------
Write-Host "`n[Gate 8/14] assembleDebugAndroidTest" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 8 - assembleDebugAndroidTest" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:assembleDebugAndroidTest..." -ForegroundColor Gray
    & .\gradlew.bat :app:assembleDebugAndroidTest 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 8 - assembleDebugAndroidTest"
    } else {
        Report-Fail "Gate 8 - assembleDebugAndroidTest" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# GATE 9: ASSEMBLE DEBUG APPLICATION APK
# -----------------------------------------------------------
Write-Host "`n[Gate 9/14] assembleDebug" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 9 - assembleDebug" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:assembleDebug..." -ForegroundColor Gray
    & .\gradlew.bat :app:assembleDebug 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 9 - assembleDebug"
    } else {
        Report-Fail "Gate 9 - assembleDebug" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# GATE 10: LINT DEBUG
# -----------------------------------------------------------
Write-Host "`n[Gate 10/14] lintDebug" -ForegroundColor White
if ($SkipBuild) {
    Report-Warn "Gate 10 - lintDebug" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running gradlew :app:lintDebug..." -ForegroundColor Gray
    & .\gradlew.bat :app:lintDebug 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "Gate 10 - lintDebug"
    } else {
        # Check if there are lint errors reported in the report xml/html
        $lintReportXml = "app/build/reports/lint-results-debug.xml"
        if (Test-Path $lintReportXml) {
            [xml]$xml = Get-Content $lintReportXml
            $errors = ($xml.issues.issue | Where-Object { $_.severity -eq "Error" -or $_.severity -eq "Fatal" }).Count
            $warnings = ($xml.issues.issue | Where-Object { $_.severity -eq "Warning" }).Count
            Write-Host "  Lint Report: $errors errors, $warnings warnings"
            if ($errors -eq 0) {
                Report-Pass "Gate 10 - lintDebug (0 errors, $warnings warnings)"
            } else {
                Report-Fail "Gate 10 - lintDebug" "$errors fatal/error lint issues found"
            }
        } else {
            Report-Fail "Gate 10 - lintDebug" "Exit code $LASTEXITCODE"
        }
    }
}

# -----------------------------------------------------------
# GATE 11: APK SHA256 & INTEGRITY
# -----------------------------------------------------------
Write-Host "`n[Gate 11/14] APK SHA256 & Integrity" -ForegroundColor White
$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (Test-Path $apkPath) {
    $apkInfo = Get-Item $apkPath
    $apkHash = (Get-FileHash $apkPath -Algorithm SHA256).Hash
    Write-Host "  APK Path:   $apkPath"
    Write-Host "  APK Bytes:  $($apkInfo.Length)"
    Write-Host "  APK Size:   $([math]::Round($apkInfo.Length / 1MB, 2)) MB"
    Write-Host "  APK SHA256: $apkHash"
    Report-Pass "Gate 11 - APK exists ($($apkInfo.Length) bytes, SHA256: $apkHash)"
} else {
    if ($SkipBuild) {
        Report-Warn "Gate 11 - APK verification" "Skipped because build was skipped"
    } else {
        Report-Fail "Gate 11 - APK verification" "app-debug.apk not found after build"
    }
}

# -----------------------------------------------------------
# GATE 12: JNI PACKAGING IN APK
# -----------------------------------------------------------
Write-Host "`n[Gate 12/14] JNI Packaging in APK" -ForegroundColor White
if (Test-Path $apkPath) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) {
        $jniCheck = & "$env:JAVA_HOME\bin\jar.exe" tf $apkPath 2>$null | Select-String "lib/arm64-v8a/libitantra_mt_jni.so"
        if ($jniCheck) {
            Report-Pass "Gate 12 - lib/arm64-v8a/libitantra_mt_jni.so packaged in APK"
        } else {
            Report-Fail "Gate 12 - JNI" "libitantra_mt_jni.so NOT found inside APK"
        }
    } else {
        # Fallback to python zipfile inspection
        $jniCheck = python -c "import zipfile; z = zipfile.ZipFile('$apkPath'); print('lib/arm64-v8a/libitantra_mt_jni.so' in z.namelist())" 2>$null
        if ($jniCheck -match "True") {
            Report-Pass "Gate 12 - lib/arm64-v8a/libitantra_mt_jni.so packaged in APK (zip verification)"
        } else {
            Report-Fail "Gate 12 - JNI" "libitantra_mt_jni.so NOT found inside APK"
        }
    }
} else {
    if ($SkipBuild) {
        Report-Warn "Gate 12 - JNI packaging" "Skipped because build was skipped"
    } else {
        Report-Fail "Gate 12 - JNI" "APK not found, cannot verify JNI"
    }
}

# -----------------------------------------------------------
# GATE 13: TRACKED ARTIFACT AUDIT
# -----------------------------------------------------------
Write-Host "`n[Gate 13/14] Tracked Model/Cache/Build Artifact Audit" -ForegroundColor White
$trackedBlobs = git ls-files | Select-String -Pattern "\.(bin|onnx|apk|aar|so|jar|class)$"
if ($trackedBlobs) {
    $count = ($trackedBlobs | Measure-Object).Count
    Report-Fail "Gate 13 - Tracked Binaries" "Found $count tracked binaries/models in git repository"
    $trackedBlobs | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
} else {
    Report-Pass "Gate 13 - Tracked Artifact Audit (0 binary model blobs or build artifacts committed)"
}

# -----------------------------------------------------------
# GATE 14: STALE DOCUMENTATION SCAN
# -----------------------------------------------------------
Write-Host "`n[Gate 14/14] Stale Documentation Scan" -ForegroundColor White
$docFiles = @()
if (Test-Path "iTantra_Documentation") {
    $docFiles = Get-ChildItem -Path "iTantra_Documentation" -Recurse -Filter "*.md" | Select-Object -ExpandProperty FullName
}

# Check for stale commit hash fa39210
$staleHeadHits = @()
foreach ($file in $docFiles) {
    $hits = Select-String -Path $file -Pattern "fa39210" -SimpleMatch -ErrorAction SilentlyContinue
    foreach ($line in $hits) {
        $staleHeadHits += "$($line.Filename):$($line.LineNumber)"
    }
}
if ($staleHeadHits.Count -eq 0) {
    Report-Pass "Gate 14 - Stale Git HEAD fa39210: 0 references found"
} else {
    Report-Fail "Gate 14 - Stale Git HEAD" "Found $($staleHeadHits.Count) references to stale HEAD fa39210"
}

# Check for unqualified '100% offline'
$offlineHits = @()
foreach ($file in $docFiles) {
    $hits = Select-String -Path $file -Pattern "100% offline" -CaseSensitive:$false -ErrorAction SilentlyContinue
    foreach ($line in $hits) {
        $offlineHits += "$($line.Filename):$($line.LineNumber): $($line.Line.Trim())"
    }
}
if ($offlineHits.Count -eq 0) {
    Report-Pass "Gate 14 - Offline wording consistency (0 unqualified '100% offline' claims)"
} else {
    Report-Fail "Gate 14 - Offline wording" "Found $($offlineHits.Count) unqualified '100% offline' claims"
}

# Prohibited terms scan
$scanTerms = @("FEC", "forward error correction", "UnsupportedOperationException")
foreach ($term in $scanTerms) {
    $hits = @()
    foreach ($file in $docFiles) {
        $lines = Select-String -Path $file -Pattern $term -SimpleMatch -ErrorAction SilentlyContinue
        foreach ($line in $lines) {
            $hits += "$($line.Filename):$($line.LineNumber): $($line.Line.Trim())"
        }
    }
    if ($hits.Count -eq 0) {
        Report-Pass "Gate 14 - No prohibited '$term' in docs"
    } else {
        foreach ($hit in $hits | Select-Object -First 3) {
            Report-Warn "Gate 14 - Prohibited term '$term'" $hit
        }
    }
}

# -----------------------------------------------------------
# SUMMARY & VERDICT
# -----------------------------------------------------------
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  FINAL VALIDATION SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n  PASS:     $($script:passes.Count)" -ForegroundColor Green
Write-Host "  WARNINGS: $($script:warnings.Count)" -ForegroundColor Yellow
Write-Host "  FAILURES: $($script:failures.Count)" -ForegroundColor Red

if ($script:failures.Count -gt 0) {
    Write-Host "`n  FAILURES:" -ForegroundColor Red
    foreach ($f in $script:failures) {
        Write-Host "    - $f" -ForegroundColor Red
    }
    Write-Host "`n  READY_FOR_FINAL_COMMIT = NO" -ForegroundColor Red
    exit 1
} elseif ($script:warnings.Count -gt 0) {
    Write-Host "`n  READY_FOR_FINAL_COMMIT = YES (with non-blocking warnings)" -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "`n  READY_FOR_FINAL_COMMIT = YES" -ForegroundColor Green
    exit 0
}
