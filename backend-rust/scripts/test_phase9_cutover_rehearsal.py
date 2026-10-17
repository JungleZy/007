#!/usr/bin/env python3
"""Safety/transition contracts only; real HTTP/DB evidence comes from the rehearsal, not these tests."""
import importlib.util
from pathlib import Path
import socket
import threading
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location('cutover_rehearsal', Path(__file__).with_name('phase9-cutover-rehearsal.py'))
CUTOVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CUTOVER)
PORTS = {'java': 18141, 'rust': 18142, 'gateway': 18140}


class SafetyTest(unittest.TestCase):
    def test_only_run_specific_global_rule_schemas_can_be_owned(self):
        valid = 'rust_cutover_global_rule_0123456789abcdef'
        self.assertEqual(valid, CUTOVER.Mysql(valid).schema)
        for schema in ('project006', 'rust_parity_java_local', 'rust_cutover_global_rule_', valid + ';DROP DATABASE project006'):
            with self.subTest(schema=schema), self.assertRaises(CUTOVER.Failure):
                CUTOVER.Mysql(schema)

    def test_cleanup_never_drops_a_schema_it_did_not_create(self):
        database = CUTOVER.Mysql('rust_cutover_global_rule_0123456789abcdef')
        database.sql = Mock()
        self.assertEqual('not created; untouched', database.drop())
        database.sql.assert_not_called()
        database.created = True
        database.drop()
        database.sql.assert_called_once_with('DROP DATABASE `rust_cutover_global_rule_0123456789abcdef`;', False)
        database.drop()
        self.assertEqual(1, database.sql.call_count)

    def test_failed_create_does_not_claim_an_existing_schema(self):
        database = CUTOVER.Mysql('rust_cutover_global_rule_0123456789abcdef')
        database.sql = Mock(side_effect=CUTOVER.Failure('already exists'))
        with patch.object(CUTOVER, 'command', return_value='CREATE TABLE t_example (id INT);'):
            with self.assertRaises(CUTOVER.Failure):
                database.create()
        self.assertFalse(database.created)
        database.drop()
        self.assertEqual(1, database.sql.call_count)

    def test_occupied_or_protected_ports_fail_without_stopping_anything(self):
        for ports in ([18001], [18002], [18121], [18122], [18140, 18140]):
            with self.subTest(ports=ports), self.assertRaises(CUTOVER.Failure):
                CUTOVER.check_ports(ports)
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            with self.assertRaisesRegex(CUTOVER.Failure, 'occupied'):
                CUTOVER.check_ports([listener.getsockname()[1]])



class TransitionTest(unittest.TestCase):
    def test_worker_identity_uses_master_pid_and_start_ticks(self):
        runner = CUTOVER.Rehearsal.__new__(CUTOVER.Rehearsal)
        runner.processes = {'gateway': SimpleNamespace(pid=42, poll=lambda: None)}
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for pid, parent, name, started in ((101, 42, 'nginx', 300), (102, 99, 'nginx', 301), (103, 42, 'sleep', 302)):
                path = Path(directory) / str(pid)
                fields = ['S', str(parent), *(['0'] * 17), str(started)]
                path.write_text(f'{pid} ({name}) ' + ' '.join(fields))
                paths.append(path)
            with patch.object(CUTOVER.Path, 'glob', return_value=paths):
                self.assertEqual({'101:300'}, runner.workers())
                paths[0].write_text('101 (nginx) ' + ' '.join(['S', '42', *(['0'] * 17), '400']))
                self.assertEqual({'101:400'}, runner.workers())

    def harness(self):
        runner = CUTOVER.Rehearsal.__new__(CUTOVER.Rehearsal)
        directory = tempfile.TemporaryDirectory(prefix='cutover-transition-')
        self.addCleanup(directory.cleanup)
        runner.work = Path(directory.name)
        runner.args = SimpleNamespace(drain_timeout=0)
        runner.owner = 'java'
        runner.report = {'transitions': []}
        runner.stop = threading.Event()
        runner.workers = Mock(return_value={'11:100'})
        runner.reload = Mock()
        return runner

    def test_drain_timeout_keeps_frozen_and_never_publishes_next_owner(self):
        runner = self.harness()
        frozen = threading.Event()
        with self.assertRaisesRegex(CUTOVER.Failure, 'next owner NOT published'):
            runner.switch('rust', 'cutover', frozen)
        self.assertTrue(frozen.is_set())
        runner.reload.assert_called_once_with('frozen', 'cutover-frozen')
        self.assertEqual('java', runner.owner)
        self.assertEqual('FAIL_CLOSED_DRAIN_TIMEOUT', runner.report['transitions'][0]['status'])
        self.assertTrue((runner.work / 'frozen').exists())

    def test_worker_identity_must_disappear_before_publication(self):
        runner = self.harness()
        live = {'11:100'}
        runner.workers.side_effect = lambda: set(live)
        def reload(owner, generation):
            self.assertTrue((runner.work / 'frozen').exists())
            live.clear()
            live.add('12:120' if owner == 'frozen' else '13:130')
        runner.reload.side_effect = reload
        transition = runner.switch('rust', 'cutover', threading.Event())
        self.assertEqual([('frozen', 'cutover-frozen'), ('rust', 'cutover-rust')],
                         [call.args for call in runner.reload.call_args_list])
        self.assertEqual('COMPLETED', transition['status'])
        self.assertLessEqual(transition['drained_ns'], transition['admitted_ns'])
        self.assertFalse((runner.work / 'frozen').exists())
        self.assertEqual('rust', runner.owner)

    def test_cancelled_or_failed_freeze_never_publishes_next_owner(self):
        for fail_reload in (False, True):
            runner = self.harness()
            runner.reload.side_effect = CUTOVER.Failure('reload failed') if fail_reload else lambda *_: runner.stop.set()
            with self.subTest(fail_reload=fail_reload), self.assertRaises(CUTOVER.Failure):
                runner.switch('rust', 'cutover', threading.Event())
            runner.reload.assert_called_once_with('frozen', 'cutover-frozen')
            self.assertEqual('java', runner.owner)
            self.assertTrue((runner.work / 'frozen').exists())


