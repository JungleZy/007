use crate::domain::tools::tools::twelve_month_data;

#[test]
fn twelve_month_data_matches_java_descending_year_month_order() {
    let data = twelve_month_data(2026, 1);
    assert_eq!(data.first().expect("first month")["year"], 2026);
    assert_eq!(data.first().expect("first month")["month"], 1);
    assert_eq!(data.last().expect("last month")["year"], 2025);
    assert_eq!(data.last().expect("last month")["month"], 2);
    assert_eq!(data.len(), 12);
}
