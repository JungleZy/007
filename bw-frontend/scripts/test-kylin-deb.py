import importlib.util
import io
import tarfile
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('kylin_deb', Path(__file__).with_name('build-kylin-deb.py'))
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)

class KylinPackageTests(unittest.TestCase):
    def test_every_parent_exists_before_unpacking_files(self):
        names = ['opt/app/bin/config.json', 'opt/app/bin/java/bin/java',
                 'usr/bin/app', 'usr/share/applications/app.desktop']
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode='w') as archive:
            package.add_parent_directories(archive, names)
            for name in names:
                package.add_bytes(archive, name, b'payload')
        stream.seek(0)
        with tempfile.TemporaryDirectory() as directory, tarfile.open(fileobj=stream) as archive:
            root = Path(directory)
            for member in archive:
                target = root / member.name
                self.assertTrue(target.parent.is_dir(), member.name)
                if member.isdir():
                    self.assertEqual(member.mode, 0o755)
                    self.assertEqual((member.uid, member.gid), (0, 0))
                    target.mkdir()  # No implicit parent creation, like dpkg unpacking.
                else:
                    target.write_bytes(archive.extractfile(member).read())
            self.assertEqual((root / names[0]).read_bytes(), b'payload')

    def test_arm64_executables_get_execute_permission_and_reject_other_architectures(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'java'
            data = bytearray(64)
            data[:6] = b'\x7fELF\x02\x01'
            struct.pack_into('<H', data, 18, 183)
            file.write_bytes(data)
            self.assertEqual(package.file_mode('runtime/bin/java', file), (0o755, True))
            struct.pack_into('<H', data, 18, 62)
            file.write_bytes(data)
            with self.assertRaisesRegex(ValueError, 'Non-ARM64'): package.file_mode('wrong', file)
            file.write_bytes(b'MZ' + bytes(62))
            with self.assertRaisesRegex(ValueError, 'Windows binary'): package.file_mode('wrong.exe', file)

    def test_deb_ar_header_and_members_follow_debian_format(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'marker').write_bytes(b'2.0\n')
            (root / 'odd').write_bytes(b'abc')
            package.write_ar(root / 'test.deb', [('debian-binary', root / 'marker'), ('control.tar.gz', root / 'odd')])
            data = (root / 'test.deb').read_bytes()
            self.assertEqual(data[:8], b'!<arch>\n')
            self.assertEqual(data[8:24].strip(), b'debian-binary/')
            self.assertEqual(data[68:72], b'2.0\n')
            self.assertEqual(data[-4:], b'abc\n')

    def test_launcher_and_uninstall_preserve_unprivileged_data(self):
        self.assertIn('id -u', package.LAUNCHER)
        self.assertIn('/opt/nip-traffic-system-kylin-arm64/nip-traffic-system', package.LAUNCHER)
        self.assertNotIn('rm ', package.POSTRM)
        self.assertNotIn('mysqld', package.POSTINST)
        self.assertIn('libc6 (>= 2.28)', package.DEPENDENCIES)

if __name__ == '__main__': unittest.main()
