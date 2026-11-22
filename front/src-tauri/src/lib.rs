mod git;
mod oauth;
mod vault;

use tauri::menu::{MenuBuilder, MenuItemBuilder};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Manager, Url};
use tauri_plugin_opener::OpenerExt;

const MAX_EXTERNAL_URL_BYTES: usize = 2_048;

pub fn validate_external_url(raw_url: &str) -> Result<(), String> {
    if raw_url.is_empty()
        || raw_url.len() > MAX_EXTERNAL_URL_BYTES
        || raw_url
            .bytes()
            .any(|b| b.is_ascii_control() || b.is_ascii_whitespace())
    {
        return Err("허용되지 않은 URL 형식입니다.".to_string());
    }

    let parsed = Url::parse(raw_url).map_err(|_| "외부 URL 파싱에 실패했습니다.".to_string())?;
    if !parsed.username().is_empty() || parsed.password().is_some() {
        return Err("인증 정보가 포함된 URL은 열 수 없습니다.".to_string());
    }

    let Some(host) = parsed.host_str() else {
        return Err("호스트가 없는 URL은 열 수 없습니다.".to_string());
    };

    match parsed.scheme() {
        "https" if !host.is_empty() => Ok(()),
        "http" if (host == "localhost" || host == "127.0.0.1") && parsed.port().is_some() => Ok(()),
        _ => Err("외부 브라우저는 HTTPS 또는 로컬 루프백 주소만 열 수 있습니다.".to_string()),
    }
}

#[tauri::command]
fn open_external_url(app: tauri::AppHandle, url: String) -> Result<(), String> {
    validate_external_url(&url)?;
    app.opener()
        .open_url(&url, None::<&str>)
        .map_err(|err| format!("외부 브라우저 실행에 실패했습니다: {err}"))
}

fn focus_main_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(oauth::OAuthLoopbackState::default())
        .setup(|app| {
            let show_item = MenuItemBuilder::with_id("open", "Aquila Buddy 열기").build(app)?;
            let quit_item = MenuItemBuilder::with_id("quit", "종료").build(app)?;
            let menu = MenuBuilder::new(app)
                .items(&[&show_item, &quit_item])
                .build()?;

            let icon = app
                .default_window_icon()
                .cloned()
                .unwrap_or_else(|| tauri::image::Image::new(&[0, 0, 0, 0], 1, 1));

            let _tray = TrayIconBuilder::new()
                .icon(icon)
                .menu(&menu)
                .tooltip("Aquila Buddy")
                .on_menu_event(|app, event| match event.id().as_ref() {
                    "open" => focus_main_window(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        focus_main_window(tray.app_handle());
                    }
                })
                .build(app)?;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            open_external_url,
            vault::vault_get,
            vault::vault_set,
            vault::vault_delete,
            git::git_status,
            git::git_diff,
            oauth::oauth_start_loopback,
            oauth::oauth_await_loopback,
        ])
        .run(tauri::generate_context!())
        .expect("Aquila Buddy desktop application failed to run");
}

#[cfg(test)]
mod tests {
    use super::validate_external_url;

    #[test]
    fn allows_https_and_local_loopback_only() {
        assert!(
            validate_external_url("https://buddy.aquilaxk.site/oauth2/authorization/github")
                .is_ok()
        );
        assert!(validate_external_url("http://localhost:8080/oauth2/authorization/github").is_ok());
        assert!(validate_external_url("http://127.0.0.1:8080/api").is_ok());

        assert!(validate_external_url("javascript:alert(1)").is_err());
        assert!(validate_external_url("file:///etc/passwd").is_err());
        assert!(validate_external_url("http://evil.example.com/phish").is_err());
        assert!(validate_external_url("http://localhost:80@evil.com/phish").is_err());
        assert!(validate_external_url("http://127.0.0.1:pass@evil.com/").is_err());
        assert!(validate_external_url("http://localhost:@evil.com/").is_err());
    }
}
