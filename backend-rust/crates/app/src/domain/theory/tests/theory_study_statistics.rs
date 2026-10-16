use crate::domain::theory::theory::study_credit_text;

#[test]
fn display_credit_keeps_java_double_spelling() {
    for (value, expected) in [
        (0.0, "0.0"),
        (-0.0, "-0.0"),
        (2.0, "2.0"),
        (2.5, "2.5"),
        (0.0001, "1.0E-4"),
        (10_000_000.0, "1.0E7"),
    ] {
        assert_eq!(study_credit_text(value), expected);
    }
}
