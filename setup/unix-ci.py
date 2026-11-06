#!/usr/bin/env python
# License: GPLv3 Copyright: 2017, Kovid Goyal <kovid at kovidgoyal.net>


import glob
import io
import os
import shlex
import shutil
import subprocess
import sys
import tarfile
import time
from tempfile import NamedTemporaryFile
from urllib.request import urlopen

_plat = sys.platform.lower()
ismacos = 'darwin' in _plat
iswindows = 'win32' in _plat or 'win64' in _plat


def setenv(key, val):
    os.environ[key] = os.path.expandvars(val)


if ismacos:

    SWBASE = '/Users/Shared/calibre-build/sw'
    SW = SWBASE + '/sw'

    def patch_macos_headers():
        # Fix Qt header bug on Apple Clang / arm64 where __has_builtin(__yield) evaluates to true
        # but __yield() is undeclared without <arm_acle.h>, causing an implicit-function-declaration error.
        # Excluding Clang causes Qt's qyieldcpu.h to fall through to __builtin_arm_yield() which works cleanly.
        if not os.path.isdir(SW):
            return
        for root, _, files in os.walk(SW):
            for f in files:
                if f == 'qyieldcpu.h':
                    p = os.path.join(root, f)
                    try:
                        real = os.path.realpath(p)
                        os.chmod(real, 0o644)
                        with open(real, 'r', encoding='utf-8', errors='replace') as fp:
                            content = fp.read()
                        if '#if __has_builtin(__yield)' in content and '!defined(__clang__)' not in content:
                            print(f'Patching {real} for clang arm64 compatibility', flush=True)
                            content = content.replace(
                                '#if __has_builtin(__yield)',
                                '#if __has_builtin(__yield) && !defined(__clang__)'
                            )
                            with open(real, 'w', encoding='utf-8') as fp:
                                fp.write(content)
                    except Exception as e:
                        print(f'Note: failed to patch {p}: {e}', flush=True)

    def fix_macos_toolchain():
        # The prebuilt macos tarball provides universal libraries, but a few tool binaries
        # (cmake, ninja, node, etc.) are x86_64 only. On arm64 runners, replace or remove
        # them so the runner's native arm64 tools are used.
        import platform
        is_arm = platform.machine() == 'arm64' or 'arm64' in platform.processor()
        for b in ('cmake', 'ccmake', 'cpack', 'ctest', 'ninja', 'node', 'nasm', 'ndisasm'):
            bp = os.path.join(SW, 'bin', b)
            sys_bin = shutil.which(b)
            if sys_bin and os.path.abspath(sys_bin).startswith(os.path.abspath(SW)):
                sys_bin = None
            if not sys_bin:
                for cand in (f'/opt/homebrew/bin/{b}', f'/usr/local/bin/{b}', f'/usr/bin/{b}'):
                    if os.path.isfile(cand) and os.access(cand, os.X_OK):
                        sys_bin = cand
                        break
            if is_arm:
                if os.path.lexists(bp):
                    if os.path.islink(bp) and sys_bin and os.path.realpath(bp) == os.path.realpath(sys_bin):
                        continue
                    try:
                        os.remove(bp)
                    except OSError:
                        pass
                if sys_bin:
                    try:
                        os.symlink(sys_bin, bp)
                    except OSError:
                        pass
        patch_macos_headers()

    def install_env():
        setenv('SWBASE', SWBASE)
        setenv('SW', SW)
        fix_macos_toolchain()
        patch_macos_headers()
        py_framework_dirs = (
            glob.glob(f'{SW}/python/Python.framework/Versions/*/bin') +
            glob.glob(f'{SW}/Python.framework/Versions/*/bin')
        )
        py_bin_dirs = ':'.join(py_framework_dirs) if py_framework_dirs else '$SW/python/Python.framework/Versions/3.14/bin'
        for pydir in py_framework_dirs:
            for name in os.listdir(pydir):
                target = os.path.join(pydir, name)
                link = os.path.join(SW, 'bin', name)
                if os.path.lexists(link) and not os.path.exists(link):
                    try:
                        os.remove(link)
                    except OSError:
                        pass
                if not os.path.lexists(link):
                    try:
                        os.symlink(target, link)
                    except OSError:
                        pass
            for name in ('python', 'python3'):
                target = os.path.join(pydir, 'python3')
                if not os.path.exists(target):
                    target = os.path.join(pydir, 'python')
                if os.path.exists(target):
                    link = os.path.join(SW, 'bin', name)
                    if os.path.lexists(link):
                        try:
                            os.remove(link)
                        except OSError:
                            pass
                    try:
                        os.symlink(target, link)
                    except OSError:
                        pass
        setenv(
            'PATH',
            f'$SW/bin:$SW/qt/bin:{py_bin_dirs}:$PWD/node_modules/.bin:$PATH'
        )
        setenv('CFLAGS', '-I$SW/include -Wno-error=implicit-function-declaration')
        setenv('CXXFLAGS', '-I$SW/include -Wno-error=implicit-function-declaration')
        setenv('LDFLAGS', '-L$SW/lib')
        setenv('QMAKE', '$SW/qt/bin/qmake')
        setenv('QTWEBENGINE_DISABLE_SANDBOX', '1')
        setenv('QT_PLUGIN_PATH', '$SW/qt/plugins')
        old = os.environ.get('DYLD_FALLBACK_LIBRARY_PATH', '')
        if old:
            old += ':'
        setenv('DYLD_FALLBACK_LIBRARY_PATH', old + '$SW/lib:$SW/ffmpeg/lib:$SW/qt/lib')
        setenv('DYLD_FRAMEWORK_PATH', '$SW/qt/lib')
        setenv('PKG_CONFIG_PATH', '$SW/lib/pkgconfig')
        setenv('CALIBRE_ESPEAK_DATA_DIR', '$SW/share/espeak-ng-data')
        setenv('ESPEAK_DATA_PATH', '$SW/share/espeak-ng-data')
