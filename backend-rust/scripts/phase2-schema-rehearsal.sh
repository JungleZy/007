#!/usr/bin/env bash
set -euo pipefail

: "${RUST_BACKEND_DATABASE_URL:?RUST_BACKEND_DATABASE_URL must point to an isolated rehearsal database}"

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
ENTITY_DIR=${RUST_BACKEND_ENTITY_DIR:-"$ROOT_DIR/crates/persistence/src/entity"}
mkdir -p "$ENTITY_DIR"

# The three tables below have no primary key in both Phase 0 snapshots. SeaORM
# requires a primary key for an Entity, so they remain explicit raw-SQL-only
# tables; this script must not invent a synthetic key.
sea generate entity \
  --database-url "$RUST_BACKEND_DATABASE_URL" \
  --output-dir "$ENTITY_DIR" \
  --with-prelude all \
  --impl-active-model-behavior=true \
  --ignore-tables 'seaql_migrations,seq,t_post_ticker_tape_train_setting,t_ticker_tape_train_setting'

cargo check --workspace --locked
printf '%s\n' "Phase 2 schema rehearsal OK: generated SeaORM entities without DDL"
