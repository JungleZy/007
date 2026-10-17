use rust_decimal::{Decimal, RoundingStrategy};

pub(crate) fn rate_per_minute(count: i64, total_time_millis: i64) -> Decimal {
    if count <= 0 || total_time_millis <= 0 {
        return Decimal::ZERO;
    }
    (Decimal::from(count) * Decimal::from(60_000) / Decimal::from(total_time_millis))
        .round_dp_with_strategy(0, RoundingStrategy::MidpointAwayFromZero)
}
pub(crate) fn rate_per_minute_precise(count: i64, total_time_millis: i64) -> Decimal {
    if count <= 0 || total_time_millis <= 0 {
        return Decimal::ZERO;
    }
    (Decimal::from(count) * Decimal::from(60_000) / Decimal::from(total_time_millis))
        .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
}

pub(crate) fn accuracy_percent(correct: i64, total: i64) -> Decimal {
    if total <= 0 {
        return Decimal::ZERO;
    }
    let correct = correct.max(0);
    (Decimal::from(correct) / Decimal::from(total))
        .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
        * Decimal::from(100)
}
