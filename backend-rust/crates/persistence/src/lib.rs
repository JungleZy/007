pub mod entity;
pub mod pagination;
use std::future::Future;

use sea_orm::{DatabaseConnection, DatabaseTransaction, DbErr, TransactionTrait};

/// Shared persistence boundary for future business domains.
///
/// This crate never performs schema synchronization. Callers own the query
/// and transaction semantics, while this wrapper makes commit/rollback
/// behavior explicit and prevents a domain service from silently dropping a
/// transaction error.
#[derive(Clone)]
pub struct RepositoryContext {
    database: DatabaseConnection,
}
/// Minimal repository contract shared by domain-specific repositories.
pub trait Repository {
    fn database(&self) -> &DatabaseConnection;
}

impl Repository for RepositoryContext {
    fn database(&self) -> &DatabaseConnection {
        self.database()
    }
}

impl RepositoryContext {
    pub fn new(database: DatabaseConnection) -> Self {
        Self { database }
    }

    pub fn database(&self) -> &DatabaseConnection {
        &self.database
    }

    pub async fn begin(&self) -> Result<DatabaseTransaction, DbErr> {
        self.database.begin().await
    }

    pub async fn transaction<T, F, Fut>(&self, operation: F) -> Result<T, DbErr>
    where
        F: FnOnce(&DatabaseTransaction) -> Fut,
        Fut: Future<Output = Result<T, DbErr>>,
    {
        let transaction = self.begin().await?;
        match operation(&transaction).await {
            Ok(value) => {
                transaction.commit().await?;
                Ok(value)
            }
            Err(error) => {
                transaction.rollback().await?;
                Err(error)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repository_context_type_is_cloneable() {
        fn assert_clone<T: Clone>() {}
        assert_clone::<RepositoryContext>();
    }
}
