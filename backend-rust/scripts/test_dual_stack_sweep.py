#!/usr/bin/env python3
"""Regression coverage for opt-in dual-stack journey response comparisons."""
import copy
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("dual_stack_sweep", Path(__file__).with_name("dual-stack-sweep.py"))
sweep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sweep)


class JourneyComparisonTest(unittest.TestCase):
    def equivalent(self, left, right, step):
        left, right = copy.deepcopy(left), copy.deepcopy(right)
        sweep.prepare_journey_response(left, step)
        sweep.prepare_journey_response(right, step)
        job = {"endpoint": "journey:test/read", "variant": "journey", "who": "admin", "path": "/read"}
        return sweep.comparison_entry(job, {"status": 200, "json": left}, {"status": 200, "json": right}, {}) is None

    def test_unordered_is_opt_in_and_preserves_multiset_counts_and_values(self):
        left = {"data": [{"key": "a", "value": "1"}, {"key": "b", "value": "2"}, {"key": "a", "value": "1"}]}
        right = {"data": [left["data"][1], left["data"][0], left["data"][2]]}
        self.assertFalse(self.equivalent(left, right, {}))
        step = {"unordered": ["$.data"]}
        self.assertTrue(self.equivalent(left, right, step))
        self.assertFalse(self.equivalent(left, {"data": right["data"][:-1]}, step))
        changed = copy.deepcopy(right)
        changed["data"][0]["value"] = "different"
        self.assertFalse(self.equivalent(left, changed, step))
        self.assertFalse(self.equivalent({**left, "ordered": [1, 2]}, {**right, "ordered": [2, 1]}, step))

    def test_unordered_sorts_by_normalized_generated_identity(self):
        left = {"data": [{"id": "11111111-1111-1111-1111-111111111111", "key": "b"},
                         {"id": "99999999-9999-9999-9999-999999999999", "key": "a"}]}
        right = {"data": [{"id": "11111111-1111-1111-1111-111111111111", "key": "a"},
                          {"id": "99999999-9999-9999-9999-999999999999", "key": "b"}]}
        self.assertTrue(self.equivalent(left, right, {"unordered": ["$.data"]}))

    def test_parse_json_normalizes_nested_ids_clocks_and_object_order_only_when_selected(self):
        left_content = {"questions": [{"id": "11111111-1111-1111-1111-111111111111", "answer": "A", "score": 10}],
                        "created": "1790000000000", "nested": {"at": "2026-10-08 12:30:00"}}
        right_content = {"nested": {"at": "2026-10-09 12:30:00"}, "created": "1790000000100",
                         "questions": [{"score": 10, "answer": "A", "id": "99999999-9999-9999-9999-999999999999"}]}
        left = {"data": [{"content": json.dumps(left_content)}]}
        right = {"data": [{"content": json.dumps(right_content)}]}
        step = {"parse_json": ["$.data[*].content"]}
        self.assertFalse(self.equivalent(left, right, {}))
        self.assertTrue(self.equivalent(left, right, step))
        for field, value in [("answer", "B"), ("score", 999), ("score", "10")]:
            changed = copy.deepcopy(right_content)
            changed["questions"][0][field] = value
            self.assertFalse(self.equivalent(left, {"data": [{"content": json.dumps(changed)}]}, step))
        self.assertFalse(self.equivalent(left, {"data": [{"content": right_content}]}, step), "wire string type stays significant")
        self.assertFalse(self.equivalent({**left, "literal": json.dumps(left_content)},
                                         {**right, "literal": json.dumps(right_content)}, step))

    def test_active_exam_detail_compares_saved_candidate_answers(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in journeys["journeys"] if journey["name"] == "theory-self-test")
        by_name = {step["name"]: step for step in steps}
        saved_index = next(index for index, step in enumerate(steps) if step["name"] == "answer-realtime")
        after_save = steps[saved_index + 1]
        self.assertEqual(after_save["name"], "detail-active-after-answer")
        for step in [by_name["detail-active"], after_save]:
            self.assertIn("$.data.user[*].content", step["parse_json"])
            self.assertNotIn("$.data.student.content", step["parse_json"])
            left = {"data": {"user": [{"content": '{"singleChoice":[{"id":"11111111-1111-1111-1111-111111111111","answer":"A","teacherScore":10}]}'}]}}
            right = {"data": {"user": [{"content": '{"singleChoice":[{"teacherScore":10,"answer":"A","id":"99999999-9999-9999-9999-999999999999"}]}'}]}}
            self.assertTrue(self.equivalent(left, right, step))
            for field, value in [("answer", "B"), ("teacherScore", 0)]:
                changed = copy.deepcopy(right)
                content = json.loads(changed["data"]["user"][0]["content"])
                content["singleChoice"][0][field] = value
                changed["data"]["user"][0]["content"] = json.dumps(content)
                self.assertFalse(self.equivalent(left, changed, step))
        for name in ["enter", "reenter-preserves-answers"]:
            self.assertIn("$.data.student.content", by_name[name]["parse_json"])

    def test_prejob_frontend_endpoints_have_student_journey_steps(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())["journeys"]
        paths = {step["path"] for journey in journeys if journey["name"].startswith("prejob-")
                 for step in journey["steps"] if step.get("who") == "student"}
        expected = {f"/api/telegraphKeyPatTrainSynthetical/{name}" for name in
                    ["save", "begin", "goTo", "finish", "findById", "lastTrain", "statisticalPage"]}
        expected.update(f"/api/telegraphKeyPatTrain/{name}" for name in ["save", "findByType", "clear"])
        expected.add("/api/telexPat/deleteTexPatByToken")
        self.assertTrue(expected <= paths, expected - paths)

    def test_prejob_launchers_replay_real_100_group_payloads(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())["journeys"]
        for journey in journeys:
            if journey["name"].startswith(("prejob-synthetical-", "prejob-student-telex-")):
                with self.subTest(journey=journey["name"]):
                    body = next(step["body"] for step in journey["steps"] if step["name"] == "create")
                    content = json.loads(body["content"])
                    self.assertEqual(len(content), 100)
                    for row in content:
                        self.assertEqual(set(row), {"text", "value", "type", "isFocus"})
                        self.assertEqual(len(row["text"]), 4)
                        self.assertEqual(row["value"], "")
                        self.assertIs(row["type"], True)
                        self.assertIs(row["isFocus"], False)

    def test_prejob_fixed_results_and_statistics_are_not_masked(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())["journeys"]
        for journey in journeys:
            if not journey["name"].startswith("prejob-synthetical-"):
                continue
            steps = {step["name"]: step for step in journey["steps"]}
            result = steps.get("detail-finished", steps.get("detail-settled"))
            statistics = steps.get("statistics-finished", steps.get("statistics-settled"))
            with self.subTest(journey=journey["name"]):
                self.assertNotIn("mask", result)
                self.assertNotIn("mask", statistics)
                left = {"data": {"duration": "60", "speed": "8.00", "totalNumber": 100,
                                 "errorNumber": 1, "accuracy": 50.0,
                                 "content": '[{"text":"1234","value":"1234","type":true,"isFocus":true}]'}}
                self.assertTrue(self.equivalent(left, left, result))
                for field, value in [("duration", "61"), ("speed", "9.00"), ("totalNumber", 99),
                                     ("errorNumber", 0), ("accuracy", 100.0),
                                     ("content", '[{"text":"1234","value":"1235","type":false,"isFocus":true}]')]:
                    changed = copy.deepcopy(left)
                    changed["data"][field] = value
                    self.assertFalse(self.equivalent(left, changed, result), field)
                totals = {"data": [{"type": 2, "totalCount": 1, "totalTime": "60000", "avgSpeed": 8}]}
                for field, value in [("totalCount", 2), ("totalTime", "61000"), ("avgSpeed", 9)]:
                    changed = copy.deepcopy(totals)
                    changed["data"][0][field] = value
                    self.assertFalse(self.equivalent(totals, changed, statistics), field)

    def test_parse_json_retains_array_order_and_rejects_invalid_json(self):
        step = {"parse_json": ["$.data.content"]}
        self.assertFalse(self.equivalent({"data": {"content": "[1,2]"}}, {"data": {"content": "[2,1]"}}, step))
        with self.assertRaises(json.JSONDecodeError):
            sweep.prepare_journey_response({"data": {"content": "not json"}}, step)
        self.assertFalse(self.equivalent({"data": {"content": "null"}}, {"data": {"content": None}}, step))
        self.assertFalse(self.equivalent({"data": {"content": "[]"}}, {"data": {}}, step))

    def test_paths_are_selective_and_missing_paths_do_not_erase_differences(self):
        step = {"unordered": ["$.data[0].rows"], "mask": ["$.data[0].duration"]}
        left = {"data": [{"rows": [1, 2], "duration": 1}, {"rows": [1, 2]}]}
        right = {"data": [{"rows": [2, 1], "duration": 2}, {"rows": [2, 1]}]}
        self.assertFalse(self.equivalent(left, right, step))
        self.assertFalse(self.equivalent({"data": {"rows": []}}, {"data": {}}, {"unordered": ["$.data.rows"]}))
        self.assertFalse(self.equivalent({"data": {"rows": []}}, {"data": {"rows": "[]"}}, {"unordered": ["$.data.rows"]}))

    def study_key(self, identity, clock, credit="2.5", title="通信基础"):
        return (f"TheoryKnowledgeEntity(id={identity}, type=0, status=1, cover=study.jpg, title={title}, "
                f"createUserId=sweep-admin, createTime={clock}, credit={credit}, difficultyId=null, specialtyId=null)")

    def test_entity_key_normalization_is_opt_in_and_path_scoped(self):
        first = self.study_key("11111111-1111-1111-1111-111111111111", "1790000000000")
        second = self.study_key("99999999-9999-9999-9999-999999999999", "2026-10-09 12:30:00")
        left, right = {"data": {"down": {first: {"通信原理": 3601}}}}, {"data": {"down": {second: {"通信原理": 3601}}}}
        step = {"normalize_keys": ["$.data.down"]}
        self.assertFalse(self.equivalent(left, right, {}))
        self.assertTrue(self.equivalent(left, right, step))
        self.assertFalse(self.equivalent({**left, "literal": first}, {**right, "literal": second}, step))
        for credit, title in [("0.0", "通信基础"), ("2.5", "其他知识")]:
            changed = {"data": {"down": {self.study_key("99999999-9999-9999-9999-999999999999", "1790000000000", credit, title): {"通信原理": 3601}}}}
            self.assertFalse(self.equivalent(left, changed, step))
        self.assertFalse(self.equivalent(left, {"data": {"down": {second: {"通信原理": 3600}}}}, step))

    def test_entity_key_normalization_never_merges_distinct_keys(self):
        first = self.study_key("11111111-1111-1111-1111-111111111111", "1790000000000")
        second = self.study_key("99999999-9999-9999-9999-999999999999", "1790000000100")
        entries = sweep.normalized_entity_keys({first: {"课件": 1}, second: {"课件": 1}})["<normalized-entity-key-entries>"]
        self.assertEqual(2, len(entries))
        self.assertEqual(sweep.normalize(entries[0]), sweep.normalize(entries[1]))
        step = {"normalize_keys": ["$.data.down"]}
        self.assertFalse(self.equivalent({"data": {"down": {first: {"课件": 1}, second: {"课件": 1}}}},
                                         {"data": {"down": {first: {"课件": 1}}}}, step))
        self.assertFalse(self.equivalent({"data": {"down": {first: {"课件": 1}, second: {"课件": 2}}}},
                                         {"data": {"down": {first: {"课件": 1}, second: {"课件": 3}}}}, step))

    def test_entity_key_normalization_leaves_nonmatching_keys_untouched(self):
        nonmatching = {"plain-uuid-11111111-1111-1111-1111-111111111111": 1, "TheoryKnowledgeEntity(id=123)": 2}
        self.assertEqual(nonmatching, sweep.normalized_entity_keys(nonmatching))
        first = self.study_key("11111111-1111-1111-1111-111111111111", "1790000000000")
        entries = sweep.normalized_entity_keys({first: {}, **nonmatching})["<normalized-entity-key-entries>"]
        for key, item in nonmatching.items():
            self.assertIn([key, item], entries)
        plain = self.study_key("fixed-id", "fixed-time")
        self.assertEqual("fixed-id", sweep.normalized_entity_keys({plain: 1})["<normalized-entity-key-entries>"][0][0]["<entity-key>"]["id"])

    def test_scheduled_candidate_journey_preserves_named_query_order(self):
        spec = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in spec["journeys"] if journey["name"] == "theory-scheduled-exam")
        listing = next(step for step in steps if step["name"] == "list-scheduled-candidate")
        self.assertNotIn("$.data.exam", listing.get("unordered", []))
        starts = {step["body"]["startTime"] for step in steps
                  if step["path"].endswith("/savetheoryKnowledgeExam") and "id" not in step["body"] and "sweep-student" in step["body"]["stuId"]}
        self.assertGreaterEqual(len(starts), 2)
        response = {"data": {"exam": [{"startTime": start, "title": f"distinct-exam-{index}"}
                                      for index, start in enumerate(sorted(starts, reverse=True))]}}
        reversed_response = copy.deepcopy(response)
        reversed_response["data"]["exam"].reverse()
        self.assertFalse(self.equivalent(response, reversed_response, listing))

    def test_fixed_journey_seed_runs_only_on_both_isolated_databases(self):
        seeded = []
        db = SimpleNamespace(dbs=("rust_parity_java_test", "rust_parity_rust_test"),
                             run=lambda database, sql: seeded.append((database, sql)))
        spec = {"journeys": [{"name": "fixed-ids", "seed_sql": "INSERT fixture", "steps": []}]}
        self.assertEqual([], sweep.run_journeys(spec, None, {}, db))
        self.assertEqual([(name, "INSERT fixture") for name in db.dbs], seeded)

    def test_step_fixture_seeds_both_databases_before_http_and_preserves_order_assertions(self):
        events = []
        db = SimpleNamespace(dbs=("rust_parity_java_test", "rust_parity_rust_test"),
                             run=lambda database, sql: events.append((database, sql)))
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in journeys["journeys"] if journey["name"] == "training-list-pagination")
        listings = [step for step in steps if step["name"] in ("post-ticker-2-rows-page-1", "post-ticker-3-rows-page-1")]
        self.assertEqual(2, len(listings))

        def http_case(base, *args):
            events.append((base, "http"))
            return {"status": 200, "json": {"code": 200, "data": []}}

        for listing in listings:
            self.assertNotIn("$.data.data", listing.get("unordered", []))
            events.clear()
            with patch.object(sweep, "http_case", side_effect=http_case):
                sweep.run_journeys({"journeys": [{"name": "clock-fixture", "steps": [listing]}]}, args, {"student": {}}, db)
            self.assertEqual([(database, listing["seed_sql"]) for database in db.dbs] + [("java", "http"), ("rust", "http")], events)
            left = {"data": {"data": [{"name": "second"}, {"name": "first"}]}}
            right = {"data": {"data": [{"name": "first"}, {"name": "second"}]}}
            self.assertFalse(self.equivalent(left, right, listing), "deterministic clocks do not exempt reordered results")

    def test_message_body_lists_have_deterministic_clocks_without_masking_projection_or_order(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in journeys["journeys"] if journey["name"] == "post-ticker-list-message-body")
        listings = [step for step in steps if step["name"] in ("list-populated-body", "list-null-body", "list-absent-body")]
        self.assertEqual(3, len(listings))
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        for listing in listings:
            events = []
            db = SimpleNamespace(dbs=("rust_parity_java_test", "rust_parity_rust_test"),
                                 run=lambda database, sql: events.append((database, sql)))

            def http_case(base, *args):
                events.append((base, "http"))
                return {"status": 200, "json": {"code": 200, "data": []}}

            with patch.object(sweep, "http_case", side_effect=http_case):
                sweep.run_journeys({"journeys": [{"name": "body-clock-fixture", "steps": [listing]}]}, args, {"admin": {}}, db)
            self.assertEqual([(database, listing["seed_sql"]) for database in db.dbs] + [("java", "http"), ("rust", "http")], events)
            rows = [{"name": "列表非空报文", "codeMessageBody": None}, {"name": "列表空报文", "codeMessageBody": []}]
            document = {"data": {"data": rows}}
            self.assertTrue(self.equivalent(document, document, listing))
            self.assertFalse(self.equivalent(document, {"data": {"data": list(reversed(rows))}}, listing))
            changed = copy.deepcopy(document)
            changed["data"]["data"][1]["codeMessageBody"] = None
            self.assertFalse(self.equivalent(document, changed, listing), "[] versus null projection remains significant")

    def test_ticker_finish_uses_identical_closed_active_history_without_masking_seconds(self):
        journeys = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in journeys["journeys"] if journey["name"] == "personal-ticker-tape")
        finish = next(step for step in steps if step["name"] == "finish")
        self.assertIn("accumulated_active_millis=3500,active_since=NULL", finish["seed_sql"])
        events = []
        db = SimpleNamespace(dbs=("rust_parity_java_test", "rust_parity_rust_test"),
                             run=lambda database, sql: events.append((database, sql)))
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)

        def http_case(base, *args):
            events.append((base, "http"))
            return {"status": 200, "json": {"code": 200}}

        with patch.object(sweep, "http_case", side_effect=http_case):
            sweep.run_journeys({"journeys": [{"name": "active-history", "steps": [finish]}]}, args, {"admin": {}}, db)
        self.assertEqual([(database, finish["seed_sql"]) for database in db.dbs] + [("java", "http"), ("rust", "http")], events)
        for name in ("get-by-id-after", "statistics-after", "list-after-third"):
            step = next(step for step in steps if step["name"] == name)
            left = {"data": {"validTime": "3", "rows": [{"totalTime": "3"}]}}
            self.assertFalse(self.equivalent(left, {"data": {"validTime": "4", "rows": [{"totalTime": "3"}]}}, step))
            self.assertFalse(self.equivalent(left, {"data": {"validTime": "3", "rows": [{"totalTime": "4"}]}}, step))

    def test_run_journeys_captures_original_content_before_transforms(self):
        original = '{"value": 1}'
        calls = []

        def http_case(base, method, path, body, headers, timeout):
            calls.append(body)
            return {"status": 200, "json": {"code": 200, "data": {"content": original}}}

        journey = {"journeys": [{"name": "capture", "steps": [
            {"name": "read", "method": "POST", "path": "/read", "capture": {"content": "$.data.content"},
             "parse_json": ["$.data.content"]},
            {"name": "write", "method": "POST", "path": "/write", "body": {"content": "${content}"}},
        ]}]}
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        with patch.object(sweep, "http_case", side_effect=http_case):
            results = sweep.run_journeys(journey, args, {"admin": {}}, None)
        self.assertEqual([{"content": original}, {"content": original}], calls[2:])
        self.assertEqual({"<parsed-json-string>": {"value": 1}}, results[0][1]["json"]["data"]["content"])
        self.assertEqual(original, results[1][1]["json"]["data"]["content"])


