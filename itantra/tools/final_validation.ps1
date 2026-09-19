<#
.SYNOPSIS
    iTantra Final Validation Script - Pass F Repository Release Gate
.DESCRIPTION
    One-command validation that checks source integrity, build health, test results,
    documentation consistency, and model provisioning for SIH submission readiness.
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
Write-Host "  iTantra Final Validation - Pass F" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# -----------------------------------------------------------
# 1. GIT STATUS
# -----------------------------------------------------------
Write-Host "[1/8] Git Status" -ForegroundColor White
$gitBranch = git rev-parse --abbrev-ref HEAD 2>$null
$gitHead = git rev-parse --short HEAD 2>$null
Write-Host "  Branch: $gitBranch"
Write-Host "  HEAD:   $gitHead"

$gitDiffCheck = git diff --check 2>&1 | Where-Object { $_ -notlike "warning:*" }
if ($gitDiffCheck) {
    Report-Fail "git diff --check" "Trailing whitespace errors found"
} else {
    Report-Pass "git diff --check - no trailing whitespace"
}

# -----------------------------------------------------------
# 2. SOURCE FILE INVENTORY
# -----------------------------------------------------------
Write-Host "`n[2/8] Source File Inventory" -ForegroundColor White

$criticalFiles = @(
    "app/src/main/java/com/itantra/core/translation/CTranslate2TranslationEngine.kt",
    "app/src/main/cpp/itantra_mt_jni.cpp",
    "app/src/main/java/com/itantra/core/inference/SherpaOnnxSpeechSynthesizer.kt",
    "app/src/main/java/com/itantra/core/inference/MicrophoneAudioSource.kt",
    "app/src/main/java/com/itantra/core/inference/ActiveLanguageSessionManager.kt",
    "app/src/main/java/com/itantra/core/inference/DeviceCapabilityDetector.kt",
    "app/src/main/java/com/itantra/core/transceiver/TransceiverCoordinator.kt",
    "app/src/main/java/com/itantra/core/crypto/SecureSessionManager.kt",
    "app/src/main/java/com/itantra/core/audio/SpeakerAudioSink.kt",
    "app/src/main/java/com/itantra/core/transport/TransportCoordinator.kt",
    "app/src/main/java/com/itantra/core/service/OperationalForegroundService.kt",
    "app/src/main/java/com/itantra/core/emergency/EmergencyPersistenceStore.kt",
    "app/src/main/AndroidManifest.xml",
    "tools/provision_models.py",
    "tools/model_manifest.json"
)

foreach ($f in $criticalFiles) {
    if (Test-Path $f) {
        Report-Pass "EXISTS: $f"
    } else {
        Report-Fail "MISSING" $f
    }
}

# -----------------------------------------------------------
# 3. LANGUAGE PACK MANIFESTS
# -----------------------------------------------------------
Write-Host "`n[3/8] Language Pack Manifests" -ForegroundColor White

$languages = @("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or")
foreach ($lang in $languages) {
    $manifest = "app/src/main/assets/language_packs/${lang}_dev_manifest.json"
    if (Test-Path $manifest) {
        Report-Pass "MANIFEST: $lang"
    } else {
        Report-Fail "MISSING MANIFEST" "$lang - $manifest"
    }
}

# -----------------------------------------------------------
# 4. BENCHMARK ASSETS
# -----------------------------------------------------------
Write-Host "`n[4/8] Benchmark Assets" -ForegroundColor White

foreach ($lang in $languages) {
    $benchmark = "app/src/main/assets/benchmark/${lang}_benchmark.json"
    if (Test-Path $benchmark) {
        Report-Pass "BENCHMARK: $lang"
    } else {
        Report-Fail "MISSING BENCHMARK" "$lang - $benchmark"
    }
}

if (Test-Path "app/src/main/assets/benchmark/tts_sentences.json") {
    Report-Pass "TTS sentences asset exists"
} else {
    Report-Fail "MISSING" "tts_sentences.json"
}

# -----------------------------------------------------------
# 5. PROHIBITED TERMS SCAN
# -----------------------------------------------------------
Write-Host "`n[5/8] Prohibited Terms Scan" -ForegroundColor White

$scanTerms = @("FEC", "forward error correction", "UnsupportedOperationException")

$docFiles = @()
if (Test-Path "iTantra_Documentation") {
    $docFiles = Get-ChildItem -Path "iTantra_Documentation" -Recurse -Filter "*.md" | Select-Object -ExpandProperty FullName
}

foreach ($term in $scanTerms) {
    $hits = @()
    foreach ($file in $docFiles) {
        $lines = Select-String -Path $file -Pattern $term -SimpleMatch -ErrorAction SilentlyContinue
        foreach ($line in $lines) {
            $hits += "$($line.Filename):$($line.LineNumber): $($line.Line.Trim())"
        }
    }

    if ($hits.Count -eq 0) {
        Report-Pass "No prohibited '$term' references in docs"
    } else {
        foreach ($hit in $hits | Select-Object -First 3) {
            Report-Warn "Prohibited term '$term'" $hit
        }
    }
}

