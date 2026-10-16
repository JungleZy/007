use crate::domain::post_telegram::post_telegram_write::generated_groups;

#[test]
fn unfilled_pages_keep_the_same_source_rules() {
    assert_eq!(
        generated_groups(104, 0, false, false)[100..],
        ["0123", "4567", "8901", "2345"]
    );
    assert_eq!(generated_groups(104, 1, false, false)[100], "KLMN");
    assert_eq!(generated_groups(104, 2, false, false)[100], "4567");
    for _ in 0..20 {
        let numbers = generated_groups(14, 0, true, true);
        let mut counts = [0_usize; 10];
        for group in numbers.iter().take(10) {
            let chars: Vec<_> = group.bytes().collect();
            assert_eq!(chars.len(), 4);
            assert_eq!(
                chars
                    .iter()
                    .filter(|&&code| (b'1'..=b'5').contains(&code))
                    .count(),
                2
            );
            for &code in &chars {
                counts[(code - b'0') as usize] += 1;
            }
            assert_eq!(
                chars
                    .iter()
                    .copied()
                    .collect::<std::collections::HashSet<_>>()
                    .len(),
                4
            );
        }
        assert!(counts.into_iter().all(|count| count == 4));
        for (domain, alphabet) in [
            (1, b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".as_slice()),
            (2, b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".as_slice()),
        ] {
            let groups = generated_groups(104, domain, true, true);
            assert_eq!(groups.len(), 104);
            for group in &groups {
                assert_eq!(group.len(), 4);
                assert_eq!(
                    group
                        .bytes()
                        .collect::<std::collections::HashSet<_>>()
                        .len(),
                    4
                );
            }
            for &code in alphabet {
                let expected = (0..104)
                    .flat_map(|index| {
                        (0..4).map(move |slot| {
                            let position = if domain == 1 {
                                index % 100 * 4 + slot
                            } else {
                                index * 4 + slot
                            };
                            alphabet[position % alphabet.len()]
                        })
                    })
                    .filter(|&value| value == code)
                    .count();
                let actual = groups
                    .iter()
                    .map(|group| group.bytes().filter(|&value| value == code).count())
                    .sum::<usize>();
                assert_eq!(actual, expected);
            }
        }
    }
}
