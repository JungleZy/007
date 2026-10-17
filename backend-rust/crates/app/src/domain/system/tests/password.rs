use crate::domain::system::password::*;

#[test]
fn verifies_java_pbkdf2_vector_and_marks_upgrade() {
    let stored =
        "pbkdf2_sha256$1$1$MDEyMzQ1Njc4OWFiY2RlZg$751fat1KXRn0p_yStI8jUeqVu5d2QsAHHtTkAQpCy2w";
    assert_eq!(
        verify("password", Some(stored)),
        Verification {
            matches: true,
            needs_upgrade: true
        }
    );
    assert!(!verify("wrong", Some(stored)).matches);
}

#[test]
fn verifies_legacy_md5_and_marks_upgrade() {
    assert_eq!(
        verify("password", Some("5f4dcc3b5aa765d61d8327deb882cf99")),
        Verification {
            matches: true,
            needs_upgrade: true
        }
    );
    assert!(!verify("wrong", Some("5f4dcc3b5aa765d61d8327deb882cf99")).matches);
}

#[test]
fn new_hash_and_token_have_java_compatible_shapes() {
    let encoded = hash("password").expect("hash");
    assert_eq!(encoded.split('$').count(), 5);
    assert_eq!(encoded.split('$').nth(3).expect("salt").len(), 22);
    assert_eq!(encoded.split('$').nth(4).expect("derived").len(), 43);
    assert_eq!(verify("password", Some(&encoded)).matches, true);
    assert_eq!(issue_token().len(), 43);
}
