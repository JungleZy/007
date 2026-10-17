use crate::shared::scoring::*;
use rust_decimal::Decimal;

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
