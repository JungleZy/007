use rust_persistence::RepositoryContext;
use sea_orm::{ConnectionTrait, Database, DbBackend, DbErr, Statement};
use uuid::Uuid;

#[tokio::test]
#[ignore = "requires an isolated Phase 2 MySQL database"]
async fn transaction_rolls_back_without_leaving_fixture_rows() -> Result<(), DbErr> {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL")
        .expect("RUST_PHASE2_DATABASE_URL must target an isolated rehearsal database");
    let database = Database::connect(url).await?;
    let table = format!("phase2_txn_probe_{}", Uuid::new_v4().simple());
    let create = format!("CREATE TABLE `{table}` (id INT PRIMARY KEY, value VARCHAR(32) NOT NULL)");
    database
        .execute(Statement::from_string(DbBackend::MySql, create))
        .await?;

    let repository = RepositoryContext::new(database.clone());
    let insert = format!("INSERT INTO `{table}` (id, value) VALUES (1, 'rollback-me')");
    let result: Result<(), DbErr> = repository
        .transaction(|transaction| {
            Box::pin(async move {
                transaction
                    .execute(Statement::from_string(DbBackend::MySql, insert))
                    .await?;
                Err(DbErr::Custom("intentional rollback".into()))
            })
        })
        .await;
    assert!(result.is_err());

    let count_sql = format!("SELECT COUNT(*) AS count FROM `{table}`");
    let row = database
        .query_one(Statement::from_string(DbBackend::MySql, count_sql))
        .await?
        .expect("count row");
    assert_eq!(row.try_get::<i64>("", "count")?, 0);

    let drop_sql = format!("DROP TABLE `{table}`");
    database
        .execute(Statement::from_string(DbBackend::MySql, drop_sql))
        .await?;
    Ok(())
}
