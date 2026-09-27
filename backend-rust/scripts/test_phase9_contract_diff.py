#!/usr/bin/env python3
"""Regression coverage for the real shared-contract command-line comparator."""
import http.server
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest


class ContractDiffTest(unittest.TestCase):
    def compare(self, left, right, left_status=200, right_status=200):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                payload, status = (left, left_status) if self.path.startswith('/java/') else (right, right_status)
                self.send_response(status)
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, *_):
                pass

        runner = Path(__file__).with_name('phase9-contract-diff.sh')
        with http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                with tempfile.TemporaryDirectory() as directory:
                    fixture = Path(directory) / 'fixture.json'
                    fixture.write_text(json.dumps([{'method': 'GET', 'path': '/case'}]), encoding='utf-8')
                    base = f'http://127.0.0.1:{server.server_port}'
                    return subprocess.run(
                        ['bash', str(runner), base + '/java', base + '/rust', str(fixture)],
                        capture_output=True, text=True, timeout=10,
                    )
            finally:
                server.shutdown()
                thread.join()

    def test_invalid_or_empty_json_cannot_match_itself(self):
        for body in (b'not-json', b'', b'{}{}'):
            with self.subTest(body=body):
                result = self.compare(body, body)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn('invalid JSON response', result.stderr)
                self.assertNotIn('PASS[', result.stdout)

    def test_http_status_mismatch_fails_even_with_equal_json(self):
        result = self.compare(b'{"code":500}', b'{"code":500}', 200, 500)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Java HTTP 200, Rust HTTP 500', result.stderr)

    def test_object_key_order_does_not_hide_array_order_or_scalar_type(self):
        equal = self.compare(b'{"a":1,"b":[2,3]}', b'{"b":[2,3],"a":1}')
        self.assertEqual(0, equal.returncode, equal.stderr)
        for body in (b'{"a":1,"b":[3,2]}', b'{"a":"1","b":[2,3]}'):
            with self.subTest(body=body):
                different = self.compare(b'{"a":1,"b":[2,3]}', body)
                self.assertNotEqual(0, different.returncode)

    def test_json_null_is_valid_not_an_empty_response(self):
        result = self.compare(b'null', b'null')
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
