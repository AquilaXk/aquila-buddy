use serde::Serialize;
use std::io::{Read, Write};
use std::net::TcpListener;
use std::sync::{mpsc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

const MAX_HTTP_REQUEST_BYTES: usize = 4_096;
const LOOPBACK_TIMEOUT: Duration = Duration::from_secs(120);
const SOCKET_IO_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct DesktopOAuthCallbackPayload {
    pub code: Option<String>,
    pub error: Option<String>,
}

#[derive(Default)]
pub struct OAuthLoopbackState {
    receiver: Mutex<Option<mpsc::Receiver<Result<DesktopOAuthCallbackPayload, String>>>>,
}

#[inline]
fn hex_nibble(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

fn percent_decode(raw: &str) -> String {
    let bytes = raw.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'+' => {
                out.push(b' ');
                i += 1;
            }
            b'%' if i + 2 < bytes.len() => {
                if let (Some(hi), Some(lo)) = (hex_nibble(bytes[i + 1]), hex_nibble(bytes[i + 2])) {
                    out.push((hi << 4) | lo);
                    i += 3;
                    continue;
                }
                out.push(b'%');
                i += 1;
            }
            byte => {
                out.push(byte);
                i += 1;
            }
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn parse_loopback_request(request_text: &str) -> Result<DesktopOAuthCallbackPayload, String> {
    let first_line = request_text
        .lines()
        .next()
        .ok_or_else(|| "빈 OAuth callback 요청입니다.".to_string())?;
    let mut parts = first_line.split_whitespace();
    let method = parts.next().unwrap_or("");
    let target = parts.next().unwrap_or("");
    let (path, query) = target.split_once('?').unwrap_or((target, ""));
    if method != "GET" || path != "/oauth/callback" {
        return Err("허용되지 않은 callback 경로입니다.".to_string());
    }

    let mut code: Option<String> = None;
    let mut error: Option<String> = None;

    for pair in query.split('&') {
        if pair.is_empty() {
            continue;
        }
        let (raw_key, raw_val) = pair.split_once('=').unwrap_or((pair, ""));
        let key = percent_decode(raw_key);
        let value = percent_decode(raw_val);
        if key == "ext_code" && !value.is_empty() {
            code = Some(value);
        } else if key == "error" && !value.is_empty() {
            error = Some(value);
        }
    }

    if code.is_none() && error.is_none() {
        return Err("인증 코드 또는 오류 파라미터가 누락되었습니다.".to_string());
    }

    Ok(DesktopOAuthCallbackPayload { code, error })
}

fn send_http_response(stream: &mut std::net::TcpStream, status_line: &str, body_html: &str) {
    let response = format!(
        "HTTP/1.1 {status_line}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n{body_html}",
        body_html.len()
    );
    let _ = stream.write_all(response.as_bytes());
    let _ = stream.flush();
}

#[tauri::command]
pub fn oauth_start_loopback(state: tauri::State<'_, OAuthLoopbackState>) -> Result<u16, String> {
    start_loopback_inner(&state, LOOPBACK_TIMEOUT)
}

#[tauri::command]
pub async fn oauth_await_loopback(
    state: tauri::State<'_, OAuthLoopbackState>,
) -> Result<DesktopOAuthCallbackPayload, String> {
    await_loopback_inner(&state, LOOPBACK_TIMEOUT + SOCKET_IO_TIMEOUT).await
}

fn start_loopback_inner(state: &OAuthLoopbackState, timeout: Duration) -> Result<u16, String> {
    let listener = TcpListener::bind("127.0.0.1:0")
        .map_err(|err| format!("OAuth 루프백 포트 바인딩에 실패했습니다: {err}"))?;
    let port = listener
        .local_addr()
        .map_err(|err| format!("루프백 포트 확인에 실패했습니다: {err}"))?
        .port();
    listener
        .set_nonblocking(true)
        .map_err(|err| format!("루프백 소켓 설정에 실패했습니다: {err}"))?;

    let (tx, rx) = mpsc::channel();
    {
        let mut guard = state
            .receiver
            .lock()
            .map_err(|err| format!("OAuth 상태 잠금 오류: {err}"))?;
        *guard = Some(rx);
    }

    thread::spawn(move || {
        let deadline = Instant::now() + timeout;
        loop {
            match listener.accept() {
                Ok((mut stream, _)) => {
                    let _ = stream.set_nonblocking(false);
                    let _ = stream.set_read_timeout(Some(SOCKET_IO_TIMEOUT));
                    let _ = stream.set_write_timeout(Some(SOCKET_IO_TIMEOUT));

                    let mut buffer = [0u8; MAX_HTTP_REQUEST_BYTES + 1];
                    let mut total_read = 0usize;
                    let mut hit_cap = false;

                    loop {
                        match stream.read(&mut buffer[total_read..]) {
                            Ok(0) => break,
                            Ok(n) => {
                                total_read += n;
                                if total_read > MAX_HTTP_REQUEST_BYTES {
                                    hit_cap = true;
                                    break;
                                }
                                if buffer[..total_read].windows(4).any(|w| w == b"\r\n\r\n") {
                                    break;
                                }
                            }
                            Err(_) => break,
                        }
                    }

                    if hit_cap {
                        send_http_response(
                            &mut stream,
                            "413 Payload Too Large",
                            "<!doctype html><html><body><p>요청 크기가 허용 한도를 초과했습니다.</p></body></html>",
                        );
                        let _ = tx.send(Err("OAuth callback 요청 크기가 상한(4KB)을 초과했습니다.".to_string()));
                        break;
                    }

                    let request_text = String::from_utf8_lossy(&buffer[..total_read]);
                    let first_target = request_text
                        .lines()
                        .next()
                        .and_then(|line| line.split_whitespace().nth(1))
                        .unwrap_or("");
                    if first_target == "/favicon.ico" {
                        send_http_response(&mut stream, "404 Not Found", "");
                        continue;
                    }

                    match parse_loopback_request(&request_text) {
                        Ok(payload) => {
                            let body = if payload.error.is_some() {
                                "<!doctype html><html><body style='font-family:system-ui,sans-serif;padding:32px;color:#1f2328'><h2>인증이 취소되었거나 실패했습니다</h2><p>이 창을 닫고 Aquila Buddy 앱에서 다시 시도하세요.</p></body></html>"
                            } else {
                                "<!doctype html><html><body style='font-family:system-ui,sans-serif;padding:32px;color:#1f2328'><h2>Aquila Buddy 로그인 완료</h2><p>이 창을 닫고 데스크톱 앱으로 돌아갈 수 있습니다.</p><script>window.close();</script></body></html>"
                            };
                            send_http_response(&mut stream, "200 OK", body);
                            let _ = tx.send(Ok(payload));
                            break;
                        }
                        Err(err) => {
                            send_http_response(
                                &mut stream,
                                "400 Bad Request",
                                "<!doctype html><html><body><p>유효하지 않은 OAuth callback 요청입니다.</p></body></html>",
                            );
                            let _ = tx.send(Err(err));
                            break;
                        }
                    }
                }
                Err(err) if err.kind() == std::io::ErrorKind::WouldBlock => {
                    if Instant::now() >= deadline {
                        let _ = tx.send(Err("GitHub 로그인 대기 시간이 만료되었습니다.".to_string()));
                        break;
                    }
                    thread::sleep(Duration::from_millis(25));
                }
                Err(err) => {
                    let _ = tx.send(Err(format!("OAuth 루프백 수신 실패: {err}")));
                    break;
                }
            }
        }
    });

    Ok(port)
}

async fn await_loopback_inner(
    state: &OAuthLoopbackState,
    timeout: Duration,
) -> Result<DesktopOAuthCallbackPayload, String> {
    let rx = {
        let mut guard = state
            .receiver
            .lock()
            .map_err(|err| format!("OAuth 상태 잠금 오류: {err}"))?;
        guard
            .take()
            .ok_or_else(|| "시작된 OAuth 루프백 세션이 없습니다.".to_string())?
    };

    tauri::async_runtime::spawn_blocking(move || match rx.recv_timeout(timeout) {
        Ok(result) => result,
        Err(_) => Err("GitHub 로그인 응답 대기 시간이 초과되었습니다.".to_string()),
    })
    .await
    .map_err(|err| format!("OAuth 루프백 태스크 오류: {err}"))?
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpStream;

    #[test]
    fn loopback_captures_ext_code_and_rejects_oversized_requests() {
        let state = OAuthLoopbackState::default();
        let port = start_loopback_inner(&state, Duration::from_secs(5)).unwrap();

        let mut stream = TcpStream::connect(("127.0.0.1", port)).unwrap();
        stream
            .write_all(b"GET /oauth/callback?ext_code=abc_123 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            .unwrap();
        let mut resp = String::new();
        stream.read_to_string(&mut resp).unwrap();
        assert!(resp.starts_with("HTTP/1.1 200 OK"));

        let payload = tauri::async_runtime::block_on(await_loopback_inner(&state, Duration::from_secs(5))).unwrap();
        assert_eq!(
            payload,
            DesktopOAuthCallbackPayload {
                code: Some("abc_123".to_string()),
                error: None,
            }
        );

        let port2 = start_loopback_inner(&state, Duration::from_secs(5)).unwrap();
        let mut stream2 = TcpStream::connect(("127.0.0.1", port2)).unwrap();
        let oversized = format!(
            "GET /oauth/callback?ext_code={} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            "a".repeat(MAX_HTTP_REQUEST_BYTES + 128)
        );
        stream2.write_all(oversized.as_bytes()).unwrap();
        let mut resp2 = String::new();
        let _ = stream2.read_to_string(&mut resp2);
        assert!(resp2.starts_with("HTTP/1.1 413"));

        let err = tauri::async_runtime::block_on(await_loopback_inner(&state, Duration::from_secs(5))).unwrap_err();
        assert!(err.contains("4KB"));
    }

    #[test]
    fn parse_loopback_handles_error_parameter_and_invalid_percent_bytes() {
        let parsed = parse_loopback_request("GET /oauth/callback?error=access_denied HTTP/1.1\r\n\r\n").unwrap();
        assert_eq!(
            parsed,
            DesktopOAuthCallbackPayload {
                code: None,
                error: Some("access_denied".to_string()),
            }
        );
        let non_ascii = parse_loopback_request("GET /oauth/callback?%가=1&ext_code=safe HTTP/1.1\r\n\r\n").unwrap();
        assert_eq!(non_ascii.code.as_deref(), Some("safe"));
        let bad_utf8 = percent_decode("%\u{FFFD}x");
        assert!(bad_utf8.starts_with('%'));
        assert!(parse_loopback_request("POST /oauth/callback?ext_code=x HTTP/1.1\r\n\r\n").is_err());
        assert!(parse_loopback_request("GET /oauth/callbackfoo?ext_code=x HTTP/1.1\r\n\r\n").is_err());
    }
}
