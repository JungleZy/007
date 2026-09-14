//! 手写 RFC6455：服务端只需发不掩码的文本/二进制帧，并能读客户端掩码帧。
//! SHA-1 与 base64 一并自带，避免为握手引入依赖。

use std::io::{self, Read, Write};
use std::net::TcpStream;

pub const OP_TEXT: u8 = 0x1;
pub const OP_BINARY: u8 = 0x2;
pub const OP_CLOSE: u8 = 0x8;
pub const OP_PING: u8 = 0x9;
pub const OP_PONG: u8 = 0xA;

const GUID: &str = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

// ------------------------------------------------------------------ SHA-1

pub fn sha1(data: &[u8]) -> [u8; 20] {
    let mut state: [u32; 5] = [0x6745_2301, 0xEFCD_AB89, 0x98BA_DCFE, 0x1032_5476, 0xC3D2_E1F0];
    let mut message = data.to_vec();
    let bit_length = (data.len() as u64) * 8;
    message.push(0x80);
    while message.len() % 64 != 56 {
        message.push(0);
    }
    message.extend_from_slice(&bit_length.to_be_bytes());

    for block in message.chunks(64) {
        let mut words = [0u32; 80];
        for (index, chunk) in block.chunks(4).enumerate() {
            words[index] = u32::from_be_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
        }
        for index in 16..80 {
            words[index] = (words[index - 3] ^ words[index - 8] ^ words[index - 14] ^ words[index - 16]).rotate_left(1);
        }
        let [mut a, mut b, mut c, mut d, mut e] = state;
        for (index, word) in words.iter().enumerate() {
            let (f, k) = match index {
                0..=19 => ((b & c) | ((!b) & d), 0x5A82_7999u32),
                20..=39 => (b ^ c ^ d, 0x6ED9_EBA1),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1B_BCDC),
                _ => (b ^ c ^ d, 0xCA62_C1D6),
            };
            let temp = a
                .rotate_left(5)
                .wrapping_add(f)
                .wrapping_add(e)
                .wrapping_add(k)
                .wrapping_add(*word);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = temp;
        }
        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
        state[4] = state[4].wrapping_add(e);
    }

    let mut digest = [0u8; 20];
    for (index, word) in state.iter().enumerate() {
        digest[index * 4..index * 4 + 4].copy_from_slice(&word.to_be_bytes());
    }
    digest
}

const BASE64: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

pub fn base64(data: &[u8]) -> String {
    let mut out = String::with_capacity((data.len() + 2) / 3 * 4);
    for chunk in data.chunks(3) {
        let bytes = [chunk[0], chunk.get(1).copied().unwrap_or(0), chunk.get(2).copied().unwrap_or(0)];
        let triple = ((bytes[0] as u32) << 16) | ((bytes[1] as u32) << 8) | bytes[2] as u32;
        out.push(BASE64[(triple >> 18) as usize & 0x3f] as char);
        out.push(BASE64[(triple >> 12) as usize & 0x3f] as char);
        out.push(if chunk.len() > 1 { BASE64[(triple >> 6) as usize & 0x3f] as char } else { '=' });
        out.push(if chunk.len() > 2 { BASE64[triple as usize & 0x3f] as char } else { '=' });
    }
    out
}

pub fn accept_key(client_key: &str) -> String {
    base64(&sha1(format!("{client_key}{GUID}").as_bytes()))
}

// ------------------------------------------------------------------ 帧编解码

pub fn encode(opcode: u8, payload: &[u8]) -> Vec<u8> {
    let mut frame = Vec::with_capacity(payload.len() + 10);
    frame.push(0x80 | opcode);
    if payload.len() < 126 {
        frame.push(payload.len() as u8);
    } else if payload.len() < 65536 {
        frame.push(126);
        frame.extend_from_slice(&(payload.len() as u16).to_be_bytes());
    } else {
        frame.push(127);
        frame.extend_from_slice(&(payload.len() as u64).to_be_bytes());
    }
    frame.extend_from_slice(payload);
    frame
}

/// 客户端 -> 服务端必须掩码；这里按掩码写，供 CDP 客户端使用
pub fn encode_masked(opcode: u8, payload: &[u8], mask: [u8; 4]) -> Vec<u8> {
    let mut frame = Vec::with_capacity(payload.len() + 14);
    frame.push(0x80 | opcode);
    let length_byte = if payload.len() < 126 {
        payload.len() as u8
    } else if payload.len() < 65536 {
        126
    } else {
        127
    };
    frame.push(0x80 | length_byte);
    if length_byte == 126 {
        frame.extend_from_slice(&(payload.len() as u16).to_be_bytes());
    } else if length_byte == 127 {
        frame.extend_from_slice(&(payload.len() as u64).to_be_bytes());
    }
    frame.extend_from_slice(&mask);
    frame.extend(payload.iter().enumerate().map(|(index, byte)| byte ^ mask[index % 4]));
    frame
}

#[derive(Debug, Clone)]
pub struct Message {
    pub opcode: u8,
    pub payload: Vec<u8>,
}

/// 增量解析：喂字节，取出完整消息（自动重组分片）
#[derive(Default)]
pub struct Parser {
    buffer: Vec<u8>,
    fragments: Vec<u8>,
    fragment_opcode: u8,
}

