"""Inspect the signed deliverable and its source delta without connecting to a device."""
from pathlib import Path
import argparse
import os
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
WORKSPACE = ROOT.parent
if os.environ.get('BRIDGE_TOOLS_DIR'):
    sys.path.insert(0, str(Path(os.environ['BRIDGE_TOOLS_DIR']) / 'python'))
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, default=ROOT / 'build/vivo-alive-1.2-test-selfbuilt.apk')
parser.add_argument('--release', action='store_true', help='Require the published APK checksum')
args = parser.parse_args()
apk_path = args.apk
apk = APK(str(apk_path))
ns = '{http://schemas.android.com/apk/res/android}'
manifest = apk.get_android_manifest_xml()
app = manifest.find('application')
launchers = []
for activity in app.findall('activity') + app.findall('activity-alias'):
    for intent in activity.findall('intent-filter'):
        actions = [node.get(ns+'name') for node in intent.findall('action')]
        categories = [node.get(ns+'name') for node in intent.findall('category')]
        if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
            launchers.append(activity.get(ns+'name'))
assert apk.get_app_name() == 'vivo alive'
assert apk.get_package() == 'com.hyperalive.aod'
assert apk.get_androidversion_name() == '1.2-test-upstream1.0.4'
assert len(launchers) == 1
assert apk.get_main_activity() == 'com.hyperalive.aod.MainActivity'
assert app.get(ns+'name') in ('.QihangApp', 'com.hyperalive.aod.QihangApp')
assert app.get(ns+'icon') is not None
assert set(apk.get_services()) == {'com.hyperalive.aod.QihangWallpaper', 'com.hyperalive.aod.QihangHomeWallpaper'}
authorities = {node.get(ns+'authorities') for node in app.findall('provider')}
assert 'com.hyperalive.aod.clockconfig' in authorities
assert all('com.qihang.aod' not in authority for authority in authorities)
if args.release:
    expected = (ROOT / 'SHA256SUMS.txt').read_text().split()[0]
    assert hashlib.sha256(apk_path.read_bytes()).hexdigest() == expected, 'Published APK checksum mismatch'
assert apk.is_signed_v3()

assets = json.loads((ROOT / 'upstream-assets.json').read_text(encoding='utf-8'))
classes = {}
strings = []
with zipfile.ZipFile(apk_path) as archive:
    assert len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate ZIP entries'
    assert archive.read('assets/xposed_init').splitlines() == [b'com.hyperalive.aod.XposedInit']
    for entry in assets:
        name = entry['path'].removeprefix('app/src/main/')
        data = archive.read(name)
        assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest() == entry['sha1'], name
    for name in archive.namelist():
        if name.endswith('.dex'):
            dex = DEX(archive.read(name))
            strings.extend(dex.get_strings())
            for cls in dex.get_classes():
                classes[cls.get_name()] = cls

for cls in ('Lcom/hyperalive/aod/MainActivity;', 'Lcom/hyperalive/aod/QihangApp;',
            'Lcom/hyperalive/aod/QihangWallpaper;', 'Lcom/hyperalive/aod/ModuleDiagLog;',
            'Lcom/hyperalive/aod/PreModuleWallpaperBackup;', 'Lcom/hyperalive/aod/PhotoDraftStore;',
            'Lcom/qihang/vivobridge/VivoBridge;', 'Lcom/qihang/vivobridge/NativeAodContent;',
            'Lcom/qihang/vivobridge/NativeAodSession;'):
    assert cls in classes, cls
assert 'Lcom/qihang/vivobridge/NativeAodLifecycle;' not in classes
assert not any(name.startswith(('Lde/robv/android/xposed/', 'Landroid/', 'Lcom/android/server/')) for name in classes)
assert 'LSystemServerRegistrationTest;' not in classes
assert 'LBridgePolicyTest;' not in classes
assert not any('ctl.restart' in value or 'pkill -9' in value for value in strings)
main_class = classes['Lcom/hyperalive/aod/MainActivity;']
assert not any(method.get_name() in ('showRestartScopeDialog', 'doRestartScopes') for method in main_class.get_methods())
entry = next(m for m in classes['Lcom/hyperalive/aod/XposedInit;'].get_methods() if m.get_name() == 'handleLoadPackage')
calls = [i.get_output() for i in entry.get_instructions() if i.get_name().startswith('invoke')]
assert next(i for i, c in enumerate(calls) if 'VivoBridge;->handleLoadPackage' in c) < next(
    i for i, c in enumerate(calls) if 'SystemServerHook;->install' in c)

provenance = json.loads((ROOT / 'upstream-provenance.json').read_text(encoding='utf-8'))
hashes = json.loads((ROOT / 'upstream-file-hashes.json').read_text(encoding='utf-8'))
changes = []
for name, expected_hash in hashes.items():
    path = ROOT / name
    if name.startswith('app/') and path.is_file():
        data = path.read_bytes().replace(b'\r\n', b'\n')
        if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest() != expected_hash:
            changes.append(name)
assert len(changes) == 11, changes

report = {
    'app_name': apk.get_app_name(), 'package': apk.get_package(),
    'version': apk.get_androidversion_name(), 'launcher_count': len(launchers),
    'upstream_tag': provenance['tag'], 'upstream_commit': provenance['commit'],
    'upstream_assets_verified_against_git': len(assets),
    'upstream_files_changed': changes, 'runtime_class_count': len(classes),
    'restart_commands_in_dex': False, 'jvm_fakes_or_tests_packaged': False,
    'single_module_entry': 'com.hyperalive.aod.XposedInit',
    'apk_sha256': hashlib.sha256(apk_path.read_bytes()).hexdigest(),
    'device_connected_or_modified': False, 'device_tested': False,
}
(ROOT / 'build').mkdir(exist_ok=True)
(ROOT / 'build/apk-verification.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
if args.release:
    print('Published APK checksum verified')
print(json.dumps(report, indent=2, ensure_ascii=False))
