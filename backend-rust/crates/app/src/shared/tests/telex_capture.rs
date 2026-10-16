use crate::shared::telex_capture::character_count;

#[test]
fn preserves_java_transmitted_text_and_command_boundaries() {
    let cases = [
        ("1234 5678", 8, 8),
        ("12// 9999", 6, 8),
        ("QTA 3---5 ADD 1---2 1234 5678", 8, 20),
        ("QTA", 3, 3),
        ("ADD 1---2 ABCD", 10, 10),
        ("01 ABCD EFGH", 10, 10),
        ("+1 ABCD EFGH", 8, 10),
        ("1P 2 ABCD", 4, 7),
        ("01P 2 ABCD", 7, 8),
        ("ABCD-1/2/3", 4, 4),
        ("ABCD-01/2", 8, 9),
        ("////", 4, 4),
        ("ABCD ////", 8, 8),
        ("ABCD //// EFGH", 8, 12),
        ("ABCD ///#@", 9, 9),
        ("ABCD /EFGH", 8, 9),
        ("汉/ ABCD", 5, 6),
        ("ͅ/ ABCD", 6, 6),
        ("Ⅰ/ ABCD", 6, 6),
        ("½/ ABCD", 6, 6),
        ("𠀀/ ABCD", 6, 6),
        ("١ ABCD EFGH", 8, 9),
        ("　AB\tCD EF", 6, 6),
        ("QTA +1", 0, 5),
        ("QTA 01", 0, 5),
        ("QTA 001---003", 12, 12),
        ("ADD 1---2 ABCD EFGH", 8, 14),
        ("/AB_C", 5, 5),
        ("A /AB_C", 5, 6),
        ("/A/B", 2, 4),
    ];
    for (text, telex, plain) in cases {
        assert_eq!(character_count(text, Some(0)), telex, "telex: {text:?}");
        assert_eq!(character_count(text, Some(4)), plain, "plain: {text:?}");
    }
}
