import os, sys, fnmatch, zipfile, shutil
sys.stdout.reconfigure(encoding='utf-8')

root = os.path.dirname(os.path.abspath(__file__))
out_dir = os.path.join(root, 'REVIEW_ZIPS_UPDATED')

if os.path.exists(out_dir):
    shutil.rmtree(out_dir)
os.makedirs(out_dir, exist_ok=True)

exclude_dirs = {'.git', '.gradle', 'build', '.idea', '.kotlin', 'node_modules', 'REVIEW_ZIPS_UPDATED', '_iTantra_DEBUG_BUNDLE_20260921_014820'}
all_files = []
for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = [d for d in dirnames if d not in exclude_dirs]
    for f in filenames:
        if f.endswith('.zip'):
            continue
        rel = os.path.relpath(os.path.join(dirpath, f), root).replace('\\\\', '/')
        all_files.append(rel)

groups = {
    '01_CORE_APP': [
        'app/build.gradle.kts',
        'app/src/main/AndroidManifest.xml',
        'app/src/main/java/com/itantra/app/*',
        'app/src/main/java/com/example/itantra/*'
    ],
    '02_AUDIO_VAD_STT': [
        '*ContinuousListenEngine.kt',
        '*MicrophoneAudioSource.kt',
        '*Speech*.kt',
        '*Stt*.kt',
        'app/src/main/cpp/*',
        'app/src/main/assets/silero_vad.onnx',
        'app/src/main/assets/language_packs/shared/stt/*'
    ],
    '03_TTS_AUDIO_OUTPUT': [
        '*Tts*.kt',
        '*SpeakerAudioSink.kt',
        'app/src/main/assets/language_packs/*/tts/*'
    ],
    '04_LANGUAGE_SYSTEM': [
        '*Language*.kt',
        'app/src/main/assets/language_packs/*',
        'app/src/main/assets/languages*',
        '*LANGUAGE*.md',
        '*LANGUAGES*.md'
    ],
    '05_TRANSLATION_ROUTING': [
        '*TranslationRouter.kt',
        '*Translation*.kt',
        '*CTranslate2*.kt'
    ],
    '06_TRANSPORT': [
        '*Transport*.kt',
        '*Bluetooth*.kt',
        '*PeerTransport.kt'
    ],
    '07_PACKET_PROTOCOL': [
        '*Packet*.kt',
        '*ItantraPacket.kt',
        '*ReplayWindow.kt',
        '*PROTOCOL*.md'
    ],
    '08_DATABASE_STATE': [
        '*AppDatabase.kt',
        '*MessageDao.kt',
        '*MessageEntity.kt',
        '*Repository*.kt',
        '*State*.kt',
        '*EmergencyPersistenceStore.kt'
    ],
    '09_SECURITY_EMERGENCY': [
        '*Secure*.kt',
        '*Crypto*.kt',
        '*Replay*.kt',
        '*Emergency*.kt',
        '*Alert*.kt'
    ],
    '10_METRICS_DIAGNOSTICS': [
        '*Metrics*.kt',
        '*Diagnostics*.kt',
        '*Benchmark*.kt',
        '*Performance*.kt',
        'app/src/main/assets/benchmark/*'
    ],
    '11_UI_AND_LIFECYCLE': [
        '*Screen.kt',
        '*ViewModel.kt',
        '*MainActivity.kt',
        '*UiState*.kt'
    ],
    '12_TESTS_AND_SIH_EVIDENCE': [
        'app/src/test/*',
        'app/src/androidTest/*',
        '*TEST*.md',
        '*REPORT*.md',
        '*COMPLIANCE*.md',
        '*VALIDATION*.md',
        '*BENCHMARK*.md'
    ],
    '13_FULL_SOURCE_REVIEW': [
        'app/src/*',
        'app/build.gradle.kts',
        'app/proguard-rules.pro',
        '*.md',
        '*.yaml',
        '*.yml',
        '*.json',
        '*.ps1',
        '*.py',
        'gradle/*',
        'settings.gradle.kts',
        'build.gradle.kts',
        'gradle.properties',
        'gradlew',
        'gradlew.bat'
    ]
}

def matches_any(path, patterns):
    name = os.path.basename(path)
    for p in patterns:
        p_clean = p.replace('\\\\', '/')
        if fnmatch.fnmatch(path, p_clean) or fnmatch.fnmatch(name, p_clean):
            return True
        if p_clean.endswith('/*') and path.startswith(p_clean[:-2]):
            return True
    return False

print('Generating 13 clean, updated review ZIPs...')
summary = []
for grp_name in sorted(groups.keys()):
    patterns = groups[grp_name]
    matched = sorted([f for f in all_files if matches_any(f, patterns)])
    zip_path = os.path.join(out_dir, f'{grp_name}.zip')
    
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for rel in matched:
            abs_path = os.path.join(root, rel.replace('/', os.sep))
            zf.write(abs_path, rel)
            
    size = os.path.getsize(zip_path)
    summary.append((f'{grp_name}.zip', len(matched), size))
    print(f'CREATED: {grp_name}.zip ({len(matched)} files, {size:,} bytes)')

print('\n' + '='*55)
print('ALL 13 UPDATED REVIEW ZIPS GENERATED SUCCESSFULLY')
print('='*55)
for name, count, size in summary:
    print(f'{name:30} {count:4} files  {size:10,} bytes')