class FloorContentComparisonTest(unittest.TestCase):
    java_id = "11111111-1111-1111-1111-111111111111"
    rust_id = "99999999-9999-9999-9999-999999999999"

    def document(self, floor_id):
        return {"code": 200, "data": {floor_id: [
            {"id": "22222222-2222-2222-2222-222222222222", "floorId": floor_id, "sort": 0, "moresKey": "E", "moresValue": "[]", "moresTime": "[]"},
            {"id": "33333333-3333-3333-3333-333333333333", "floorId": floor_id, "sort": 1, "moresKey": "T", "moresValue": "[1]", "moresTime": "[160]"},
        ]}}

    def compare(self, java, rust, java_id=None, rust_id=None):
        responses = []
        for document, floor_id in [(java, java_id or self.java_id), (rust, rust_id or self.rust_id)]:
            response = {"status": 200, "json": copy.deepcopy(document)}
            errors = sweep.prepare_journey_response(response["json"], {"floor_content": "${floor}"}, {"floor": floor_id})
            if errors:
                response["comparison_errors"] = errors
            responses.append(response)
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        job = {"endpoint": "journey:personal-telegram/floor-content", "variant": "journey", "who": "admin", "path": "/floor"}
        return sweep.comparison_entry(job, *responses, registry)

    def test_captured_floor_keys_and_unspecified_row_order_compare_precisely(self):
        java, rust = self.document(self.java_id), self.document(self.rust_id)
        java["data"][self.java_id].reverse()
        self.assertIsNone(self.compare(java, rust))
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        self.assertFalse(any(rule["endpoint"] == "journey:personal-telegram/floor-content" for rule in registry["overrides"]))
        self.assertTrue(all("allowed_paths" in rule for rule in registry["overrides"] if rule["category"] == "DATA"))
        self.assertFalse(sweep.allowed_data_paths({"category": "DATA", "data_diffs": []}, {}))

    def test_all_fields_morse_content_and_duplicate_inventory_remain_significant(self):
        java, rust = self.document(self.java_id), self.document(self.rust_id)
        for field, value in [("moresKey", "A"), ("moresValue", "[0]"), ("moresTime", "[40]"), ("sort", 9), ("sort", False)]:
            with self.subTest(field=field, value=value):
                changed = copy.deepcopy(rust)
                changed["data"][self.rust_id][0][field] = value
                entry = self.compare(java, changed)
                self.assertEqual("DATA", entry["category"])
                self.assertEqual("FAIL", entry["disposition"])
        for mutation in ["missing", "extra", "duplicate", "missing-field", "extra-field"]:
            changed = copy.deepcopy(rust)
            rows = changed["data"][self.rust_id]
            if mutation == "missing":
                rows.pop()
            elif mutation == "extra":
                rows.append({**rows[0], "moresKey": "A"})
            elif mutation == "duplicate":
                rows.append(copy.deepcopy(rows[0]))
            elif mutation == "missing-field":
                del rows[0]["moresTime"]
            else:
                rows[0]["unexpected"] = 1
            self.assertEqual("FAIL", self.compare(java, changed)["disposition"], mutation)

    def test_bad_keys_and_references_fail_even_if_both_wire_responses_match(self):
        original = self.document(self.java_id)
        for mutation in ["wrong-key", "extra-key", "wrong-reference", "missing-reference", "non-array", "non-object"]:
            changed = copy.deepcopy(original)
            data = changed["data"]
            if mutation == "wrong-key":
                data[self.rust_id] = data.pop(self.java_id)
            elif mutation == "extra-key":
                data[self.rust_id] = []
            elif mutation == "wrong-reference":
                data[self.java_id][0]["floorId"] = self.rust_id
            elif mutation == "missing-reference":
                del data[self.java_id][0]["floorId"]
            elif mutation == "non-array":
                data[self.java_id] = {}
            else:
                data[self.java_id][0] = None
            entry = self.compare(changed, changed, self.java_id, self.java_id)
            self.assertEqual("FAIL", entry["disposition"], mutation)
            self.assertTrue(entry["comparison_errors"], mutation)
        self.assertTrue(sweep.prepare_journey_response(original, {"floor_content": "${missing}"}, {}))

    def test_journey_binds_floor_mapping_to_each_stack_capture(self):
        spec = {"journeys": [{"name": "personal-telegram", "steps": [
            {"name": "capture", "method": "POST", "path": "/capture", "capture": {"floor": "$.data.id"}},
            {"name": "floor-content", "method": "POST", "path": "/floor", "body": ["${floor}"], "floor_content": "${floor}"},
        ]}]}
        calls = []

        def http_case(base, method, path, body, headers, timeout):
            floor_id = self.java_id if base == "java" else self.rust_id
            if path == "/capture":
                return {"status": 200, "json": {"code": 200, "data": {"id": floor_id}}}
            calls.append(body)
            return {"status": 200, "json": self.document(floor_id)}

        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        with patch.object(sweep, "http_case", side_effect=http_case):
            results = sweep.run_journeys(spec, args, {"admin": {}}, None)
        self.assertEqual([[self.java_id], [self.rust_id]], calls)
        self.assertIsNone(sweep.comparison_entry(*results[-1], {}))


