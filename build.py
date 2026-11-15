"""Standalone 0.6 source build; never connects to a phone or installs an APK."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'build'
TOOLS = Path(os.environ.get('BRIDGE_TOOLS_DIR', ROOT / '.tools'))
SDK = Path(os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', TOOLS / 'sdk')))
BT = SDK / 'build-tools' / os.environ.get('BRIDGE_BUILD_TOOLS', '36.0.0')
ANDROID = SDK / 'platforms/android-36/android.jar'
ECJ = Path(os.environ.get('ECJ_JAR', TOOLS / 'ecj.jar'))
SUFFIX = '.exe' if os.name == 'nt' else ''


def java_bin(name):
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']) / 'bin' / (name + SUFFIX))
    candidates.append(TOOLS / 'jdk/jdk4py/java-runtime/bin' / (name + SUFFIX))
    path = shutil.which(name)
    if path:
        candidates.append(Path(path))
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f'{name} not found; install a JDK and set JAVA_HOME. See docs/BUILD.md.')


def run(*args, cwd=ROOT, secrets=()):
    result = subprocess.run([str(arg) for arg in args], cwd=cwd, capture_output=True)
    output = result.stdout.decode('utf-8', errors='replace')
    error = result.stderr.decode('utf-8', errors='replace')
    for secret in secrets:
        if secret:
            output = output.replace(secret, '<redacted>')
            error = error.replace(secret, '<redacted>')
    if output:
        print(output, end='')
    if result.returncode:
        raise RuntimeError(error or f'Command failed: {args[0]}')


def compile_java(sources, destination, classpath):
    destination.mkdir(parents=True, exist_ok=True)
    run(java_bin('java'), '-jar', ECJ, '-8', '-encoding', 'UTF-8', '-proc:none',
        '-warn:none', '-classpath', classpath, '-d', destination, *sorted(sources))


def jar_classes(directory, target):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(directory.rglob('*.class')):
            archive.write(path, path.relative_to(directory).as_posix())


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    for path in (ANDROID, ECJ, BT / ('aapt2' + SUFFIX), BT / 'lib/d8.jar', BT / 'lib/apksigner.jar'):
        if not path.is_file():
            raise SystemExit(f'Missing build dependency: {path}. See docs/BUILD.md.')
    OUT.mkdir(exist_ok=True)
    for name in ('classes', 'stubs', 'test-classes', 'dex'):
        generated = OUT / name
        if generated.exists():
            assert generated.resolve().parent == OUT.resolve()
            shutil.rmtree(generated)
    stubs, classes, tests = OUT / 'stubs', OUT / 'classes', OUT / 'test-classes'
    compile_java((ROOT / 'stub').rglob('*.java'), stubs, str(ANDROID))
    compile_java((ROOT / 'src').rglob('*.java'), classes, os.pathsep.join([str(ANDROID), str(stubs)]))
    compile_java((ROOT / 'tests').rglob('*.java'), tests, str(classes))
    run(java_bin('java'), '-cp', os.pathsep.join([str(tests), str(classes)]), 'BridgePolicyTest')
    jar_classes(classes, OUT / 'classes.jar')
    jar_classes(stubs, OUT / 'compile-only-stubs.jar')
    (OUT / 'dex').mkdir()
    run(java_bin('java'), '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '29', '--lib', ANDROID, '--classpath', OUT / 'compile-only-stubs.jar',
        '--output', OUT / 'dex', OUT / 'classes.jar')
    manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
    version = manifest.get('{http://schemas.android.com/apk/res/android}versionName')
    final = OUT / f'HyperAlive-vivo-bridge-{version}-selfbuilt.apk'
    with tempfile.TemporaryDirectory(prefix='vivo-bridge-') as temporary:
        stage = Path(temporary)
        shutil.copytree(ROOT / 'res', stage / 'res')
        shutil.copy2(ROOT / 'AndroidManifest.xml', stage / 'AndroidManifest.xml')
        shutil.copy2(ANDROID, stage / 'android.jar')
        run(BT / ('aapt2' + SUFFIX), 'compile', '--dir', 'res', '-o', 'res.zip', cwd=stage)
        run(BT / ('aapt2' + SUFFIX), 'link', '-o', 'unsigned.apk', '-I', 'android.jar',
            '--manifest', 'AndroidManifest.xml', 'res.zip', cwd=stage)
        with zipfile.ZipFile(stage / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
            archive.write(OUT / 'dex/classes.dex', 'classes.dex')
            archive.write(ROOT / 'assets/xposed_init', 'assets/xposed_init')
        run(BT / ('zipalign' + SUFFIX), '-f', '4', stage / 'unsigned.apk', stage / 'aligned.apk')
        custom_key = os.environ.get('BRIDGE_KEYSTORE')
        key = Path(custom_key) if custom_key else OUT / 'local-debug.keystore'
        alias = os.environ.get('BRIDGE_KEY_ALIAS', 'vivobridge')
        password = os.environ.get('BRIDGE_STORE_PASSWORD', 'android')
        key_password = os.environ.get('BRIDGE_KEY_PASSWORD', password)
        if not key.exists():
            if custom_key:
                raise SystemExit('Configured BRIDGE_KEYSTORE does not exist.')
            run(java_bin('keytool'), '-genkeypair', '-keystore', key, '-alias', alias,
                '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
                '-storepass', password, '-keypass', key_password,
                '-dname', 'CN=HyperAlive Vivo Self Build', secrets=(password, key_password))
        run(java_bin('java'), '-jar', BT / 'lib/apksigner.jar', 'sign', '--ks', key,
            '--ks-key-alias', alias, '--ks-pass', 'pass:' + password,
            '--key-pass', 'pass:' + key_password, '--out', final, stage / 'aligned.apk',
            secrets=(password, key_password))
        run(java_bin('java'), '-jar', BT / 'lib/apksigner.jar', 'verify', '--verbose', final)
    with zipfile.ZipFile(final) as archive:
        assert archive.read('assets/xposed_init').strip() == b'com.qihang.vivobridge.VivoBridge'
    report = {'version': version, 'policy_checks': 43, 'source_java_files': len(list((ROOT / 'src').rglob('*.java'))),
        'self_built': True, 'device_tested': False, 'size_bytes': final.stat().st_size,
        'sha256': hashlib.sha256(final.read_bytes()).hexdigest()}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))
    print('Built', final)


if __name__ == '__main__':
    main()
