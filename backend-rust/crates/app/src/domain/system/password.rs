use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use md5::{Digest as Md5Digest, Md5};
use pbkdf2::pbkdf2_hmac;
use rand::{rngs::OsRng, RngCore};
use sha2::Sha256;
use subtle::ConstantTimeEq;

const CURRENT_ITERATIONS: u32 = 600_000;
const MAX_ITERATIONS: u32 = 2_000_000;
const SALT_LENGTH: usize = 16;
const KEY_LENGTH: usize = 32;

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub(super) struct Verification {
    pub(super) matches: bool,
    pub(super) needs_upgrade: bool,
}

pub(super) fn verify(plaintext: &str, stored: Option<&str>) -> Verification {
    let Some(stored) = stored.filter(|value| !value.is_empty() && value.len() <= 128) else {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    };
    if plaintext.is_empty() {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    }
    if is_legacy_hex(stored) {
        let mut hasher = Md5::new();
        hasher.update(plaintext.as_bytes());
        let actual = hasher.finalize();
        let expected = decode_hex(stored).expect("legacy hex was validated");
        let matches = actual.as_slice().ct_eq(&expected).into();
        return Verification {
            matches,
            needs_upgrade: matches,
        };
    }

    let fields: Vec<&str> = stored.split('$').collect();
    if fields.len() != 5
        || fields[0] != "pbkdf2_sha256"
        || fields[1] != "1"
        || fields[2].is_empty()
        || fields[2].len() > 7
        || !fields[2].bytes().all(|byte| byte.is_ascii_digit())
        || fields[3].len() != 22
        || fields[4].len() != 43
    {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    }
    let Ok(iterations) = fields[2].parse::<u32>() else {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    };
    if !(1..=MAX_ITERATIONS).contains(&iterations) {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    }
    let Ok(salt) = URL_SAFE_NO_PAD.decode(fields[3]) else {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    };
    let Ok(expected) = URL_SAFE_NO_PAD.decode(fields[4]) else {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    };
    if salt.len() != SALT_LENGTH || expected.len() != KEY_LENGTH {
        return Verification {
            matches: false,
            needs_upgrade: false,
        };
    }
    let mut actual = [0u8; KEY_LENGTH];
    pbkdf2_hmac::<Sha256>(plaintext.as_bytes(), &salt, iterations, &mut actual);
    let matches = actual.as_slice().ct_eq(&expected).into();
    Verification {
        matches,
        needs_upgrade: matches && iterations < CURRENT_ITERATIONS,
    }
}

pub(super) fn hash(plaintext: &str) -> Result<String, &'static str> {
    if plaintext.is_empty() {
        return Err("password must not be empty");
    }
    let mut salt = [0u8; SALT_LENGTH];
    OsRng.fill_bytes(&mut salt);
    let mut derived = [0u8; KEY_LENGTH];
    pbkdf2_hmac::<Sha256>(
        plaintext.as_bytes(),
        &salt,
        CURRENT_ITERATIONS,
        &mut derived,
    );
    Ok(format!(
        "pbkdf2_sha256$1${CURRENT_ITERATIONS}${}${}",
        URL_SAFE_NO_PAD.encode(salt),
        URL_SAFE_NO_PAD.encode(derived)
    ))
}

pub(super) fn issue_token() -> String {
    let mut bytes = [0u8; 32];
    OsRng.fill_bytes(&mut bytes);
    URL_SAFE_NO_PAD.encode(bytes)
}

fn is_legacy_hex(value: &str) -> bool {
    value.len() == 32 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn decode_hex(value: &str) -> Option<Vec<u8>> {
    value
        .as_bytes()
        .chunks_exact(2)
        .map(|chunk| {
            let high = (chunk[0] as char).to_digit(16)? as u8;
            let low = (chunk[1] as char).to_digit(16)? as u8;
            Some((high << 4) | low)
        })
        .collect()
}
