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
            results = sweep.run_journeys(journey, args, {"admin": {}})
        self.assertEqual([{"content": original}, {"content": original}], calls[2:])
        self.assertEqual({"<parsed-json-string>": {"value": 1}}, results[0][1]["json"]["data"]["content"])
        self.assertEqual(original, results[1][1]["json"]["data"]["content"])


if __name__ == "__main__":
    unittest.main()