class IdentityCorrespondenceTest(unittest.TestCase):
    java_one = "11111111-1111-1111-1111-111111111111"
    java_two = "22222222-2222-2222-2222-222222222222"
    rust_one = "88888888-8888-8888-8888-888888888888"
    rust_two = "99999999-9999-9999-9999-999999999999"

    def compare(self, java, rust, identities=None, step=None, variant="journey", path="/read"):
        java, rust = copy.deepcopy(java), copy.deepcopy(rust)
        for document in (java, rust):
            sweep.prepare_journey_response(document, step or {})
        left = {"status": 200, "json": {"code": 200, **java}}
        right = {"status": 200, "json": {"code": 200, **rust}}
        if identities is not None:
            domain = sweep.identity_domain(path)
            left["identity_diffs"] = identities.compare(left["json"], right["json"], domain=domain)
            left["comparison_json"] = identities.canonical(left["json"], "java", domain=domain)
            right["comparison_json"] = identities.canonical(right["json"], "rust", domain=domain)
        job = {"endpoint": "journey:identity/read", "variant": variant, "who": "admin", "path": path}
        return sweep.comparison_entry(job, left, right, {})

    def seeded(self):
        identities = sweep.IdentityMap()
        self.assertEqual([], identities.compare([self.java_one, self.java_two], [self.rust_one, self.rust_two]))
        return identities

    def assert_mismatch(self, entry, path):
        self.assertEqual("DATA", entry["category"])
        self.assertEqual("FAIL", entry["disposition"])
        self.assertTrue(any(diff[0] == path and len(diff) > 3 and diff[3].get("reason") == "identity mismatch" for diff in entry["data_diffs"]))

    def test_wrong_question_reference_is_not_hidden_by_uuid_shape(self):
        entry = self.compare({"data": {"answer": {"questionId": self.java_one}}},
                             {"data": {"answer": {"questionId": self.rust_two}}}, self.seeded())
        self.assert_mismatch(entry, "$.data.answer.questionId")

    def test_swapped_references_between_rows_fail(self):
        java = {"data": [{"name": "first", "questionId": self.java_one}, {"name": "second", "questionId": self.java_two}]}
        rust = {"data": [{"name": "first", "questionId": self.rust_two}, {"name": "second", "questionId": self.rust_one}]}
        entry = self.compare(java, rust, self.seeded())
        self.assert_mismatch(entry, "$.data[0].questionId")
        self.assert_mismatch(entry, "$.data[1].questionId")

    def test_reused_identifier_fails_in_both_bijection_directions(self):
        java = {"data": [{"id": self.java_one}, {"id": self.java_two}]}
        rust = {"data": [{"id": self.rust_one}, {"id": self.rust_one}]}
        for left, right in [(java, rust), (rust, java)]:
            self.assert_mismatch(self.compare(left, right), "$.data[1].id")
        entry = self.compare(java, rust, variant="empty")
        self.assertEqual("DATA", entry["category"], "standalone requests also check a response-local map")

    def test_consistent_ids_across_steps_and_literal_fixture_ids(self):
        identities = sweep.IdentityMap()
        for left, right in [({"id": self.java_one}, {"id": self.rust_one}),
                            ({"questionId": self.java_one}, {"questionId": self.rust_one}),
                            ({"rows": [{"parentId": self.java_one, "id": self.java_two}]},
                             {"rows": [{"parentId": self.rust_one, "id": self.rust_two}]})]:
            self.assertIsNone(self.compare({"data": left}, {"data": right}, identities))
        self.assertIsNone(self.compare({"data": "sweep-admin"}, {"data": "sweep-admin"}, identities))
        self.assertEqual("DATA", self.compare({"data": "sweep-admin"}, {"data": "sweep-student"}, identities)["category"])
        self.assertIsNone(self.compare({"data": {"time": "1790000000000"}}, {"data": {"time": "1790000000100"}}, identities))

    def test_unordered_references_use_canonical_rows_and_existing_mapping(self):
        java = {"data": [{"name": "one", "questionId": self.java_one}, {"name": "two", "questionId": self.java_two}]}
        rust = {"data": [{"name": "two", "questionId": self.rust_two}, {"name": "one", "questionId": self.rust_one}]}
        step = {"unordered": ["$.data"]}
        self.assertIsNone(self.compare(java, rust, self.seeded(), step))
        rust["data"][0]["questionId"] = self.rust_one
        rust["data"][1]["questionId"] = self.rust_two
        self.assert_mismatch(self.compare(java, rust, self.seeded(), step), "$.data[0].questionId")

    def test_ambiguous_unordered_entities_fail_until_identity_is_known(self):
        java = {"data": [{"id": self.java_one}, {"id": self.java_two}]}
        rust = {"data": [{"id": self.rust_two}, {"id": self.rust_one}]}
        step = {"unordered": ["$.data"]}
        entry = self.compare(java, rust, step=step)
        self.assertEqual("ambiguous identity correspondence", entry["data_diffs"][0][3]["reason"])
        self.assertIsNone(self.compare(java, rust, self.seeded(), step))
        self.assertIsNone(self.compare({"data": [java["data"][0]] * 2}, {"data": [rust["data"][1]] * 2}, step=step))

    def test_embedded_json_shares_the_outer_and_prior_step_identity_map(self):
        java = {"data": {"questions": [{"id": self.java_one}, {"id": self.java_two}],
                         "content": json.dumps({"answers": [{"questionId": self.java_one}]})}}
        rust = {"data": {"questions": [{"id": self.rust_one}, {"id": self.rust_two}],
                         "content": json.dumps({"answers": [{"questionId": self.rust_one}]})}}
        step = {"parse_json": ["$.data.content"]}
        self.assertIsNone(self.compare(java, rust, step=step))
        rust["data"]["content"] = json.dumps({"answers": [{"questionId": self.rust_two}]})
        self.assertEqual("DATA", self.compare(java, rust, step=step)["category"])
        self.assert_mismatch(self.compare(java, rust, self.seeded(), step), "$.data.content.<parsed-json-string>.answers[0].questionId")

    def test_normalized_entity_keys_preserve_identity_references(self):
        helper = JourneyComparisonTest()
        java = {"data": {"down": {helper.study_key(self.java_one, "1790000000000"): {"questionId": self.java_one}}}}
        rust = {"data": {"down": {helper.study_key(self.rust_one, "1790000000100"): {"questionId": self.rust_one}}}}
        step = {"normalize_keys": ["$.data.down"]}
        self.assertIsNone(self.compare(java, rust, self.seeded(), step))
        rust["data"]["down"] = {helper.study_key(self.rust_two, "1790000000100"): {"questionId": self.rust_one}}
        self.assert_mismatch(self.compare(java, rust, self.seeded(), step), "$.data.down.<normalized-entity-key-entries>[0][0].<entity-key>.id")

    def test_numeric_generated_ids_are_scoped_to_entity_domains_not_scores(self):
        identities = sweep.IdentityMap()
        self.assertEqual([], identities.compare({"id": 1}, {"id": 1}, domain="device"))
        self.assertEqual([], identities.compare({"id": 1}, {"id": 1}, domain="deviceType"))
        self.assertIsNone(self.compare({"data": {"deviceId": 1, "deviceTypeId": 1}},
                                      {"data": {"deviceId": 1, "deviceTypeId": 1}}, identities))
        self.assert_mismatch(self.compare({"data": {"deviceId": 1}}, {"data": {"deviceId": 2}}, identities), "$.data.deviceId")
        self.assertEqual("DATA", self.compare({"data": {"score": 1}}, {"data": {"score": 2}}, identities)["category"])

    def test_numeric_mapping_accepts_consistent_generated_pk_offsets(self):
        identities = sweep.IdentityMap()
        self.assertIsNone(self.compare({"data": {"id": 1}}, {"data": {"id": 41}}, identities, path="/api/deviceType/save"))
        self.assertIsNone(self.compare({"data": {"id": 1, "deviceTypeId": 1}},
                                      {"data": {"id": 19, "deviceTypeId": 41}}, identities, path="/api/device/save"))
        java = {"data": [{"name": "one", "deviceId": 1, "deviceTypeId": 1}, {"name": "two", "deviceId": 2, "deviceTypeId": 1}]}
        rust = {"data": [{"name": "two", "deviceId": 20, "deviceTypeId": 41}, {"name": "one", "deviceId": 19, "deviceTypeId": 41}]}
        step = {"unordered": ["$.data"]}
        self.assertIsNone(self.compare(java, rust, identities, step))
        rust["data"][0]["deviceId"] = 19
        self.assert_mismatch(self.compare(java, rust, identities, step), "$.data[1].deviceId")

    def test_group_net_device_type_alias_uses_the_same_integer_identity(self):
        identities = sweep.IdentityMap()
        self.assertIsNone(self.compare({"data": {"id": 1}}, {"data": {"id": 41}}, identities, path="/api/deviceType/save"))
        self.assertIsNone(self.compare({"data": {"id": 2, "deviceType": 1, "trainId": 2}},
                                      {"data": {"id": 52, "deviceType": 41, "trainId": 52}}, identities, path="/api/groupNetTrain/saveTrain"))
        self.assert_mismatch(self.compare({"data": {"deviceType": 1}}, {"data": {"deviceType": 42}}, identities), "$.data.deviceType")

    def test_nested_device_description_ids_do_not_alias_device_primary_keys(self):
        identities = sweep.IdentityMap()
        java = {"data": {"id": 1, "descriptions": [{"id": 1, "deviceId": 1}]}}
        rust = {"data": {"id": 19, "descriptions": [{"id": 27, "deviceId": 19}]}}
        self.assertIsNone(self.compare(java, rust, identities, path="/api/device/save"))
        self.assertIsNone(self.compare({"data": {"id": 1, "deviceId": 1}},
                                      {"data": {"id": 27, "deviceId": 19}}, identities, path="/api/device/addDeviceDescription"))
        rust["data"]["descriptions"][0]["id"] = 28
        self.assert_mismatch(self.compare(java, rust, identities, path="/api/device/listPage"), "$.data.descriptions[0].id")

    def test_capture_path_not_variable_name_binds_integer_ids_before_masking(self):
        spec = {"journeys": [{"name": "numeric", "steps": [
            {"name": "capture", "method": "POST", "path": "/api/deviceType/save", "capture": {"groupType": "$.data.id"}, "mask": ["$.data.id"]},
            {"name": "read", "method": "POST", "path": "/api/groupNetTrain/saveTrain"},
        ]}]}
        replies = [{"id": 1}, {"id": 41}, {"deviceType": 1}, {"deviceType": 42}]
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        with patch.object(sweep, "http_case", side_effect=[{"status": 200, "json": {"code": 200, "data": value}} for value in replies]):
            results = sweep.run_journeys(spec, args, {"admin": {}}, None)
        self.assertIsNone(sweep.comparison_entry(*results[0], {}))
        self.assert_mismatch(sweep.comparison_entry(*results[1], {}), "$.data.deviceType")

    def test_nested_captures_remain_raw_when_response_masks_an_id(self):
        spec = {"journeys": [{"name": "nested", "steps": [
            {"name": "capture", "method": "POST", "path": "/create", "capture": {"record": "$.data"}, "mask": ["$.data.id"]},
            {"name": "read", "method": "POST", "path": "/read", "body": "${record}"},
        ]}]}
        calls = []

        def http_case(base, method, path, body, *args):
            calls.append(body)
            identity = self.java_one if base == "java" else self.rust_one
            return {"status": 200, "json": {"code": 200, "data": {"id": identity}}}

        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        with patch.object(sweep, "http_case", side_effect=http_case):
            results = sweep.run_journeys(spec, args, {"admin": {}}, None)
        self.assertEqual([{"id": self.java_one}, {"id": self.rust_one}], calls[2:])
        self.assertTrue(all(sweep.comparison_entry(*result, {}) is None for result in results))

    def test_run_journeys_seeds_captures_before_masking_and_resets_per_journey(self):
        spec = {"journeys": [{"name": name, "steps": [
            {"name": "capture", "method": "POST", "path": "/create", "capture": {"id": "$.data.id"}, "mask": ["$.data.id"]},
            {"name": "read", "method": "POST", "path": "/read"},
        ]} for name in ("first", "second")]}
        replies = [self.java_one, self.rust_one, self.java_one, self.rust_two,
                   self.java_one, self.rust_two, self.java_one, self.rust_two]
        args = SimpleNamespace(java_url="java", rust_url="rust", timeout=1)
        with patch.object(sweep, "http_case", side_effect=[{"status": 200, "json": {"code": 200, "data": {"id": value}}} for value in replies]):
            results = sweep.run_journeys(spec, args, {"admin": {}}, None)
        self.assertIsNone(sweep.comparison_entry(*results[0], {}))
        self.assert_mismatch(sweep.comparison_entry(*results[1], {}), "$.data.id")
        self.assertIsNone(sweep.comparison_entry(*results[2], {}))
        self.assertIsNone(sweep.comparison_entry(*results[3], {}))


