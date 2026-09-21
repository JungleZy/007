#!/usr/bin/env python3
"""Build the Phase 0 schema inventory from a completed dual-snapshot rehearsal.

This is deliberately read-only with respect to database snapshots and the
rehearsal output. It refuses missing or untracked inputs instead of producing a
partial inventory, and writes only schema metadata (never table rows).
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shlex
import subprocess
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


HEADER = [
    "state",
    "record_type",
    "table_name",
    "object_name",
    "ordinal_position",
    "column_name",
    "data_type",
    "column_type",
    "is_nullable",
    "column_default",
    "extra",
    "collation_name",
    "engine",
    "index_non_unique",
    "index_type",
    "referenced_table",
    "referenced_column",
    "source_snapshot",
    "source_snapshot_sha256",
]
STATES = ("pre-current", "pre-base", "post-current", "post-base")


def fail(message: str) -> "NoReturn":
    print(f"schema inventory: {message}", file=sys.stderr)
    raise SystemExit(2)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as exc:
        fail(f"cannot read {path}: {exc}")
    return digest.hexdigest()


def repo_path(repo: Path, path: Path) -> str:
    try:
        return path.resolve().relative_to(repo.resolve()).as_posix()
    except ValueError:
        return str(path.resolve())


def read_provenance(path: Path) -> tuple[dict[str, str], list[dict[str, str]]]:
    if not path.is_file():
        fail(f"missing collector provenance: {path}")
    with path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    required = {"kind", "name", "value", "path", "sha256"}
    if not rows or set(rows[0]) != required:
        fail(f"invalid collector provenance header: {path}")
    run = {row["name"]: row["value"] for row in rows if row["kind"] == "run"}
    migrations = [row for row in rows if row["kind"] == "migration"]
    for key in ("commit", "command", "utc"):
        if not run.get(key):
            fail(f"collector provenance lacks run {key}")
    if not migrations:
        fail("collector provenance contains no migrations")
    return run, migrations


def read_records(path: Path, expected_state: str) -> list[dict[str, str]]:
    if not path.is_file():
        fail(f"missing rehearsal metadata: {path}")
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        fields = reader.fieldnames or []
        expected = HEADER[:17]
        if fields != expected:
            fail(f"invalid metadata header in {path}; expected {expected}")
        records = list(reader)
    if not records:
        fail(f"metadata is empty: {path}")
    for record in records:
        if record["state"] != expected_state:
            fail(f"metadata state mismatch in {path}: {record['state']!r}")
        if any(key not in record for key in expected):
            fail(f"metadata row missing fields in {path}")
    return records


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rehearsal-dir", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--snapshot-current", required=True, type=Path)
    parser.add_argument("--snapshot-base", required=True, type=Path)
    parser.add_argument("--migrations-dir", required=True, type=Path)
    args = parser.parse_args()

    rehearsal = args.rehearsal_dir.resolve()
    out_dir = args.out_dir.resolve()
    repo = Path(__file__).resolve().parents[2]
    snapshots = {
        "current": args.snapshot_current.resolve(),
        "base": args.snapshot_base.resolve(),
    }
    for path in snapshots.values():
        if not path.is_file():
            fail(f"missing snapshot input: {path}")
    migrations_dir = args.migrations_dir.resolve()
    if not migrations_dir.is_dir():
        fail(f"missing migrations directory: {migrations_dir}")

    collector_run, provenance_migrations = read_provenance(rehearsal / "rehearsal-provenance.tsv")
    discovered = sorted(migrations_dir.glob("*.sql"), key=lambda path: path.name)
    if not discovered:
        fail(f"no migration SQL files found in {migrations_dir}")
    declared_names = [row["value"] for row in provenance_migrations]
    discovered_names = [path.name for path in discovered]
    if declared_names != discovered_names:
        fail("collector migration order/coverage differs from migrations directory: "
             f"declared={declared_names}, discovered={discovered_names}")
    for row, path in zip(provenance_migrations, discovered):
        actual = sha256(path)
        if row["value"] != path.name or row["sha256"] != actual:
            fail(f"migration checksum mismatch: {path}")

    snapshot_hashes = {label: sha256(path) for label, path in snapshots.items()}
    provenance_snapshots = {
        row["name"]: row for row in csv.DictReader(
            (rehearsal / "rehearsal-provenance.tsv").open(newline="", encoding="utf-8"),
            delimiter="\t",
        ) if row["kind"] == "snapshot"
    }
    for label, digest in snapshot_hashes.items():
        row = provenance_snapshots.get(label)
        if not row or row["sha256"] != digest:
            fail(f"snapshot checksum mismatch or missing provenance: {label}")

    state_files = {
        "pre-current": rehearsal / "current-pre-metadata.tsv",
        "pre-base": rehearsal / "base-pre-metadata.tsv",
        "post-current": rehearsal / "current-post-metadata.tsv",
        "post-base": rehearsal / "base-post-metadata.tsv",
    }
    migration_checksums = []
    for row, path in zip(provenance_migrations, discovered):
        actual = sha256(path)
        if row["value"] != path.name or row["sha256"] != actual:
            fail(f"migration checksum mismatch: {path}")
        migration_checksums.append(actual)

    records: list[dict[str, str]] = []
    snapshot_for_state = {
        "pre-current": "current", "post-current": "current",
        "pre-base": "base", "post-base": "base",
    }
    table_counts: dict[str, int] = {}
    counts: dict[str, int] = {state: 0 for state in STATES}
    for state in STATES:
        state_records = read_records(state_files[state], state)
        source = snapshot_for_state[state]
        for record in state_records:
            row = {key: record.get(key, "") for key in HEADER[:17]}
            row["source_snapshot"] = source
            row["source_snapshot_sha256"] = snapshot_hashes[source]
            records.append(row)
            counts[state] += 1
        table_counts[state] = sum(1 for record in state_records if record["record_type"] == "TABLE")

    record_order = {"TABLE": 0, "PRIMARY_KEY": 1, "COLUMN": 2, "INDEX": 3, "FOREIGN_KEY": 4}
    records.sort(key=lambda row: (
        STATES.index(row["state"]),
        row["table_name"],
        record_order.get(row["record_type"], 99),
        row["object_name"],
        int(row["ordinal_position"] or 0),
        row["column_name"],
    ))

    out_dir.mkdir(parents=True, exist_ok=True)
    inventory_path = out_dir / "schema-inventory.tsv"
    with inventory_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=HEADER, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(records)

    try:
        commit = subprocess.check_output(
            ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        fail("cannot determine generator commit")
    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    metadata = {
        "schema_inventory_version": 1,
        "generated_at_utc": generated_at,
        "generator": {
            "script": repo_path(repo, Path(__file__).resolve()),
            "sha256": sha256(Path(__file__).resolve()),
            "command": " ".join(shlex.quote(arg) for arg in sys.argv),
            "commit": commit,
        },
        "collector": {
            "provenance": repo_path(repo, rehearsal / "rehearsal-provenance.tsv"),
            "command": collector_run["command"],
            "utc": collector_run["utc"],
            "commit": collector_run["commit"],
        },
        "snapshots": [
            {
                "label": label,
                "path": repo_path(repo, snapshots[label]),
                "sha256": snapshot_hashes[label],
                "states": [f"pre-{label}", f"post-{label}"],
            }
            for label in ("current", "base")
        ],
        "migrations": [
            {
                "order": index,
                "name": path.name,
                "path": repo_path(repo, path),
                "sha256": sha256(path),
            }
            for index, path in enumerate(discovered, start=1)
        ],
        "states": [
            {
                "name": state,
                "source_snapshot": snapshot_for_state[state],
                "is_pre_snapshot": state.startswith("pre-"),
                "is_post_migration": state.startswith("post-"),
                "record_count": counts[state],
                "table_count": table_counts[state],
            }
            for state in STATES
        ],
        "inventory": {
            "path": repo_path(repo, inventory_path),
            "record_count": len(records),
            "contains_table_rows": False,
            "post_states_are_rehearsal_results_not_snapshots": True,
        },
    }
    metadata_path = out_dir / "schema-inventory-metadata.json"
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {inventory_path}")
    print(f"Wrote {metadata_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