else:

    SWBASE = '/sw'
    SW = SWBASE + '/sw'

    def install_env():
        setenv('SW', SW)
        setenv('PATH', '$SW/bin:$PATH')
        setenv('CFLAGS', '-I$SW/include')
        setenv('LDFLAGS', '-L$SW/lib')
        setenv('LD_LIBRARY_PATH', '$SW/qt/lib:$SW/ffmpeg/lib:$SW/lib')
        setenv('PKG_CONFIG_PATH', '$SW/qt/lib/pkgconfig:$SW/ffmpeg/lib/pkgconfig:$SW/lib/pkgconfig')
        setenv('QMAKE', '$SW/qt/bin/qmake')
        setenv('CALIBRE_QT_PREFIX', '$SW/qt')
        setenv('CALIBRE_ESPEAK_DATA_DIR', '$SW/share/espeak-ng-data')
        setenv('ESPEAK_DATA_PATH', '$SW/share/espeak-ng-data')


def run(*args, timeout=600):
    if len(args) == 1:
        args = shlex.split(args[0])
    print(' '.join(args), flush=True)
    p = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace', bufsize=1)
    recent_lines = []
    for line in iter(p.stdout.readline, ''):
        sys.stdout.write(line)
        sys.stdout.flush()
        recent_lines.append(line.rstrip())
        if len(recent_lines) > 200:
            recent_lines.pop(0)
    p.stdout.close()
    try:
        ret = p.wait(timeout=timeout)
    except subprocess.TimeoutExpired as err:
        ret = 1
        print(err, file=sys.stderr, flush=True)
        print('Timed out running:', ' '.join(args), flush=True, file=sys.stderr)
        p.kill()

    if ret != 0:
        for l in recent_lines[-60:]:
            if l:
                print(f'::error::{l}', flush=True)
        raise SystemExit(ret)


def decompress(path, dest, compression):
    run('tar', 'x' + compression + 'f', path, '-C', dest)


def download_and_decompress(url, dest, compression=None):
    if compression is None:
        compression = 'j' if url.endswith('.bz2') else 'J'
    for i in range(5):
        print('Downloading', url, '...')
        with NamedTemporaryFile() as f:
            ret = subprocess.Popen(['curl', '-fSL', url], stdout=f).wait()
            if ret == 0:
                decompress(f.name, dest, compression)
                sys.stdout.flush(), sys.stderr.flush()
                return
            time.sleep(1)
    raise SystemExit('Failed to download ' + url)