class ClassificationTest(unittest.TestCase):
    def compare(self, java, rust, registry=None):
        job = {"endpoint": "journey:typed/read", "variant": "journey", "who": "admin", "path": "/read"}
        java, rust = ({"status": 200, "json": {"code": 200, **document}} for document in (java, rust))
        self.assertNotEqual(sweep.comparable(java), sweep.comparable(rust))
        return sweep.comparison_entry(job, java, rust, registry or {})

    def test_nested_boolean_integer_changes_are_data_and_fail_closed(self):
        for integer, boolean in [(1, True), (0, False)]:
            with self.subTest(integer=integer):
                entry = self.compare({"data": {"rows": [{"score": integer}]}},
                                     {"data": {"rows": [{"score": boolean}]}})
                self.assertEqual("DATA", entry["category"])
                self.assertEqual("FAIL", entry["disposition"])
                self.assertEqual([["$.data.rows[0].score", integer, boolean]], entry["data_diffs"])

    def test_envelope_absent_and_null_data_preserve_presence_both_directions(self):
        for java, rust in [({}, {"data": None}), ({"data": None}, {})]:
            with self.subTest(java=java):
                entry = self.compare(java, rust)
                self.assertEqual("DATA", entry["category"])
                self.assertEqual("FAIL", entry["disposition"])
                self.assertEqual([["$.data", None, None,
                                   {"java_present": "data" in java, "rust_present": "data" in rust}]], entry["data_diffs"])

    def test_text_only_requires_zero_data_differences_even_with_override(self):
        entry = self.compare({"data": {"rows": [{"score": 1}]}, "message": "java"},
                             {"data": {"rows": [{"score": 1}]}, "message": "rust"})
        self.assertEqual("TEXT_ONLY", entry["category"])
        self.assertEqual([], entry["data_diffs"])
        self.assertEqual("ACCEPTED", entry["disposition"])
        registry = {"overrides": [{"endpoint": entry["endpoint"], "category": "TEXT_ONLY", "disposition": "ACCEPTED"}]}
        for differences in [None, [["$.data.score", 1, True]]]:
            changed = copy.deepcopy(entry)
            if differences is None:
                del changed["data_diffs"]
            else:
                changed["data_diffs"] = differences
            self.assertEqual("FAIL", sweep.disposition(changed, registry)[0])


