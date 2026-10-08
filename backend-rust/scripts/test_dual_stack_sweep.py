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
        return sweep.comparable({"status": 200, "json": left}) == sweep.comparable({"status": 200, "json": right})

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
        self.assertEqual(entries[0], entries[1])
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
        self.assertEqual(plain, sweep.normalized_entity_keys({plain: 1})["<normalized-entity-key-entries>"][0][0])

    def test_scheduled_candidate_journey_preserves_named_query_order(self):
        spec = json.loads(Path(__file__).with_name("dual-stack-journeys.json").read_text())
        steps = next(journey["steps"] for journey in spec["journeys"] if journey["name"] == "theory-scheduled-exam")
        listing = next(step for step in steps if step["name"] == "list-scheduled-candidate")
        self.assertNotIn("$.data.exam", listing.get("unordered", []))
        starts = {step["body"]["startTime"] for step in steps
                  if step["path"].endswith("/savetheoryKnowledgeExam") and "sweep-student" in step["body"]["stuId"]}
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


if __name__ == "__main__":
    unittest.main()