# Mesh scan - allow negations
$meshHits = @()
foreach ($file in $docFiles) {
    $lines = Select-String -Path $file -Pattern "mesh" -SimpleMatch -ErrorAction SilentlyContinue
    foreach ($line in $lines) {
        $text = $line.Line.Trim()
        if ($text -notmatch "NOT.*mesh|NOT a mesh|never.*mesh|not.*mesh") {
            $meshHits += "$($line.Filename):$($line.LineNumber): $text"
        }
    }
}

if ($meshHits.Count -eq 0) {
    Report-Pass "No prohibited 'mesh' references (excluding negations)"
} else {
    foreach ($hit in $meshHits | Select-Object -First 3) {
        Report-Warn "Prohibited term 'mesh'" $hit
    }
}

# -----------------------------------------------------------
# 6. BUILD VALIDATION
# -----------------------------------------------------------
Write-Host "`n[6/8] Build Validation" -ForegroundColor White

if ($SkipBuild) {
    Report-Warn "Build" "Skipped via -SkipBuild flag"
} else {
    Write-Host "  Running compileDebugKotlin..." -ForegroundColor Gray
    & .\gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "compileDebugKotlin"
    } else {
        Report-Fail "compileDebugKotlin" "Exit code $LASTEXITCODE"
    }

    Write-Host "  Running testDebugUnitTest..." -ForegroundColor Gray
    & .\gradlew.bat :app:testDebugUnitTest --no-daemon 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "testDebugUnitTest"
    } else {
        Report-Fail "testDebugUnitTest" "Exit code $LASTEXITCODE"
    }

    Write-Host "  Running assembleDebug..." -ForegroundColor Gray
    & .\gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report-Pass "assembleDebug"

        $apkPath = "app/build/outputs/apk/debug/app-debug.apk"
        if (Test-Path $apkPath) {
            $apkInfo = Get-Item $apkPath
            $apkHash = (Get-FileHash $apkPath -Algorithm SHA256).Hash
            Write-Host "  APK Size: $([math]::Round($apkInfo.Length / 1MB, 2)) MB"
            Write-Host "  APK SHA256: $apkHash"
            Report-Pass "APK generated ($([math]::Round($apkInfo.Length / 1MB, 2)) MB)"

            # Check for JNI lib
            if ($env:JAVA_HOME) {
                $jniCheck = & "$env:JAVA_HOME\bin\jar.exe" tf $apkPath 2>$null | Select-String "lib/arm64-v8a/libitantra_mt_jni.so"
                if ($jniCheck) {
                    Report-Pass "libitantra_mt_jni.so packaged in APK"
                } else {
                    Report-Fail "JNI" "libitantra_mt_jni.so NOT found in APK"
                }
            } else {
                Report-Warn "JNI check" "JAVA_HOME not set, cannot verify JNI packaging"
            }
        } else {
            Report-Fail "APK" "app-debug.apk not found after assembleDebug"
        }
    } else {
        Report-Fail "assembleDebug" "Exit code $LASTEXITCODE"
    }
}

# -----------------------------------------------------------
# 7. DOCUMENTATION CONSISTENCY
# -----------------------------------------------------------
Write-Host "`n[7/8] Documentation Consistency" -ForegroundColor White

$requiredDocs = @(
    "iTantra_Documentation/FINAL_STATUS.md",
    "iTantra_Documentation/FINAL_LIMITATIONS.md",
    "iTantra_Documentation/FINAL_MODEL_MATRIX.md",
    "iTantra_Documentation/MODEL_SOURCES_AND_LICENSES.md",
    "iTantra_Documentation/ISRO_FINAL_REQUIREMENT_MATRIX.md",
    "iTantra_Documentation/ISRO_EVALUATOR_QA.md",
    "iTantra_Documentation/ACCURACY_MATRIX.md",
    "iTantra_Documentation/EFFICIENCY_MATRIX.md",
    "iTantra_Documentation/LATENCY_MATRIX.md",
    "iTantra_Documentation/TRANSPORT_VALIDATION_MATRIX.md",
    "iTantra_Documentation/EMERGENCY_PHRASE_REVIEW_MATRIX.md",
    "iTantra_Documentation/OPERATIONAL_RELIABILITY_MATRIX.md",
    "iTantra_Documentation/PHYSICAL_VALIDATION_CHECKLIST.md",
    "iTantra_Documentation/JURY_READINESS_SCORECARD.md",
    "iTantra_Documentation/Reports/FINAL_PROJECT_AUDIT.md",
    "iTantra_Documentation/Reports/MODULE_7_FINAL_VALIDATION.md"
)

foreach ($doc in $requiredDocs) {
    if (Test-Path $doc) {
        Report-Pass "DOC: $(Split-Path $doc -Leaf)"
    } else {
        Report-Fail "MISSING DOC" $doc
    }
}

# -----------------------------------------------------------
# 8. SUMMARY
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
} elseif ($script:warnings.Count -gt 0) {
    Write-Host "`n  READY_FOR_FINAL_COMMIT = YES (with warnings)" -ForegroundColor Yellow
} else {
    Write-Host "`n  READY_FOR_FINAL_COMMIT = YES" -ForegroundColor Green
}

Write-Host ""
