//! Isolated-DB regression: a business message waiting on a row lock must not stall the
//! connection's heartbeats (no production database URLs).
use super::*;
use crate::{build_router, token_digest, AppConfig};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

/// Bare RFC 6455 client: masked text frames out, unfragmented frames in.
struct Client {
    stream: TcpStream,
    buffer: Vec<u8>,
}

impl Client {
    async fn connect(address: std::net::SocketAddr, path: &str) -> Self {
        let mut stream = TcpStream::connect(address).await.unwrap();
        let request = format!(
            "GET {path} HTTP/1.1\r\nHost: {address}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
        );
        stream.write_all(request.as_bytes()).await.unwrap();
        let mut buffer = Vec::new();
        let end = loop {
            let mut chunk = [0u8; 1024];
            let read = stream.read(&mut chunk).await.unwrap();
            assert!(read > 0, "connection closed during the handshake");
            buffer.extend_from_slice(&chunk[..read]);
            if let Some(end) = buffer.windows(4).position(|window| window == b"\r\n\r\n") {
                break end + 4;
            }
        };
        assert!(
            buffer.starts_with(b"HTTP/1.1 101"),
            "{}",
            String::from_utf8_lossy(&buffer)
        );
        Self {
            stream,
            buffer: buffer.split_off(end),
        }
    }

    async fn send_text(&mut self, text: &str) {
        let payload = text.as_bytes();
        let mut frame = vec![0x81];
        if payload.len() < 126 {
            frame.push(0x80 | payload.len() as u8);
        } else {
            frame.push(0x80 | 126);
            frame.extend_from_slice(&(payload.len() as u16).to_be_bytes());
        }
        let mask = [0x11, 0x22, 0x33, 0x44];
        frame.extend_from_slice(&mask);
        frame.extend(
            payload
                .iter()
                .enumerate()
                .map(|(index, byte)| byte ^ mask[index % 4]),
        );
        self.stream.write_all(&frame).await.unwrap();
    }

    async fn fill(&mut self, count: usize) {
        while self.buffer.len() < count {
            let mut chunk = [0u8; 4096];
            let read = self.stream.read(&mut chunk).await.unwrap();
            assert!(read > 0, "connection closed");
            self.buffer.extend_from_slice(&chunk[..read]);
        }
    }

    /// The next text frame, or None if none arrives within `limit`.
    async fn next_text(&mut self, limit: Duration) -> Option<String> {
        tokio::time::timeout(limit, async {
            loop {
                self.fill(2).await;
                let (opcode, short) = (self.buffer[0] & 0x0F, (self.buffer[1] & 0x7F) as usize);
                let (header, size) = if short == 126 {
                    self.fill(4).await;
                    (
                        4,
                        u16::from_be_bytes([self.buffer[2], self.buffer[3]]) as usize,
                    )
                } else {
                    (2, short)
                };
                self.fill(header + size).await;
                let frame: Vec<u8> = self.buffer.drain(..header + size).collect();
                if opcode == 0x1 {
                    return String::from_utf8(frame[header..].to_vec()).unwrap();
                }
            }
        })
        .await
        .ok()
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn business_message_waiting_on_a_row_lock_does_not_stall_heartbeats() {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
    assert!(url.ends_with("/rust_scratch_lockdemo"));
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(8)
        .connect(&url)
        .await
        .unwrap();
    let tag = Uuid::new_v4().simple().to_string();
    let (user, token) = (format!("ws-worker-{tag}"), format!("ws-worker-token-{tag}"));
    sqlx::query("INSERT INTO t_user (id,user_account,user_name,status,device_id,token) VALUES (?,?,'ws worker regression',0,'ws-worker-dev',?)")
        .bind(&user)
        .bind(&user)
        .bind(token_digest(&token))
        .execute(&pool)
        .await
        .unwrap();
    let mut config = AppConfig::from_env().unwrap();
    config.ws_readonly_enabled = true;
    let state = AppState {
        config: Arc::new(config),
        pool: Some(pool.clone()),
        database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
    };
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let server = tokio::spawn(async move { axum::serve(listener, build_router(state)).await });

    // Hold the user row the 2001 capture locks first (telegram_lifecycle::save_captured_log).
    let mut gate = pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM t_user WHERE id=? FOR UPDATE")
        .bind(&user)
        .fetch_one(&mut *gate)
        .await
        .unwrap();
    let mut client = Client::connect(
        address,
        &format!("/websocket/ws-worker?token={token}&deviceId=ws-worker-dev"),
    )
    .await;
    tokio::time::sleep(Duration::from_millis(300)).await;
    client
        .send_text(r#"{"code":2001,"data":{"telegramTrainId":"ws-worker-missing-train","type":1,"value":"1"}}"#)
        .await;
    tokio::time::sleep(Duration::from_millis(300)).await;
    client.send_text(HEARTBEAT_PING).await;
    let heartbeat_while_blocked = client.next_text(Duration::from_secs(3)).await;
    gate.commit().await.unwrap();
    let capture_reply = client.next_text(Duration::from_secs(10)).await;

    server.abort();
    sqlx::query("DELETE FROM t_user WHERE id=?")
        .bind(&user)
        .execute(&pool)
        .await
        .unwrap();
    assert_eq!(heartbeat_while_blocked.as_deref(), Some(HEARTBEAT_PONG));
    let reply: Value = serde_json::from_str(
        capture_reply
            .as_deref()
            .expect("capture reply after release"),
    )
    .unwrap();
    assert_ne!(
        reply["code"],
        json!(200),
        "the blocked capture must finish after the gate: {reply}"
    );
}
