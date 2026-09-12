#!/usr/bin/env bash
# ============================================================================
# rehearse-migrations.sh — 双快照迁移演练（一次性 Docker 容器，纯 schema 证据）
#
# 对 database/project006.sql（current）与 project006-base.sql（base）两个快照（路径以 backend/ 为根），
# 各起一个全新、唯一命名的 mysql:8.0 容器+卷，导入快照，顺序执行迁移
# 01（schema-sync）→ 02（engine-innodb）→ 03（unique-lazy-create）→ 04/05（JSON容量），断言最终 schema，并对实体表生成
# 与实体权威 schema（EntitySchemaSnapshotRehearsal 导出的 entity-schema.tsv）
# 的规范化差分。差分必须为空。
#
# 硬安全约束：
#   - 不接受任何位置参数（不接受主机 / JDBC URL）。
#   - 拒绝环境变量 DB_HOST / JDBC_URL / QUARKUS_DATASOURCE_JDBC_URL。
#   - 绝不读取 application.yml 的 datasource 配置。
#   - 每个快照使用全新、唯一命名的容器与卷；仅通过 docker exec -i 导入。
#   - 所有出口路径 trap 清理容器与卷。
#   - 只写 schema 元数据（表/列/引擎），绝不导出业务数据行或凭据。
#
# 可调项（只影响证据落盘位置，不影响数据源）：
#   REHEARSAL_OUT_NAME    证据目录名（默认当天日期），仅允许 [0-9A-Za-z._-]，
#                         防止逃出 database/rehearsal/；重跑不再覆盖历史证据。
#   REHEARSAL_ENTITY_SCHEMA  实体权威 schema TSV 路径；默认先找证据目录，
#                         再回退 target/migration-rehearsal/entity-schema.tsv 并自动复制。
# ============================================================================
set -euo pipefail