class RegistryPredicateTest(unittest.TestCase):
    def test_full_differences_not_four_item_preview_control_acceptance(self):
        java = {f"artefact{index}": 0 for index in range(4)} | {"supportedScore": 100}
        rust = {f"artefact{index}": 1 for index in range(4)} | {"supportedScore": 99}
        differences = sweep.data_diffs(java, rust, "$.data")
        self.assertEqual(5, len(differences))
        entry = {"endpoint": "read", "variant": "journey", "category": "DATA",
                 "data_diffs": differences, "first_data_diffs": differences[:4]}
        rule = {"endpoint": "read", "category": "DATA", "disposition": "ACCEPTED",
                "allowed_paths": [f"$.data.artefact{index}" for index in range(4)],
                "path_predicates": {f"$.data.artefact{index}": {"java": {"value": 0}, "rust": {"value": 1}} for index in range(4)}}
        self.assertEqual("FAIL", sweep.disposition(entry, {"overrides": [rule]})[0])
        entry["data_diffs"] = differences[:4]
        self.assertEqual("ACCEPTED", sweep.disposition(entry, {"overrides": [rule]})[0])

    def test_exact_paths_and_explicit_subtrees(self):
        paths = ["$.data.rows[*].score", "$.data.user"]
        rule = {"allowed_paths": paths, "path_predicates": {path: {"java": {"value": 0}, "rust": {"value": 1}} for path in paths}}
        self.assertTrue(sweep.allowed_data_paths({"data_diffs": [["$.data.rows[12].score", 0, 1]]}, rule))
        for path in ["$.data.rows[12].score.value", "$.data.user.name", "$.data.rows[12].scores", "$.data.users.name", "$.data.rows[len]"]:
            self.assertFalse(sweep.allowed_data_paths({"data_diffs": [[path, 0, 1]]}, rule), path)
        rule["subtree_paths"] = ["$.data.user"]
        self.assertTrue(sweep.allowed_data_paths({"data_diffs": [["$.data.user.name", 0, 1]]}, rule))
        self.assertFalse(sweep.allowed_data_paths({"first_data_diffs": []}, rule))
        self.assertFalse(sweep.allowed_data_paths({"data_diffs": [["$.data.score", 0, 1]]}, {"allowed_paths": []}))

    def test_array_length_does_not_hide_changes_in_existing_rows(self):
        differences = sweep.data_diffs([{"title": "supported", "score": 100}],
                                       [{"title": "supported", "score": 99}, {"title": None}], "$.data")
        self.assertEqual(["$.data[len]", "$.data[0].score"], [item[0] for item in differences])
        self.assertFalse(sweep.allowed_data_paths({"data_diffs": differences}, {"allowed_paths": ["$.data[len]"]}))

    def test_empty_word_stock_accepts_all_five_dto_fields_but_not_another_supported_field(self):
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        differences = sweep.data_diffs(dict.fromkeys(["id", "name", "type", "content", "wordSize"]), {}, "$.data")
        self.assertEqual(5, len(differences))
        for variant in ["probe-str", "probe-num"]:
            entry = {"endpoint": "POST /api/postEnteringExercise/getWordStock", "variant": variant,
                     "category": "DATA", "data_diffs": differences, "first_data_diffs": differences[:4]}
            self.assertEqual("ACCEPTED", sweep.disposition(entry, registry)[0])
            entry["data_diffs"] = differences + [["$.data.supportedScore", 100, 99]]
            self.assertEqual("OPEN", sweep.disposition(entry, registry)[0])

    def test_all_state_artefacts_have_bounded_paths_and_supported_field_mutation_is_open(self):
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        artefacts = [rule for rule in registry["overrides"] if rule.get("note", "").startswith("STATE_ARTEFACT:")]
        self.assertEqual(15, len(artefacts))
        self.assertTrue(all(rule.get("allowed_paths") for rule in artefacts))
        rule = next(rule for rule in artefacts if rule["endpoint"] == "POST /api/telegramTrain/statisticalPage")
        expected = [["$.data[2].totalCount", 0, 2], ["$.data[2].totalTime", "0", "0.0"]]
        entry = {"endpoint": rule["endpoint"], "variant": rule["variant"], "category": "DATA", "data_diffs": expected}
        self.assertEqual("ACCEPTED", sweep.disposition(entry, registry)[0])
        entry["data_diffs"] = expected + [["$.data[2].avgSpeed", 50, 0]]
        self.assertEqual("OPEN", sweep.disposition(entry, registry)[0])

    def test_generated_description_offset_needs_correspondence_not_a_data_exemption(self):
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        endpoint = "POST /api/device/addDeviceDescription"
        self.assertFalse(any(rule["endpoint"] == endpoint and rule["variant"] == "probe-num" for rule in registry["overrides"]))
        job = {"endpoint": endpoint, "variant": "probe-num", "who": "admin", "path": "/api/device/addDeviceDescription"}
        java = {"status": 200, "json": {"code": 200, "data": {"id": 2, "deviceId": 999999, "title": "x"}}}
        rust = {"status": 200, "json": {"code": 200, "data": {"id": 1, "deviceId": 999999, "title": "x"}}}
        self.assertIsNone(sweep.comparison_entry(job, java, rust, registry))
        rust["json"]["data"]["title"] = "changed"
        self.assertEqual("OPEN", sweep.comparison_entry(job, java, rust, registry)["disposition"])

    def test_in_region_value_type_and_presence_mutations_are_rejected(self):
        rule = {"allowed_paths": ["$.data.score"], "path_predicates": {"$.data.score": {"java": {"value": 0}, "rust": {"value": 2}}}}
        for value in [999, "2", True, {"nested": 2}]:
            self.assertFalse(sweep.allowed_data_paths({"data_diffs": sweep.data_diffs({"score": 0}, {"score": value}, "$.data")}, rule))
        rule = {"allowed_paths": ["$.data.content"], "path_predicates": {"$.data.content": {"java": {"value": None}, "rust": {"present": False}}}}
        self.assertTrue(sweep.allowed_data_paths({"data_diffs": sweep.data_diffs({"content": None}, {}, "$.data")}, rule))
        for java, rust in [({}, {"content": None}), ({"content": None}, {"content": "<absent>"}), ({"content": "changed"}, {})]:
            self.assertFalse(sweep.allowed_data_paths({"data_diffs": sweep.data_diffs(java, rust, "$.data")}, rule))

    def test_array_extra_rows_are_exhaustively_constrained(self):
        rule = {"allowed_paths": ["$.data[len]"], "path_predicates": {"$.data[len]": {
            "java": {"value": 0}, "rust": {"value": 1}, "java_extra": [],
            "rust_extra": [{"type": "object", "fields": {"id": {"value": "<uuid>"}, "name": {"value": None}}}]}}}
        self.assertTrue(sweep.allowed_data_paths({"data_diffs": sweep.data_diffs([], [{"id": "<uuid>", "name": None}], "$.data")}, rule))
        for row in [{"id": "<uuid>", "name": "exposed"}, {"id": "<uuid>", "name": None, "secret": 42}, "wrong type"]:
            self.assertFalse(sweep.allowed_data_paths({"data_diffs": sweep.data_diffs([], [row], "$.data")}, rule))
        self.assertFalse(sweep.allowed_data_paths({"data_diffs": [["$.data[len]", 0, 1]]}, rule))

    def test_every_constrained_rule_has_predicates_and_rejects_in_region_mutations(self):
        registry = json.loads(Path(__file__).with_name("dual-stack-sweep-registry.json").read_text())
        for rule in registry["overrides"]:
            if "allowed_paths" not in rule:
                continue
            self.assertEqual(set(rule["allowed_paths"]), set(rule["path_predicates"]))
            for path, predicate in rule["path_predicates"].items():
                actual_path = path.replace("[*]", "[0]")
                for java, rust in [("arbitrary", "contents"), (None, {"unexpected": "object"})]:
                    self.assertFalse(sweep.allowed_data_paths({"data_diffs": [[actual_path, java, rust]]}, rule), (rule["endpoint"], path))
                if path.endswith("[len]"):
                    evidence = {f"{side}_extra": [spec.get("value") for spec in predicate[f"{side}_extra"]] for side in ("java", "rust")}
                    good = [actual_path, predicate["java"]["value"], predicate["rust"]["value"], evidence]
                    self.assertTrue(sweep.allowed_data_paths({"data_diffs": [good]}, rule))
                    for side in ("java", "rust"):
                        if evidence[f"{side}_extra"]:
                            bad = copy.deepcopy(good)
                            bad[3][f"{side}_extra"][0] = {"arbitrary": "unmatched row"}
                            self.assertFalse(sweep.allowed_data_paths({"data_diffs": [bad]}, rule))


if __name__ == "__main__":
    unittest.main()
