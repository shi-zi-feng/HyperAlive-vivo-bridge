"""Build the single vivo alive APK with the workspace's Android SDK and ECJ."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
WORKSPACE = ROOT.parent
MAIN = ROOT / 'app/src/main'
spec = importlib.util.spec_from_file_location('companion_build_helpers', WORKSPACE / 'build.py')
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)
JAVA = helpers.java_bin('java')
BT, ANDROID = helpers.BT, helpers.ANDROID
OUT = ROOT / 'build'
FINAL = OUT / 'vivo-alive-1.2-test-selfbuilt.apk'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except AttributeError:
    pass


def run(*args, cwd=ROOT, secrets=()):
    return helpers.run(*args, cwd=cwd, secrets=secrets)


def compile_java(sources, destination, classpath):
    return helpers.compile_java(sources, destination, classpath)


def jar_classes(directory, target):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(directory.rglob('*.class')):
            archive.write(path, path.relative_to(directory).as_posix())


def validate_sources():
    manifest = ET.parse(MAIN / 'AndroidManifest.xml').getroot()
    app = manifest.find('application')
    launchers = []
    for activity in app.findall('activity') + app.findall('activity-alias'):
        for intent in activity.findall('intent-filter'):
            actions = [node.get(ANDROID_NS + 'name') for node in intent.findall('action')]
            categories = [node.get(ANDROID_NS + 'name') for node in intent.findall('category')]
            if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
                launchers.append(activity.get(ANDROID_NS + 'name'))
    assert launchers == ['.MainActivity'], launchers
    assert manifest.get('package') == 'com.hyperalive.aod'
    assert app.get(ANDROID_NS + 'icon') == '@mipmap/ic_launcher'
    strings = {item.get('name'): item.text for item in ET.parse(MAIN / 'res/values/strings.xml').getroot()}
    assert strings['app_name'] == 'vivo alive'
    scopes = [node.text for node in ET.parse(MAIN / 'res/values/arrays.xml').findall('.//item')]
    assert scopes == ['android', 'com.android.systemui', 'com.vivo.nightpearl', 'com.hyperalive.aod']
    assert (MAIN / 'assets/xposed_init').read_text().splitlines() == ['com.hyperalive.aod.XposedInit']
    assert not (MAIN / 'java/com/qihang/vivobridge/MainActivity.java').exists()
    main_activity = (MAIN / 'java/com/hyperalive/aod/MainActivity.java').read_text(encoding='utf-8')
    assert 'ctl.restart' not in main_activity and 'pkill' not in main_activity
    assert 'doRestartScopes' not in main_activity
    return manifest


def main():
    from fetch_assets import fetch_assets
    fetch_assets()
    manifest = validate_sources()
    OUT.mkdir(exist_ok=True)
    for name in ('classes', 'stubs', 'test-classes', 'jvm-test-classes', 'dex'):
        generated = OUT / name
        if generated.exists():
            assert generated.resolve().parent == OUT.resolve()
            shutil.rmtree(generated)
    stubs = OUT / 'stubs'
    classes = OUT / 'classes'
    tests = OUT / 'test-classes'
    compile_java(sorted((ROOT / 'stub').rglob('*.java')), stubs, str(ANDROID))

    # aapt2 on Windows requires ASCII paths. The temporary directory contains
    # only build inputs and outputs owned by this invocation.
    with tempfile.TemporaryDirectory(prefix='vivo-alive-') as temporary:
        stage = Path(temporary)
        shutil.copytree(MAIN / 'res', stage / 'res')
        shutil.copy2(MAIN / 'AndroidManifest.xml', stage / 'AndroidManifest.xml')
        shutil.copy2(ANDROID, stage / 'android.jar')
        (stage / 'r').mkdir()
        run(BT / ('aapt2' + helpers.SUFFIX), 'compile', '--dir', 'res', '-o', 'res.zip', cwd=stage)
        run(BT / ('aapt2' + helpers.SUFFIX), 'link', '-o', 'base.apk', '-I', 'android.jar',
            '--java', 'r', '--manifest', 'AndroidManifest.xml', 'res.zip', cwd=stage)
        sources = sorted((MAIN / 'java').rglob('*.java')) + sorted((stage / 'r').rglob('*.java'))
        compile_java(sources, classes, os.pathsep.join([str(ANDROID), str(stubs)]))
        compile_java(sorted((ROOT / 'tests').glob('*.java')), tests, str(classes))
        run(JAVA, '-cp', os.pathsep.join([str(tests), str(classes)]), 'BridgePolicyTest')
        jvm_tests = OUT / 'jvm-test-classes'
        compile_java(sorted((ROOT / 'tests/jvm').rglob('*.java')), jvm_tests,
                     os.pathsep.join([str(classes), str(stubs), str(ANDROID)]))
        run(JAVA, '-cp', os.pathsep.join([str(jvm_tests), str(classes), str(stubs), str(ANDROID)]),
            'SystemServerRegistrationTest')
        jar_classes(classes, OUT / 'classes.jar')
        jar_classes(stubs, OUT / 'stubs.jar')
        (OUT / 'dex').mkdir()
        run(JAVA, '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8',
            '--min-api', '29', '--lib', ANDROID, '--classpath', OUT / 'stubs.jar',
            '--output', OUT / 'dex', OUT / 'classes.jar')
        with zipfile.ZipFile(stage / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((OUT / 'dex').glob('classes*.dex')):
                archive.write(path, path.name)
            for path in sorted((MAIN / 'assets').rglob('*')):
                if path.is_file():
                    compression = zipfile.ZIP_STORED if path.suffix.lower() in ('.mp4', '.png', '.jpg', '.webp') else zipfile.ZIP_DEFLATED
                    archive.write(path, 'assets/' + path.relative_to(MAIN / 'assets').as_posix(), compress_type=compression)
        run(BT / ('zipalign' + helpers.SUFFIX), '-p', '-f', '4', stage / 'base.apk', stage / 'aligned.apk')
        custom_key = os.environ.get('VIVO_ALIVE_KEYSTORE')
        keystore = Path(custom_key) if custom_key else OUT / 'selfbuild.keystore'
        alias = os.environ.get('VIVO_ALIVE_KEY_ALIAS', 'vivoalive')
        password = os.environ.get('VIVO_ALIVE_STORE_PASSWORD', 'android')
        key_password = os.environ.get('VIVO_ALIVE_KEY_PASSWORD', password)
        if not keystore.is_file():
            if custom_key:
                raise SystemExit('Configured VIVO_ALIVE_KEYSTORE does not exist')
            run(helpers.java_bin('keytool'), '-genkeypair', '-keystore', keystore,
                '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048',
                '-validity', '10000', '-storepass', password, '-keypass', key_password,
                '-dname', 'CN=vivo alive Self Build', secrets=(password, key_password))
        signer = BT / 'lib/apksigner.jar'
        run(JAVA, '-jar', signer, 'sign', '--ks', keystore, '--ks-key-alias', alias,
            '--ks-pass', 'pass:' + password, '--key-pass', 'pass:' + key_password,
            '--out', FINAL, stage / 'aligned.apk', secrets=(password, key_password))
        run(JAVA, '-jar', signer, 'verify', '--verbose', FINAL)
    with zipfile.ZipFile(FINAL) as archive:
        assert archive.read('assets/xposed_init').strip() == b'com.hyperalive.aod.XposedInit'
        assert not any(name.startswith('de/robv/') for name in archive.namelist())
    report = {
        'app_name': 'vivo alive', 'package': 'com.hyperalive.aod',
        'version_name': manifest.get(ANDROID_NS + 'versionName'),
        'version_code': manifest.get(ANDROID_NS + 'versionCode'),
        'launcher_count': 1,
        'asset_count': sum(path.is_file() for path in (MAIN / 'assets').rglob('*')),
        'sha256': hashlib.sha256(FINAL.read_bytes()).hexdigest(),
        'size_bytes': FINAL.stat().st_size,
        'device_tested': False,
    }
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
    print('Built', FINAL)


if __name__ == '__main__':
    main()
