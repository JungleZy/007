use rand::seq::SliceRandom;
use rand::Rng;

fn separate_groups(values: &mut [u8], group_size: usize, rng: &mut impl Rng) {
    values.shuffle(rng);
    for start in (0..values.len()).step_by(group_size) {
        for index in start..start + group_size {
            let repeated = values[index];
            if !values[start..index].contains(&repeated) {
                continue;
            }
            if let Some(other) = (start + group_size..values.len())
                .find(|&other| !values[start..index].contains(&values[other]))
            {
                values.swap(index, other);
            } else if let Some(other) = (0..start).find(|&other| {
                !values[start..index].contains(&values[other])
                    && !values[other / group_size * group_size
                        ..other / group_size * group_size + group_size]
                        .contains(&repeated)
            }) {
                values.swap(index, other);
            }
        }
    }
}

pub(crate) fn generated_groups(
    count: usize,
    train_type: i32,
    random: bool,
    average: bool,
) -> Vec<String> {
    let alphabet: &[u8] = match train_type {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    };
    let mut rng = rand::thread_rng();
    let mut result = Vec::with_capacity(count);
    for (page, start) in (0..count).step_by(100).enumerate() {
        let batch = (count - start).min(100);
        if random && average && train_type == 0 {
            for ten in (0..batch).step_by(10) {
                let groups = (batch - ten).min(10);
                let mut low = [0_u8; 20];
                let mut high = [0_u8; 20];
                for index in 0..groups * 2 {
                    low[index] = b'1' + (index % 5) as u8;
                    high[index] = b'6' + (index % 5) as u8;
                    if high[index] == b':' {
                        high[index] = b'0';
                    }
                }
                separate_groups(&mut low[..groups * 2], 2, &mut rng);
                separate_groups(&mut high[..groups * 2], 2, &mut rng);
                for index in 0..groups {
                    let [l1, l2] = [low[index * 2], low[index * 2 + 1]];
                    let [r1, r2] = [high[index * 2], high[index * 2 + 1]];
                    let codes = if rng.gen_bool(0.5) {
                        [l1, r1, l2, r2]
                    } else {
                        [r1, l1, r2, l2]
                    };
                    let mut group = String::with_capacity(4);
                    for code in codes {
                        group.push(code as char);
                    }
                    result.push(group);
                }
            }
        } else if random && average {
            let mut symbols = [0_u8; 400];
            let offset = if train_type == 1 { 0 } else { page * 400 };
            let codes = &mut symbols[..batch * 4];
            for (index, code) in codes.iter_mut().enumerate() {
                *code = alphabet[(offset + index) % alphabet.len()];
            }
            separate_groups(codes, 4, &mut rng);
            for codes in codes.chunks_exact(4) {
                let mut group = String::with_capacity(4);
                for &code in codes {
                    group.push(code as char);
                }
                result.push(group);
            }
        } else {
            for index in 0..batch {
                loop {
                    let mut group = String::with_capacity(4);
                    if random {
                        while group.len() < 4 {
                            let code = alphabet[rng.gen_range(0..alphabet.len())] as char;
                            if !group.contains(code) {
                                group.push(code);
                            }
                        }
                    } else {
                        for slot in 0..4 {
                            group.push(
                                alphabet[((start + index) * 4 + slot) % alphabet.len()] as char,
                            );
                        }
                    }
                    if !random || !result[result.len().saturating_sub(10)..].contains(&group) {
                        result.push(group);
                        break;
                    }
                }
            }
        }
    }
    result
}