impl Parser {
    pub fn push(&mut self, chunk: &[u8]) -> Vec<Message> {
        self.buffer.extend_from_slice(chunk);
        let mut messages = Vec::new();
        loop {
            if self.buffer.len() < 2 {
                return messages;
            }
            let first = self.buffer[0];
            let second = self.buffer[1];
            let fin = first & 0x80 != 0;
            let opcode = first & 0x0f;
            let masked = second & 0x80 != 0;
            let mut length = (second & 0x7f) as usize;
            let mut offset = 2usize;
            if length == 126 {
                if self.buffer.len() < 4 {
                    return messages;
                }
                length = u16::from_be_bytes([self.buffer[2], self.buffer[3]]) as usize;
                offset = 4;
            } else if length == 127 {
                if self.buffer.len() < 10 {
                    return messages;
                }
                length = u64::from_be_bytes([
                    self.buffer[2], self.buffer[3], self.buffer[4], self.buffer[5],
                    self.buffer[6], self.buffer[7], self.buffer[8], self.buffer[9],
                ]) as usize;
                offset = 10;
            }
            let mask = if masked {
                if self.buffer.len() < offset + 4 {
                    return messages;
                }
                let mask = [
                    self.buffer[offset],
                    self.buffer[offset + 1],
                    self.buffer[offset + 2],
                    self.buffer[offset + 3],
                ];
                offset += 4;
                Some(mask)
            } else {
                None
            };
            if self.buffer.len() < offset + length {
                return messages;
            }
            let mut payload = self.buffer[offset..offset + length].to_vec();
            if let Some(mask) = mask {
                for (index, byte) in payload.iter_mut().enumerate() {
                    *byte ^= mask[index % 4];
                }
            }
            self.buffer.drain(..offset + length);

            match opcode {
                0x0 => self.fragments.extend_from_slice(&payload),
                _ => {
                    self.fragments = payload;
                    self.fragment_opcode = opcode;
                }
            }
            if fin {
                messages.push(Message {
                    opcode: self.fragment_opcode,
                    payload: std::mem::take(&mut self.fragments),
                });
            }
        }
    }
}

/// 读一段 HTTP 请求头（到空行为止）
pub fn read_headers(stream: &mut TcpStream) -> io::Result<String> {
    let mut head = Vec::new();
    let mut byte = [0u8; 1];
    while !head.ends_with(b"\r\n\r\n") {
        if stream.read(&mut byte)? == 0 {
            break;
        }
        head.push(byte[0]);
        if head.len() > 16384 {
            break;
        }
    }
    Ok(String::from_utf8_lossy(&head).to_string())
}

pub fn header_value(head: &str, name: &str) -> Option<String> {
    let lowered = name.to_ascii_lowercase();
    head.lines()
        .filter_map(|line| line.split_once(':'))
        .find(|(key, _)| key.trim().to_ascii_lowercase() == lowered)
        .map(|(_, value)| value.trim().to_string())
}

pub fn write_handshake(stream: &mut TcpStream, client_key: &str) -> io::Result<()> {
    let response = format!(
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
        accept_key(client_key)
    );
    stream.write_all(response.as_bytes())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// RFC6455 的握手样例：dGhlIHNhbXBsZSBub25jZQ== 必须得到 s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
    fn handshake_matches_rfc_example() {
        assert_eq!(accept_key("dGhlIHNhbXBsZSBub25jZQ=="), "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    #[test]
    /// SHA-1 与 base64 的已知值
    fn digest_and_base64_are_correct() {
        assert_eq!(
            sha1(b"abc").iter().map(|b| format!("{b:02x}")).collect::<String>(),
            "a9993e364706816aba3e25717850c26c9cd0d89d"
        );
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
        assert_eq!(base64(b"hello world"), "aGVsbG8gd29ybGQ=");
    }

    #[test]
    /// 长度分档：7 位 / 16 位 / 服务端不掩码
    fn length_fields_are_chosen_by_size() {
        assert_eq!(encode(OP_TEXT, b"ab")[0], 0x81);
        assert_eq!(encode(OP_TEXT, b"ab")[1], 2);
        assert_eq!(encode(OP_TEXT, &vec![b'x'; 200])[1], 126);
        assert_eq!(encode(OP_TEXT, b"ab")[1] & 0x80, 0, "服务端帧不得掩码");
    }

    #[test]
    /// 掩码帧与分片都能被解析回原文
    fn parser_reassembles_masked_fragments() {
        let mut parser = Parser::default();
        let mut wire = encode_masked(OP_TEXT, b"{\"type\":0,", [0x12, 0x34, 0x56, 0x78]);
        wire[0] &= 0x7f; // FIN=0
        let mut tail = encode_masked(0x0, b"\"fre\":1200}", [0x9a, 0xbc, 0xde, 0xf0]);
        tail[0] |= 0x80;
        let mut messages = parser.push(&wire);
        assert!(messages.is_empty(), "分片未结束不应产出消息");
        messages = parser.push(&tail);
        assert_eq!(messages.len(), 1);
        assert_eq!(String::from_utf8_lossy(&messages[0].payload), "{\"type\":0,\"fre\":1200}");
    }
}
