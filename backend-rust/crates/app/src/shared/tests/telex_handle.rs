use crate::shared::telex_handle::*;

fn pages(keys: &[&str]) -> Vec<PageKey> {
    keys.iter()
        .enumerate()
        .map(|(sort, key)| PageKey {
            key: (*key).to_owned(),
            sort: sort as i64,
        })
        .collect()
}

fn run(keys: &[&str], text: &str) -> (Stats, Vec<ValueRow>) {
    let mut ks = Stats::default();
    let mut out = Vec::new();
    handle(1, &pages(keys), text, &mut ks, &mut out).expect("handle");
    (ks, out)
}

const TEN: [&str; 10] = [
    "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009", "1010",
];

#[test]
fn exact_page_has_no_errors() {
    let (ks, out) = run(&TEN, &TEN.join(" "));
    assert_eq!(ks.error_code, 0);
    assert_eq!(ks.pat_group, 10);
    assert_eq!(out.len(), 10);
    assert_eq!(out[1].value, "1002");
}

#[test]
fn wrong_group_counts_one_error_code() {
    let mut groups = TEN.to_vec();
    groups[1] = "9999";
    let (ks, out) = run(&TEN, &groups.join(" "));
    assert_eq!(ks.error_code, 1);
    assert_eq!(out[1].value, "9999");
}

#[test]
fn missing_row_counts_much_less_line_once_per_missing_row() {
    let keys: Vec<String> = (0..20).map(|n| format!("{:04}", n)).collect();
    let keys: Vec<&str> = keys.iter().map(String::as_str).collect();
    let text = keys[..10].join(" ");
    let (ks, out) = run(&keys, &text);
    assert_eq!(ks.much_less_line, 1);
    assert_eq!(out.len(), 20);
    assert!(out[10..].iter().all(|row| row.value.is_empty()));
}

#[test]
fn extra_group_is_recorded_with_a_counted_prefix_row() {
    let text = "1001 9999 1002 1003 1004 1005 1006 1007 1008 1009\n1010";
    let (_, out) = run(&TEN, text);
    assert_eq!(out[1].value, "0,9999");
    assert_eq!(out[2].value, "1,1002");
}

#[test]
fn a_selector_followed_by_a_group_corrects_the_row() {
    let text = "1001 9999 2 1002 1003 1004 1005 1006 1007 1008 1009 1010";
    let (ks, _) = run(&TEN, text);
    assert_eq!(ks.correct_mistakes, 1);
}

#[test]
fn a_numeric_selector_without_following_group_is_dropped() {
    let text = format!("{} 3", TEN.join(" "));
    let (ks, _) = run(&TEN, &text);
    assert_eq!(ks.error_code, 0);
}

#[test]
fn a_mismatch_in_the_last_row_of_a_partial_page_throws_like_java() {
    // Java's lack search reads page groups past the end of a page that is not a multiple of
    // ten (IndexOutOfBounds -> HTTP 500).
    let mut ks = Stats::default();
    let mut out = Vec::new();
    let result = handle(1, &pages(&["1001", "1002"]), "1001 9999", &mut ks, &mut out);
    assert!(result.is_err());
}

#[test]
fn hyphen_patterns() {
    assert_eq!(check_hyphen_pattern("abc"), 4);
    assert_eq!(check_hyphen_pattern("3---5"), 1);
    assert_eq!(check_hyphen_pattern("5---3"), 3);
    assert_eq!(check_hyphen_pattern("3-5"), 2);
    assert_eq!(extract_numbers_around_hyphens("3---5"), vec![3, 5]);
    assert!(extract_numbers_around_hyphens("x---5").is_empty());
}

#[test]
fn a_page_marker_is_checked_per_page_from_one_hundred_groups() {
    let keys: Vec<String> = (0..100).map(|n| format!("{:04}", n)).collect();
    let keys: Vec<&str> = keys.iter().map(String::as_str).collect();
    let body = keys.join(" ");
    let (ks, _) = run(&keys, &format!("{body} -1"));
    assert_eq!((ks.less_page, ks.error_page), (0, 0));
    let (ks, _) = run(&keys, &format!("{body} -2"));
    assert_eq!((ks.less_page, ks.error_page), (0, 1));
    let (ks, _) = run(&keys, &body);
    assert_eq!((ks.less_page, ks.error_page), (1, 0));
}

#[test]
fn selector_out_of_range_throws_like_java() {
    let mut ks = Stats::default();
    let mut out = Vec::new();
    let result = handle(1, &pages(&["1111"]), "QTA abc", &mut ks, &mut out);
    assert!(result.is_err());
}
