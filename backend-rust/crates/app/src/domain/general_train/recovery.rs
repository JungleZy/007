use super::common::*;
use super::generate::frozen_rule;
use super::key::settle_key;
use super::lifecycle::closing_status;
use super::telex::settle_telex;
use super::ticker::settle_ticker;
use crate::platform::state::AppState;
use sqlx::MySqlPool;

async fn recovery_train_ids(pool: &MySqlPool, domain: Domain) -> Result<Vec<String>, sqlx::Error> {
    let sql = format!(
        "SELECT CAST(t.id AS CHAR) FROM {} t WHERE t.protocol_version=1 AND t.status=? AND t.end_time IS NOT NULL AND t.end_time <= DATE_SUB(NOW(6), INTERVAL 60 SECOND) AND EXISTS (SELECT 1 FROM {} u WHERE u.train_id=t.id AND u.role=0 AND COALESCE(u.is_finish,0)<>1)",
        table(domain),
        user_table(domain),
    );
    sqlx::query_scalar(&sql)
        .bind(closing_status(domain))
        .fetch_all(pool)
        .await
}

pub(super) async fn recover_one(pool: &MySqlPool, domain: Domain, id: &str) -> Outcome<()> {
    let mut transaction = pool.begin().await?;
    let train = load_train(&mut transaction, id, domain, true).await?;
    let eligible: i64 = sqlx::query_scalar(&format!(
        "SELECT EXISTS(SELECT 1 FROM {} WHERE id=? AND protocol_version=1 AND status=? AND end_time IS NOT NULL AND end_time <= DATE_SUB(NOW(6), INTERVAL 60 SECOND))",
        table(domain),
    ))
    .bind(id)
    .bind(closing_status(domain))
    .fetch_one(&mut *transaction)
    .await?;
    if eligible == 0 {
        transaction.commit().await?;
        return Ok(());
    }
    frozen_rule(&train, domain)?;
    // Plain read: each member is then locked by primary key (locked_member), in id order.
    let member_ids: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT user_id FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1 ORDER BY id",
        user_table(domain),
    ))
    .bind(id)
    .fetch_all(&mut *transaction)
    .await?;
    let recipients: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT user_id FROM {} WHERE train_id=? AND role=1",
        user_table(domain),
    ))
    .bind(id)
    .fetch_all(&mut *transaction)
    .await?;
    let mut settled = Vec::new();
    for user_id in member_ids {
        let member = locked_member(&mut transaction, id, &user_id, domain).await?;
        if member["role"] != 0 || member["isFinish"] == 1 {
            continue;
        }
        match domain {
            Domain::Key => settle_key(&mut transaction, &train, &member, &user_id).await?,
            Domain::Telex => {
                settle_telex(&mut transaction, &train, &member, &user_id).await?;
            }
            Domain::Ticker => settle_ticker(&mut transaction, &train, &member, &user_id).await?,
        }
        settled.push(user_id);
    }
    if matches!(domain, Domain::Key | Domain::Ticker) {
        // A NOT EXISTS subquery inside the UPDATE would S-lock every member row it scans.
        let unfinished: i64 = sqlx::query_scalar(&format!(
            "SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1",
            user_table(domain),
        ))
        .bind(id)
        .fetch_one(&mut *transaction)
        .await?;
        if unfinished == 0 {
            sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain)))
                .bind(id)
                .execute(&mut *transaction)
                .await?;
        }
    }
    transaction.commit().await?;
    for user_id in &settled {
        crate::ws::notify::notify_general_result(ws_kind(domain), id, user_id, &recipients).await;
    }
    Ok(())
}

/// Sweep completed rooms whose 60-second submission grace period has elapsed.
/// Every candidate is rechecked under a transaction lock before frozen-rule
/// settlement, and failures remain retryable.
pub(crate) async fn recover_general_trainings(state: &AppState) {
    let Some(pool) = state.pool.as_ref() else {
        tracing::warn!("general training recovery skipped: database unavailable");
        return;
    };
    for domain in [Domain::Key, Domain::Ticker, Domain::Telex] {
        let ids = match recovery_train_ids(pool, domain).await {
            Ok(ids) => ids,
            Err(error) => {
                tracing::error!(domain = ws_kind(domain), error = %error, "general training recovery candidate query failed");
                continue;
            }
        };
        for id in ids {
            if let Err(error) = recover_one(pool, domain, &id).await {
                tracing::error!(domain = ws_kind(domain), train_id = %id, error = ?error, "general training recovery failed; candidate remains retryable");
            }
        }
    }
}
