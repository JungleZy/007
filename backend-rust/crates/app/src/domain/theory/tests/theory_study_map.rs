use crate::domain::theory::theory::*;

#[test]
fn java_merge_bucket_order_and_utf16_hash_match_jdk21() {
    let mut durations = JavaStudyDurations::default();
    for key in ["AaAa", "AaBB", "BBAa", "BBBB"] {
        durations.merge(key.to_owned(), 1);
    }
    durations.merge("AaAa".to_owned(), 5);
    assert_eq!(
        durations
            .ordered()
            .map(|(key, seconds)| (key.as_str(), seconds))
            .collect::<Vec<_>>(),
        [("BBBB", 1), ("BBAa", 1), ("AaBB", 1), ("AaAa", 6)]
    );
    assert_eq!(java_string_hash("学习😀"), 718097488);
}

#[test]
fn java_merge_resize_preserves_split_chains_and_pre_call_threshold() {
    let mut durations = JavaStudyDurations::default();
    for index in 0..13 {
        durations.merge(format!("k{index}"), 1);
    }
    assert_eq!(durations.capacity, 16);
    durations.merge("k13".to_owned(), 1);
    assert_eq!(durations.capacity, 32);
    assert_eq!(
        durations
            .ordered()
            .map(|(key, _)| key.as_str())
            .collect::<Vec<_>>(),
        ["k0", "k1", "k2", "k3", "k4", "k11", "k5", "k10", "k6", "k13", "k7", "k12", "k8", "k9"]
    );
}
