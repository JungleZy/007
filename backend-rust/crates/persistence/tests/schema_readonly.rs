use rust_decimal::Decimal;
use sea_orm::{ConnectionTrait, Database, DbBackend, DbErr, Statement};

#[tokio::test]
#[ignore = "requires an isolated Phase 2 MySQL database populated from project006.sql"]
async fn read_only_fixture_preserves_pagination_null_and_decimal_shapes() -> Result<(), DbErr> {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL")
        .expect("RUST_PHASE2_DATABASE_URL must target an isolated rehearsal database");
    let database = Database::connect(url).await?;

    let rules = database
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT CAST(id AS CHAR) AS id, score, content FROM t_grading_rule ORDER BY id LIMIT 10 OFFSET 0",
        ))
        .await?;
    assert!(rules.len() <= 10);
    for row in rules {
        let _: String = row.try_get("", "id")?;
        let _: Option<i32> = row.try_get("", "score")?;
        let _: Option<String> = row.try_get("", "content")?;
    }

    let training_rows = database
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, score, accuracy FROM general_ticker_pat_train_user ORDER BY id LIMIT 10 OFFSET 0",
        ))
        .await?;
    assert!(training_rows.len() <= 10);
    for row in training_rows {
        let _: i64 = row.try_get("", "id")?;
        let _: Option<Decimal> = row.try_get("", "score")?;
        let _: Option<String> = row.try_get("", "accuracy")?;
    }
    Ok(())
}