# ---- 守卫 1：不接受任何参数 ----
[[ $# -eq 0 ]] || { echo "This script accepts no database target" >&2; exit 2; }

# ---- 守卫 2：拒绝外部数据源环境变量 ----
for banned in DB_HOST JDBC_URL QUARKUS_DATASOURCE_JDBC_URL; do
  if [[ -n "${!banned:-}" ]]; then
    echo "Refusing to run: environment variable '$banned' is set; this rehearsal is container-only" >&2
    exit 2
  fi
done

# ---- 定位工程根（不依赖 CWD，绝不读取 application.yml） ----
# PROJECT_ROOT = backend/：库快照、迁移脚本、演练证据（database/）与构建产物（target/）都在其下
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

IMAGE="mysql:8.0"
ROOT_PASSWORD="rehearsal-$(date +%s)-$$"
OUT_NAME="${REHEARSAL_OUT_NAME:-$(date +%Y-%m-%d)}"
[[ "$OUT_NAME" =~ ^[0-9A-Za-z._-]+$ ]] || {
  echo "Invalid REHEARSAL_OUT_NAME '$OUT_NAME': only [0-9A-Za-z._-] allowed" >&2; exit 2; }
OUTDIR="$PROJECT_ROOT/database/rehearsal/$OUT_NAME"
mkdir -p "$OUTDIR"

# 实体权威 schema：证据目录 → 显式覆盖 → 构建产物（自动复制，免手工搬运）
ENTITY_SCHEMA="${REHEARSAL_ENTITY_SCHEMA:-$OUTDIR/entity-schema.tsv}"
if [[ ! -f "$ENTITY_SCHEMA" ]]; then
  EXPORTED="$PROJECT_ROOT/target/migration-rehearsal/entity-schema.tsv"
  if [[ -f "$EXPORTED" ]]; then
    cp "$EXPORTED" "$OUTDIR/entity-schema.tsv"
    ENTITY_SCHEMA="$OUTDIR/entity-schema.tsv"
    echo "Copied entity schema from $EXPORTED"
  fi
fi
# 执行顺序 = 文件名字典序（T7-4：不改已执行脚本的文件名，只把演练数组排序，
# 便于与运维按文件名记录的"已跑清单"逐行对齐）。
# 已逐对核对：字典序与依赖顺序不冲突——加列脚本 2026-09-11-03-post-telex-capture-clock.sql
# 天然排在建索引脚本 2026-09-12-02-post-telex-due-index.sql 之前；
# 其余脚本作用于互不相交的表族（general_* / personal / simulation / theory），无先后依赖。
# 下方 `index >= 3` 的重复执行断言依赖前三个非幂等脚本仍排在最前，排序后位置未变。
MIGRATIONS=(
  "$PROJECT_ROOT/database/migrations/2026-08-26-01-schema-sync.sql"
  "$PROJECT_ROOT/database/migrations/2026-08-26-02-engine-innodb.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-08-01-unique-lazy-create.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-10-01-theory-json-capacity.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-10-02-scoring-json-capacity.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-01-group-net-scoring.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-01-simulation-page-uniqueness.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-03-personal-electronic-capture.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-03-post-telex-capture-clock.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-04-general-capture-clock.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-11-04-personal-handkey-capture.sql"
  "$PROJECT_ROOT/database/migrations/2026-09-12-02-post-telex-due-index.sql"
)

for f in "$ENTITY_SCHEMA" "${MIGRATIONS[@]}"; do
  [[ -f "$f" ]] || { echo "Missing required file: $f" >&2;
    [[ "$f" == "$ENTITY_SCHEMA" ]] && echo "  Run: ./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test  (产物 target/migration-rehearsal/entity-schema.tsv 会被本脚本自动复制到 $OUTDIR/)" >&2
    exit 3; }
done

# OUTDIR 已在上方创建

# 实体表名集合（差分范围仅限实体表；快照中的非实体表被排除）。
ENTITY_TABLES="$(mktemp)"
tail -n +2 "$ENTITY_SCHEMA" | cut -f1 | sort -u > "$ENTITY_TABLES"

# ---- trap 清理：记录所有已创建的容器与卷，任意出口全部销毁 ----
declare -a CREATED_CONTAINERS=()
declare -a CREATED_VOLUMES=()
cleanup() {
  local rc=$?
  set +e
  for c in "${CREATED_CONTAINERS[@]:-}"; do [[ -n "$c" ]] && docker rm -f "$c" >/dev/null 2>&1; done
  for v in "${CREATED_VOLUMES[@]:-}"; do [[ -n "$v" ]] && docker volume rm "$v" >/dev/null 2>&1; done
  rm -f "$ENTITY_TABLES" 2>/dev/null
  exit "$rc"
}
trap cleanup EXIT INT TERM

mysql_exec() { docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$1" mysql -uroot --default-character-set=utf8mb4 "${@:2}"; }
mysql_scalar() { docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$1" mysql -uroot -N -B project006 -e "$2"; }

# 规范化投影：schema TSV(6 列,带表头) → 排序去重的 (table, lower(column), norm_type)。
# 字符串族(char/varchar/*text/enum/set)归一为 string,以匹配 Hibernate validate 的类型族容忍。
normproj() {
  tail -n +2 "$1" | awk -F'\t' '{
    t=$1; c=tolower($2); dt=$4;
    if (dt=="char"||dt=="varchar"||dt=="tinytext"||dt=="text"||dt=="mediumtext"||dt=="longtext"||dt=="enum"||dt=="set") dt="string";
    print t "\t" c "\t" dt
  }' | sort -u
}

FAILURES=0
pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1" >&2; FAILURES=$((FAILURES+1)); }
assert_eq() { # desc expected actual
  if [[ "$2" == "$3" ]]; then pass "$1 ($3)"; else fail "$1 (expected '$2', got '$3')"; fi
}

# ---- 单快照演练 ----
rehearse() {
  local label="$1" snapshot="$2"
  local suffix; suffix="$(date +%s)-$$-${label}"
  local cname="rehearsal-mysql-${suffix}"
  local vname="rehearsal-vol-${suffix}"

  echo "=================================================================="
  echo "[$label] snapshot: $snapshot"

  CREATED_VOLUMES+=("$vname")
  docker volume create "$vname" >/dev/null
  CREATED_CONTAINERS+=("$cname")
  docker run -d --name "$cname" \
    -v "$vname":/var/lib/mysql \
    -e MYSQL_ROOT_PASSWORD="$ROOT_PASSWORD" \
    -e MYSQL_DATABASE=project006 \
    "$IMAGE" \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null

  # 等待就绪：mysqladmin ping
  local up=0
  for _ in $(seq 1 120); do
    if docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$cname" mysqladmin ping -uroot --silent >/dev/null 2>&1; then
      up=1; break
    fi
    sleep 1
  done
  [[ "$up" -eq 1 ]] || { fail "[$label] container did not become ready"; return; }
  # 再等一次可写连接（ping 就绪与可查询之间可能有窗口）
  for _ in $(seq 1 30); do
    if mysql_scalar "$cname" "select 1" >/dev/null 2>&1; then break; fi
    sleep 1
  done

  # 导入快照（仅 docker exec -i）
  mysql_exec "$cname" project006 < "$snapshot"

  # base 快照：迁移前 id 类型断言（integer）
  if [[ "$label" == "base" ]]; then
    local pre_id; pre_id="$(mysql_scalar "$cname" \
      "select data_type from information_schema.columns where table_schema=database() and table_name='general_key_pat_page' and column_name='id'")"
    assert_eq "[base] pre-migration general_key_pat_page.id data_type=int" "int" "$pre_id"
  fi

  local index migration started elapsed
  local migration_timings=()
  for index in "${!MIGRATIONS[@]}"; do
    migration="${MIGRATIONS[$index]}"
    started=$(date +%s%3N)
    mysql_exec "$cname" project006 < "$migration"
    elapsed=$(( $(date +%s%3N) - started ))
    migration_timings+=("$elapsed")
    echo "  TIMING ${migration##*/}=${elapsed}ms"
    if (( index >= 3 )); then
      mysql_exec "$cname" project006 < "$migration"
      echo "  PASS  [$label] repeat migration ${migration##*/}"
    fi
  done
  { printf '%s' "$label"; printf '\t%s' "${migration_timings[@]}"; printf '\n'; } >> "$OUTDIR/timings.tsv"

  # ---- 断言 ----
  assert_eq "[$label] post-migration table count=106" "106" \
    "$(mysql_scalar "$cname" "select count(*) from information_schema.tables where table_schema=database() and table_type='BASE TABLE'")"
  assert_eq "[$label] MyISAM table count=0" "0" \
    "$(mysql_scalar "$cname" "select count(*) from information_schema.tables where table_schema=database() and engine='MyISAM'")"
  local named="general_telex_pat general_telex_pat_page general_telex_pat_user general_telex_pat_user_value t_masthead"
  for t in $named; do
    assert_eq "[$label] table exists: $t" "1" \
      "$(mysql_scalar "$cname" "select count(*) from information_schema.tables where table_schema=database() and table_name='$t'")"
  done
  assert_eq "[$label] simulation_router_room.is_start_sign default=1" "1" \
    "$(mysql_scalar "$cname" "select column_default from information_schema.columns where table_schema=database() and table_name='simulation_router_room' and column_name='is_start_sign'")"
  assert_eq "[$label] t_post_ticker_tape_train.is_start_sign default=1" "1" \
    "$(mysql_scalar "$cname" "select column_default from information_schema.columns where table_schema=database() and table_name='t_post_ticker_tape_train' and column_name='is_start_sign'")"
  local post_id; post_id="$(mysql_scalar "$cname" \
    "select data_type from information_schema.columns where table_schema=database() and table_name='general_key_pat_page' and column_name='id'")"
  assert_eq "[$label] general_key_pat_page.id data_type=varchar" "varchar" "$post_id"
  for uk in "t_radiotelephone_train:uk_radiotelephone_train_user_type:2" \
            "t_theory_knowledge_test_fallible:uk_theory_test_fallible_user:1"; do
    local uk_table="${uk%%:*}"; local uk_rest="${uk#*:}"
    local uk_name="${uk_rest%%:*}"; local uk_cols="${uk_rest##*:}"
    assert_eq "[$label] unique index exists: $uk_table.$uk_name ($uk_cols cols, non_unique=0)" "$uk_cols" \
      "$(mysql_scalar "$cname" "select count(*) from information_schema.statistics where table_schema=database() and table_name='$uk_table' and index_name='$uk_name' and non_unique=0")"
  done
  assert_eq "[$label] theory exam JSON payload columns are longtext" "6" \
    "$(mysql_scalar "$cname" "select count(*) from information_schema.columns where table_schema=database() and data_type='longtext' and ((table_name='t_theory_knowledge_exam_test_paper' and column_name in ('single_choice_list','multiple_choice_list','judge_list','completion_list','short_answer')) or (table_name='t_theory_knowledge_exam_user' and column_name='content'))")"
  for payload in "t_grading_rule:content:longtext" \
    "general_ticker_pat:rule_content:longtext" \
    "general_ticker_pat_train_user:deduct_info:longtext" \
    "general_ticker_pat_train_user:statistic_info:longtext" \
    "general_ticker_pat_train_user_value:finish_info:longtext" \
    "general_ticker_pat_train_user_value:message_body:longtext" \
    "general_ticker_pat_train_user_value:resolver:longtext" \
    "general_ticker_pat_train_user_value:standard:longtext" \
    "t_post_telex_pat_train:content:text" \
    "t_post_telex_pat_train:rule_content:longtext" \
    "t_post_telex_pat_train:deduct_info:longtext" \
    "t_post_telex_pat_train_page_value:pat_value:longtext"; do
    local payload_table="${payload%%:*}"; local payload_rest="${payload#*:}"
    local payload_column="${payload_rest%%:*}"; local payload_type="${payload_rest##*:}"
    assert_eq "[$label] $payload_table.$payload_column type=$payload_type" "$payload_type" \
      "$(mysql_scalar "$cname" "select data_type from information_schema.columns where table_schema=database() and table_name='$payload_table' and column_name='$payload_column'")"
  done
  assert_eq "[$label] simulation identity keys are NOT NULL" "6" \
    "$(mysql_scalar "$cname" "select count(*) from information_schema.columns where table_schema=database() and is_nullable='NO' and ((table_name='simulation_router_room_page' and column_name in ('room_id','page_number','sort')) or (table_name='simulation_router_room_page_value' and column_name in ('room_id','user_id','page_number')))")"
  for identity in "simulation_router_room_page:uk_simulation_page_room_page_sort:room_id,page_number,sort" \
    "simulation_router_room_page_value:uk_simulation_value_room_user_page:room_id,user_id,page_number" \
    "t_post_telegraph_key_pat_train_raw_page:uk_personal_key_raw_page:train_id,page_number"; do
    local identity_table="${identity%%:*}"; local identity_rest="${identity#*:}"
    local identity_index="${identity_rest%%:*}"; local identity_columns="${identity_rest#*:}"
    assert_eq "[$label] exact unique key $identity_index" "$identity_columns" \
      "$(mysql_scalar "$cname" "select group_concat(column_name order by seq_in_index) from information_schema.statistics where table_schema=database() and table_name='$identity_table' and index_name='$identity_index' and non_unique=0 and sub_part is null")"
  done
  # 到期扫描支撑索引：列序即 findDueIds 的 (等值, 范围)，错序会让 order by 回落 filesort。
  assert_eq "[$label] exact due-scan index idx_post_telex_due" "status,deadline" \
    "$(mysql_scalar "$cname" "select group_concat(column_name order by seq_in_index) from information_schema.statistics where table_schema=database() and table_name='t_post_telex_pat_train' and index_name='idx_post_telex_due'")"
  for legacy_table in general_ticker_pat general_key_pat t_post_telegram_train t_post_telegraph_key_pat_train t_post_telex_pat_train; do
    assert_eq "[$label] historical $legacy_table is not relabeled as new capture" "0" \
      "$(mysql_scalar "$cname" "select count(*) from $legacy_table where protocol_version<>0 or protocol_version is null")"
  done
  assert_eq "[$label] General electronic standard empty values normalized" "0" \
    "$(mysql_scalar "$cname" "select count(*) from general_key_pat_page where value is null")"
  for payload in "simulation_router_room_page_value:value:longtext" \
    "group_net_train:topic:longtext" "group_net_train:answer:longtext" \
    "group_net_train:scoring_rule_content:longtext" "group_net_train:content:longtext" \
    "device_scoring_rule:rule_content:longtext" \
    "general_key_pat_user:capture_pages:longtext" \
    "general_ticker_pat_train_user_value:capture_intervals:longtext" \
    "t_post_telegram_train_floor_content_value:capture_intervals:longtext" \
    "t_post_telex_pat_train_page_value:capture_intervals:longtext" \
    "t_post_telegraph_key_pat_train_raw_page:capture_intervals:longtext"; do
    local payload_table="${payload%%:*}"; local payload_rest="${payload#*:}"
    local payload_column="${payload_rest%%:*}"; local payload_type="${payload_rest##*:}"
    assert_eq "[$label] $payload_table.$payload_column type=$payload_type" "$payload_type" \
      "$(mysql_scalar "$cname" "select data_type from information_schema.columns where table_schema=database() and table_name='$payload_table' and column_name='$payload_column'")"
  done

  # ---- schema 转储（仅实体表，6 列，带表头） ----
  local schema_tsv="$OUTDIR/${label}-schema.tsv"
  {
    printf 'table_name\tcolumn_name\tordinal_position\tdata_type\tis_nullable\tcolumn_default\n'
    mysql_scalar "$cname" \
      "select table_name, column_name, ordinal_position, data_type, is_nullable, coalesce(column_default,'<NULL>') from information_schema.columns where table_schema=database() order by table_name, ordinal_position" \
      | awk -F'\t' 'NR==FNR{keep[$1]=1; next} ($1 in keep)' "$ENTITY_TABLES" - \
      | sort -t $'\t' -k1,1 -k2,2
  } > "$schema_tsv"

  # ---- 引擎证据（独立 TSV：实体 DDL 不编码生产引擎期望） ----
  local engine_tsv="$OUTDIR/${label}-engine.tsv"
  {
    printf 'table_name\tengine\n'
    mysql_scalar "$cname" \
      "select table_name, engine from information_schema.tables where table_schema=database() and table_type='BASE TABLE' order by table_name"
  } > "$engine_tsv"

  # ---- 规范化差分：验证 "实体列 ⊆ 迁移后快照列"，按 Hibernate validate 契约归一 ----
  # validate 只要求：实体所需的每个 (表,列) 在库中存在且类型族兼容；它容忍
  #   - 库中多余列/多余表（故用 comm -23 只取"实体侧未被满足"的行，忽略快照多余列）；
  #   - 字符串族等价（char/varchar/*text/enum/set → 归一为 string；varchar↔longtext 无害）；
  #   - 列名大小写（MySQL 列名大小写不敏感 → 统一小写）。
  # ordinal_position / column_default / is_nullable / 长度 / 字符集 均在 validate 契约之外，
  # 由本脚本的独立断言覆盖，不纳入差分。int/bigint/decimal/double/datetime/bit 等保持原样，
  # 故 base 遗漏的 general_ticker_pat_train_page.id(int vs 实体 varchar) 若未修复仍会现形。
  local diff_file="$OUTDIR/diff-${label}.txt"
  comm -23 <(normproj "$ENTITY_SCHEMA") <(normproj "$schema_tsv") > "$diff_file"
  if [[ -s "$diff_file" ]]; then
    fail "[$label] entity columns unsatisfied by snapshot (see $diff_file)"
  else
    pass "[$label] entity-table schema diff empty (entity columns ⊆ snapshot, validate-equivalent)"
  fi
}

: > "$OUTDIR/timings.tsv"
rehearse current "$PROJECT_ROOT/database/project006.sql"
rehearse base    "$PROJECT_ROOT/database/project006-base.sql"

echo "=================================================================="
if [[ "$FAILURES" -ne 0 ]]; then
  echo "REHEARSAL FAILED: $FAILURES assertion(s) failed" >&2
  exit 1
fi
echo "REHEARSAL PASSED: all assertions green for both snapshots"
