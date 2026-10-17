use crate::domain::telex::telex::*;
use serde_json::json;
use serde_json::Value;

fn rows(items: &[(&str, &str)]) -> String {
    Value::Array(
        items
            .iter()
            .map(|(text, value)| json!({"value": value, "text": text, "type": true, "isFocus": false}))
            .collect(),
    )
    .to_string()
}

fn request(body: &str) -> TrainRequest {
    serde_json::from_str(body).expect("request body")
}

#[test]
fn missing_status_defaults_to_not_started_but_explicit_null_does_not() {
    // The creation form omits `status`; Jackson keeps the DTO default of 0.
    let created = request(r#"{"type":2,"content":"[]"}"#);
    assert_eq!(created.status.int_or(0), Some(0));
    assert_eq!(created.total_number.int32_or(0), Some(0));
    // An explicit null overwrites the default, which Java rejects as a missing status.
    let nulled = request(r#"{"type":2,"status":null,"totalNumber":null}"#);
    assert_eq!(nulled.status.int_or(0), None);
    assert_eq!(nulled.total_number.int32_or(0), None);
}

#[test]
fn scalars_are_coerced_like_jackson() {
    let body = request(
        r#"{"type":"1","status":" 2 ","numbs":7.9,"duration":30,"speed":12.5,"accuracy":"abc","totalNumber":3000000000}"#,
    );
    assert_eq!(body.train_type.int(), Some(1));
    assert_eq!(body.status.int(), Some(2));
    assert_eq!(body.numbs.int32(), Some(7));
    assert_eq!(body.duration.text().as_deref(), Some("30"));
    assert_eq!(body.speed.text().as_deref(), Some("12.5"));
    assert_eq!(body.accuracy.int(), None);
    // Does not fit an Integer column: never wraps into a different number.
    assert_eq!(body.total_number.int32(), None);
}

#[test]
fn content_must_keep_the_issued_text_and_row_count() {
    let stored = rows(&[("ab12", ""), ("cd34", "")]);
    let typed = rows(&[("ab12", "ab12"), ("cd34", "cx")]);
    // Typing into the rows is the whole point and is stored as submitted.
    assert_eq!(
        checked_content(&typed, Some(&stored), None, 1),
        Ok(typed.clone())
    );
    assert_eq!(
        checked_content(&rows(&[("ab13", ""), ("cd34", "")]), Some(&stored), None, 1),
        Err("训练报文源内容不可修改")
    );
    let short = rows(&[("ab12", "")]);
    assert_eq!(
        checked_content(&short, Some(&stored), None, 1),
        Err("训练报文结构不一致")
    );
    assert_eq!(
        checked_content(&short, Some(&stored), Some(2), 1),
        Err("扩展请求必须基于完整原始报文")
    );
    // A training issued without content only accepts an empty list.
    assert_eq!(checked_content("[]", None, None, 1), Ok("[]".to_owned()));
    assert_eq!(
        checked_content(&typed, None, None, 1),
        Err("训练报文结构不一致")
    );
}

#[test]
fn unreadable_content_and_out_of_range_extension_are_rejected() {
    let stored = rows(&[("ab12", "")]);
    for bad in ["", "null", "{}", "[", "not json"] {
        assert_eq!(
            checked_content(bad, Some(&stored), None, 1),
            Err("训练报文内容无效"),
            "submitted {bad:?}"
        );
    }
    assert_eq!(
        checked_content(&stored, Some("garbage"), None, 1),
        Err("训练报文内容无效")
    );
    // The range is checked before the structure, so an oversize ask on a short list says so.
    for extension in [-1, 101] {
        assert_eq!(
            checked_content(&stored, Some(&stored), Some(extension), 1),
            Err("扩展数量无效")
        );
    }
}

#[test]
fn extension_appends_generated_groups_after_untouched_rows() {
    let stored = rows(&[("ab12", ""), ("cd34", "")]);
    let typed = rows(&[("ab12", "ab12"), ("cd34", "")]);
    for (kind, alphabet) in [
        (0, "0123456789"),
        (1, "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
        (2, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    ] {
        let extended = checked_content(&typed, Some(&stored), Some(100), kind).expect("extended");
        let parsed: Vec<Value> = serde_json::from_str(&extended).expect("valid json");
        assert_eq!(parsed.len(), 102);
        // The rows the user already typed keep exactly the submitted text.
        assert!(extended.starts_with(typed.trim_end_matches(']')));
        for row in &parsed[2..] {
            let keys: Vec<&str> = row
                .as_object()
                .unwrap()
                .keys()
                .map(String::as_str)
                .collect();
            assert_eq!(keys.len(), 2, "kind {kind}: {row}");
            assert_eq!(row["value"], "");
            let text = row["text"].as_str().unwrap();
            assert_eq!(text.chars().count(), 4, "kind {kind}: {row}");
            assert!(
                text.chars().all(|c| alphabet.contains(c)),
                "kind {kind}: {row}"
            );
        }
    }
    // An issued-empty list is extended from nothing.
    let from_empty = checked_content("[]", None, Some(3), 0).expect("extended");
    assert_eq!(
        serde_json::from_str::<Vec<Value>>(&from_empty)
            .unwrap()
            .len(),
        3
    );
}

#[test]
fn scoring_counts_only_typed_rows_with_integer_accuracy() {
    let content = rows(&[
        ("ab12", "ab12"),
        ("cd34", "cx34"),
        ("ef56", ""),
        ("gh78", "   "),
    ]);
    assert_eq!(measure(Some(&content)), Ok((2, 1, 50)));
    // Integer division, not rounding: 2 of 3 right is 66, never 67.
    let content = rows(&[("a", "a"), ("b", "b"), ("c", "x")]);
    assert_eq!(measure(Some(&content)), Ok((3, 1, 66)));
    // Nothing typed (or no content at all) scores zero instead of dividing by zero.
    assert_eq!(measure(Some(&rows(&[("ab12", "")]))), Ok((0, 0, 0)));
    assert_eq!(measure(None), Ok((0, 0, 0)));
    assert_eq!(measure(Some("{")), Err("训练报文内容无效"));
}

#[test]
fn speed_is_rounded_half_up_to_two_decimals() {
    assert_eq!(speed_text(2, 250), "480.00");
    // 60000 / 480000 is exactly 0.125: half-up gives 0.13 where banker's rounding gives 0.12.
    assert_eq!(speed_text(1, 480_000), "0.13");
    assert_eq!(speed_text(0, 5_000), "0.00");
    // No active time at all is the bare "0", not "0.00".
    assert_eq!(speed_text(3, 0), "0");
}

/// Settling one user's paused predecessor (which also updates that user's statistics row)
/// must leave every other user's training and statistics rows writable. Both tables are
/// indexed by primary key only, so a locking read on `create_user_id` / `user_id` would lock
/// every row it scans and stall unrelated users' saves until commit.
///
/// Needs an isolated MySQL database with the project schema:
/// `RUST_PHASE2_DATABASE_URL=mysql://… cargo test -p rust-backend -- --ignored`.
#[tokio::test]
#[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
async fn settling_one_users_predecessor_does_not_lock_other_users_rows() -> Result<(), sqlx::Error>
{
    let url = std::env::var("RUST_PHASE2_DATABASE_URL")
        .expect("RUST_PHASE2_DATABASE_URL must target an isolated rehearsal database");
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(3)
        .connect(&url)
        .await?;
    let tag = uuid::Uuid::new_v4().simple().to_string();
    let owner = format!("lock-owner-{tag}");
    let other = format!("lock-other-{tag}");
    for user in [&owner, &other] {
        // A paused training of type 1 (statistics type 2) with a statistics row each.
        sqlx::query("INSERT INTO t_telex_pat_train (id,type,status,duration,speed,create_user_id,create_time) VALUES (?,1,2,'20','10.50',?,'1')")
            .bind(format!("train-{user}"))
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("INSERT INTO t_telex_pat_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,0,'0',0,?)")
            .bind(format!("stat-{user}"))
            .bind(user)
            .execute(&pool)
            .await?;
    }

    // Act: settle the owner's predecessor and keep the transaction open.
    let mut tx = pool.begin().await?;
    settle_previous(&mut tx, &owner, 1).await?;
    let mut bystander = pool.acquire().await?;
    sqlx::query("SET SESSION innodb_lock_wait_timeout=2")
        .execute(&mut *bystander)
        .await?;
    let training = sqlx::query("UPDATE t_telex_pat_train SET title='x' WHERE id=?")
        .bind(format!("train-{other}"))
        .execute(&mut *bystander)
        .await;
    let statistics =
        sqlx::query("UPDATE t_telex_pat_train_statistical SET total_count=7 WHERE id=?")
            .bind(format!("stat-{other}"))
            .execute(&mut *bystander)
            .await;
    // The owner's own rows were really settled (finished + counted) inside the transaction.
    let settled: (Option<i32>, Option<i32>) = (
        sqlx::query_scalar("SELECT status FROM t_telex_pat_train WHERE id=?")
            .bind(format!("train-{owner}"))
            .fetch_one(&mut *tx)
            .await?,
        sqlx::query_scalar("SELECT total_count FROM t_telex_pat_train_statistical WHERE id=?")
            .bind(format!("stat-{owner}"))
            .fetch_one(&mut *tx)
            .await?,
    );
    tx.rollback().await?;
    drop(bystander);
    for user in [&owner, &other] {
        sqlx::query("DELETE FROM t_telex_pat_train WHERE create_user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("DELETE FROM t_telex_pat_train_statistical WHERE user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
    }

    assert_eq!(settled, (Some(3), Some(1)));
    assert!(
        training.is_ok(),
        "another user's training row is locked: {training:?}"
    );
    assert!(
        statistics.is_ok(),
        "another user's statistics row is locked: {statistics:?}"
    );
    Ok(())
}
