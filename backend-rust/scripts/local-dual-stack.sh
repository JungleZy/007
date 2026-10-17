#!/usr/bin/env bash
# Local Java/Rust dual-stack harness for the migration batches (synthetic data only).
#
#   local-dual-stack.sh setup     # docker mysql:8.0 "mysql-project006" + dev snapshot + every migration
#   local-dual-stack.sh schema    # recreate the two isolated parity schemas (no data)
#   local-dual-stack.sh build     # Java fast-jar (skip tests) + Rust debug binary
#   local-dual-stack.sh up|down   # start/stop Java :18121 and Rust :18122 (all RUST_*_ENABLED flags on)
#   local-dual-stack.sh journeys [file]   # run journeys (default: all) -> $WORK/journeys.json + summary
#   local-dual-stack.sh full              # full authenticated sweep  -> $WORK/full.json + summary
#
# Needs: docker, $HOME/.local/opt/jdk21, cargo. Credentials are the throwaway local root/root
# of the container; never point this at a shared database.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$HOME/.cache/rust-dual-stack}"
JDB=rust_parity_java_local
RDB=rust_parity_rust_local
mkdir -p "$WORK"
export MYSQL_PWD=root
my() { docker exec -i -e MYSQL_PWD mysql-project006 mysql -uroot --default-character-set=utf8mb4 "$@"; }
wait_http() {
  for _ in $(seq 1 60); do
    curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$1/api/user/getUsersByToken" | grep -q 200 && return 0
    sleep 2
  done
  echo "service on :$1 did not come up; see $WORK/*.log" >&2; return 1
}
summary() { python3 "$ROOT/backend-rust/scripts/sweep-summary.py" "$1"; }

case "${1:-}" in
setup)
  docker ps -a --format '{{.Names}}' | grep -qx mysql-project006 || docker run -d --name mysql-project006 \
    -e MYSQL_ROOT_PASSWORD=root -p 127.0.0.1:3306:3306 mysql:8.0 \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
  docker start mysql-project006 >/dev/null
  until docker exec mysql-project006 mysqladmin -uroot -proot ping 2>/dev/null | grep -q alive; do sleep 2; done
  my -e "CREATE DATABASE IF NOT EXISTS project006 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  my project006 < "$ROOT/backend/database/project006.sql"
  for f in $(sed -n '/^MIGRATIONS=(/,/^)/p' "$ROOT/backend/scripts/rehearse-migrations.sh" | grep -oE 'migrations/[^"]+'); do
    my project006 < "$ROOT/backend/database/$f"
  done
  my -e "CREATE DATABASE IF NOT EXISTS rust_scratch_lockdemo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  "$0" schema
  docker exec -e MYSQL_PWD mysql-project006 mysqldump -uroot --no-data --skip-triggers --set-gtid-purged=OFF \
    --no-tablespaces project006 | my rust_scratch_lockdemo ;;
schema)
  docker exec -e MYSQL_PWD mysql-project006 mysqldump -uroot --no-data --skip-triggers --set-gtid-purged=OFF \
    --no-tablespaces project006 > "$WORK/schema.sql"
  for db in $JDB $RDB; do
    my -e "DROP DATABASE IF EXISTS $db; CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    my "$db" < "$WORK/schema.sql"
  done ;;
build)
  (cd "$ROOT/backend" && JAVA_HOME="$HOME/.local/opt/jdk21" ./mvnw -B -q -DskipTests package)
  (cd "$ROOT/backend-rust" && cargo build --locked -p rust-backend) ;;
up)
  "$0" down
  (cd "$WORK" && exec env DB_USER=root DB_PASSWORD=root QUARKUS_HTTP_PORT=18121 QUARKUS_HTTP_HOST=127.0.0.1 \
    QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://127.0.0.1:3306/$JDB?useSSL=false&allowPublicKeyRetrieval=true" \
    nohup "$HOME/.local/opt/jdk21/bin/java" -Duser.timezone=UTC -jar "$ROOT/backend/target/quarkus-app/quarkus-run.jar" \
    > "$WORK/java.log" 2>&1) & echo $! > "$WORK/java.pid"
  env RUST_BACKEND_ADDR=127.0.0.1:18122 RUST_BACKEND_DATABASE_URL="mysql://root:root@127.0.0.1:3306/$RDB" \
    $(grep -oE '"RUST_[A-Z0-9_]+_ENABLED"' "$ROOT/backend-rust/crates/app/src/platform/flags.rs" | sort -u | tr -d '"' | sed 's/$/=true/') \
    nohup "$ROOT/backend-rust/target/debug/rust-backend" > "$WORK/rust.log" 2>&1 & echo $! > "$WORK/rust.pid"
  wait_http 18121 && wait_http 18122 && echo "java :18121 and rust :18122 up" ;;
down)
  for s in java rust; do [ -f "$WORK/$s.pid" ] && kill "$(cat "$WORK/$s.pid")" 2>/dev/null || true; rm -f "$WORK/$s.pid"; done ;;
journeys)
  python3 "$ROOT/backend-rust/scripts/dual-stack-sweep.py" --java-db $JDB --rust-db $RDB --only-journeys \
    ${2:+--journeys "$2"} --out "$WORK/journeys.json" > "$WORK/journeys.log" 2>&1 || true
  summary "$WORK/journeys.json" ;;
full)
  python3 "$ROOT/backend-rust/scripts/dual-stack-sweep.py" --java-db $JDB --rust-db $RDB \
    --out "$WORK/full.json" > "$WORK/full.log" 2>&1 || true
  summary "$WORK/full.json" ;;
*) sed -n '2,12p' "$0"; exit 2 ;;
esac
