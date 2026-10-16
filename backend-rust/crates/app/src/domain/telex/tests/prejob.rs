use crate::domain::test_support::isolation;

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn telex_base_writers_use_primary_keys() {
    // telex.rs:103/221 (training), :154/229 (statistics).
    for op in ["telex-save", "telex-clear"] {
        for table in ["t_telex_pat", "t_telex_pat_train_statistical"] {
            isolation(op, table, false).await;
        }
    }
}
