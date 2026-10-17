use std::time::{SystemTime, UNIX_EPOCH};

pub(crate) fn now_time_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock must be after Unix epoch")
        .as_millis() as u64
}