def install_qt_source_code():
    dest = os.path.expanduser('~/qt-base')
    os.mkdir(dest)
    download_and_decompress('https://download.calibre-ebook.com/qtbase-everywhere-src-6.4.2.tar.xz', dest, 'J')
    qdir = glob.glob(dest + '/*')[0]
    os.environ['QT_SRC'] = qdir


def run_python(*args):
    python = os.path.expandvars('$SW/bin/python')
    if not os.path.exists(python):
        for cand in (
            glob.glob(os.path.expandvars('$SW/python/Python.framework/Versions/*/bin/python3')) +
            glob.glob(os.path.expandvars('$SW/Python.framework/Versions/*/bin/python3'))
        ):
            if os.path.exists(cand):
                python = cand
                break
    if len(args) == 1:
        args = shlex.split(args[0])
    args = [python] + list(args)
    return run(*args)


def install_linux_deps():
    run('sudo', 'apt-get', 'update', '-y')
    # run('sudo', 'apt-get', 'upgrade', '-y')
    run('sudo', 'apt-get', 'install', '-y',
        'gettext', 'libgl1-mesa-dev', 'libxkbcommon-dev', 'libxkbcommon-x11-dev', 'libfreetype-dev', 'pulseaudio', 'libasound2t64', 'libflite1', 'libspeechd2')


def get_tx():
    url = 'https://github.com/transifex/cli/releases/latest/download/tx-linux-amd64.tar.gz'
    print('Downloading:', url)
    with urlopen(url) as f:
        raw = f.read()
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r') as tf:
        tf.extract('tx')


def main():
    if iswindows:
        import runpy
        m = runpy.run_path('setup/win-ci.py')
        return m['main']()
    action = sys.argv[1]
    if action == 'install':
        run('sudo', 'mkdir', '-p', SW)
        run('sudo', 'chown', '-R', os.environ['USER'], SWBASE)

        tball = 'macos-64' if ismacos else 'linux-64'
        download_and_decompress(
            f'https://download.calibre-ebook.com/ci/calibre7/{tball}.tar.xz', SW
        )
        if ismacos:
            fix_macos_toolchain()
        if not ismacos:
            install_linux_deps()

    elif action == 'bootstrap':
        install_env()
        run_python('setup.py bootstrap --ephemeral')

    elif action == 'pot':
        transifexrc = '''\
[https://www.transifex.com]
api_hostname  = https://api.transifex.com
rest_hostname = https://rest.api.transifex.com
hostname = https://www.transifex.com
password = PASSWORD
token = PASSWORD
username = api
'''.replace('PASSWORD', os.environ['tx'])
        with open(os.path.expanduser('~/.transifexrc'), 'w') as f:
            f.write(transifexrc)
        install_qt_source_code()
        install_env()
        get_tx()
        os.environ['TX'] = os.path.abspath('tx')
        run(sys.executable, 'setup.py', 'pot', timeout=30 * 60)
    elif action == 'test':
        os.environ['CI'] = 'true'
        os.environ['OPENSSL_MODULES'] = os.path.join(SW, 'lib', 'ossl-modules')
        os.environ['PIPER_TTS_DIR'] = os.path.join(SW, 'piper')
        if ismacos:
            os.environ['SSL_CERT_FILE'] = os.path.abspath(
                'resources/mozilla-ca-certs.pem')
            # needed to ensure correct libxml2 is loaded
            os.environ['DYLD_INSERT_LIBRARIES'] = ':'.join(os.path.join(SW, 'lib', x) for x in 'libxml2.dylib libxslt.dylib libexslt.dylib'.split())
            os.environ['OPENSSL_ENGINES'] = os.path.join(SW, 'lib', 'engines-3')

        install_env()
        run_python('setup.py test')
        run_python('setup.py test_rs')
    else:
        raise SystemExit(f'Unknown action: {action}')


if __name__ == '__main__':
    main()