class EvidenceTest(unittest.TestCase):
    def fixture(self, owner='rust'):
        status = 503 if owner == 'frozen' else 200
        request = {'id': 'synthetic-1', 'owner': owner, 'method': 'POST', 'uri': '/api/postTrainGlobalRule/addRule',
                   'status': status, 'generation': 'cutover-' + owner}
        record = {key: value for key, value in request.items() if key != 'owner'}
        record.update(upstream='-' if owner == 'frozen' else '127.0.0.1:18142',
                      upstream_status='-' if owner == 'frozen' else '200', worker='12')
        return request, record

    def test_exactly_one_intended_upstream_or_frozen_no_upstream(self):
        for owner in ('rust', 'frozen'):
            request, record = self.fixture(owner)
            self.assertEqual([record], CUTOVER.validate_access([request], [record], PORTS))

    def test_missing_duplicate_wrong_owner_or_retry_evidence_cannot_pass(self):
        request, record = self.fixture()
        cases = [[], [record, record], [{**record, 'upstream': '127.0.0.1:18141'}],
                 [{**record, 'upstream': '127.0.0.1:18142, 127.0.0.1:18142'}],
                 [{**record, 'generation': 'rollback-java'}], [{**record, 'upstream_status': '500'}],
                 [record, {**record, 'id': 'unaccounted-request'}]]
        for records in cases:
            with self.subTest(records=records), self.assertRaises(CUTOVER.Failure):
                CUTOVER.validate_access([request], records, PORTS)

    def test_auth_envelopes_are_exact_not_just_http_200(self):
        runner = CUTOVER.Rehearsal.__new__(CUTOVER.Rehearsal)
        payload = {'code': 204, 'data': None, 'message': '设备标识不能为空', 'description': ''}
        self.assertIsNone(runner.envelope(payload, 204))
        for changed in ({'code': 203}, {'message': 'changed'}, {'data': False}, {'description': 'changed'}):
            with self.subTest(changed=changed), self.assertRaises(CUTOVER.Failure):
                runner.envelope({**payload, **changed}, 204)

    def test_update_must_preserve_primary_key_and_creation_time(self):
        runner = CUTOVER.Rehearsal.__new__(CUTOVER.Rehearsal)
        row = {'id': 56, 'type': 2, 'level': '合成', 'accuracy': '90~100', 'description': 'old',
               'createTime': '2026-10-13 12:00:00'}
        supplied = {key: value for key, value in row.items() if key != 'createTime'}
        supplied['description'] = 'new'
        for change in ({'id': 57}, {'createTime': '2026-10-13 12:00:01'}):
            runner.expected = {56: row.copy()}
            payload = {'code': 200, 'data': [{**row, **supplied, **change}], 'message': 'ok', 'description': ''}
            with self.subTest(change=change), self.assertRaises(CUTOVER.Failure):
                runner.accept_saved([supplied], payload, 'java')

    def test_new_timestamp_allows_only_java_datetime_rounding(self):
        row = {'id': 56, 'type': 2, 'level': '合成', 'accuracy': '90~100', 'description': 'created',
               'createTime': '2026-10-13 12:00:00'}
        supplied = {key: value for key, value in row.items() if key not in ('id', 'createTime')}
        payload = {'code': 200, 'data': [row], 'message': 'ok', 'description': ''}
        runner = CUTOVER.Rehearsal.__new__(CUTOVER.Rehearsal)
        runner.report, runner.expected = {}, {}
        persisted = {**row, 'createTime': '2026-10-13 12:00:01'}
        runner.rows = lambda: [persisted]
        runner.accept_saved([supplied], payload, 'java')
        self.assertEqual('2026-10-13 12:00:01', runner.expected[56]['createTime'])
        for owner, changed in (('rust', persisted), ('java', {**row, 'createTime': '2026-10-13 12:00:02'}),
                               ('java', {**row, 'accuracy': '0~0'})):
            runner.expected = {}
            runner.rows = lambda: [changed]
            with self.subTest(owner=owner, changed=changed), self.assertRaises(CUTOVER.Failure):
                runner.accept_saved([supplied], payload, owner)


if __name__ == '__main__':
    unittest.main()
