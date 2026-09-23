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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rate_matches_java_score_math_boundaries() {
        assert_eq!(rate_per_minute(0, 1000), Decimal::ZERO);
        assert_eq!(rate_per_minute(10, 0), Decimal::ZERO);
        assert_eq!(rate_per_minute(120, 60_000), Decimal::from(120));
        assert_eq!(rate_per_minute(1, 40_000), Decimal::from(2));
        assert_eq!(rate_per_minute_precise(1, 40_000), Decimal::new(150, 2));
    }

    #[test]
    fn accuracy_matches_java_two_decimal_intermediate_rounding() {
        assert_eq!(accuracy_percent(0, 0), Decimal::ZERO);
        assert_eq!(accuracy_percent(-1, 10), Decimal::ZERO);
        assert_eq!(accuracy_percent(1, 3), Decimal::from(33));
        assert_eq!(accuracy_percent(2, 3), Decimal::from(67));
    }
}
