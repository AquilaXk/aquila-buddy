mod git;
mod oauth;
mod vault;
mod window_state;

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use tauri::menu::{MenuBuilder, MenuItemBuilder};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Manager, Url, WindowEvent};
use tauri_plugin_opener::OpenerExt;

const MAX_EXTERNAL_URL_BYTES: usize = 2_048;

struct AppDesktopState {
    quitting: AtomicBool,
    geometry: Mutex<Option<window_state::WindowGeometry>>,
    data_dir: Option<PathBuf>,
}

impl AppDesktopState {
    fn new(data_dir: Option<PathBuf>) -> Self {
        let saved = data_dir
            .as_deref()
            .and_then(window_state::WindowGeometry::load);
        Self {
            quitting: AtomicBool::new(false),
            geometry: Mutex::new(saved),
            data_dir,
        }
    }

    fn current_geometry(&self) -> Option<window_state::WindowGeometry> {
        *self.geometry.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn set_geometry(&self, next: window_state::WindowGeometry) {
        *self.geometry.lock().unwrap_or_else(|e| e.into_inner()) = Some(next);
    }

    fn set_maximized(&self, maximized: bool) {
        let mut guard = self.geometry.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(prev) = guard.as_mut() {
            prev.maximized = maximized;
        }
    }

    fn persist_geometry(&self) {
        if let (Some(dir), Some(geom)) = (&self.data_dir, self.current_geometry()) {
            let _ = geom.save(dir);
        }
    }

    fn mark_quitting(&self) {
        self.quitting.store(true, Ordering::SeqCst);
    }

    fn is_quitting(&self) -> bool {
        self.quitting.load(Ordering::SeqCst)
    }
}

fn request_app_quit(app: &tauri::AppHandle) {
    if let Some(state) = app.try_state::<AppDesktopState>() {
        state.mark_quitting();
        state.persist_geometry();
    }
    app.exit(0);
}

#[cfg(target_os = "macos")]
fn build_macos_menu(app: &tauri::AppHandle) -> tauri::Result<tauri::menu::Menu<tauri::Wry>> {
    use tauri::menu::{Menu, PredefinedMenuItem, SubmenuBuilder};

    let app_quit = MenuItemBuilder::with_id("app_quit", "Aquila Buddy 종료")
        .accelerator("CmdOrCtrl+Q")
        .build(app)?;
    let app_menu = SubmenuBuilder::new(app, "Aquila Buddy")
        .item(&PredefinedMenuItem::about(
            app,
            Some("Aquila Buddy 정보"),
            None,
        )?)
        .separator()
        .item(&PredefinedMenuItem::services(app, Some("서비스"))?)
        .separator()
        .item(&PredefinedMenuItem::hide(app, Some("Aquila Buddy 가리기"))?)
        .item(&PredefinedMenuItem::hide_others(app, Some("기타 가리기"))?)
        .item(&PredefinedMenuItem::show_all(app, Some("모두 보기"))?)
        .separator()
        .item(&app_quit)
        .build()?;
    let edit_menu = SubmenuBuilder::new(app, "편집")
        .item(&PredefinedMenuItem::undo(app, Some("실행 취소"))?)
        .item(&PredefinedMenuItem::redo(app, Some("다시 실행"))?)
        .separator()
        .item(&PredefinedMenuItem::cut(app, Some("오려두기"))?)
        .item(&PredefinedMenuItem::copy(app, Some("복사"))?)
        .item(&PredefinedMenuItem::paste(app, Some("붙여넣기"))?)
        .item(&PredefinedMenuItem::select_all(app, Some("모두 선택"))?)
        .build()?;
    let window_menu = SubmenuBuilder::new(app, "창")
        .item(&PredefinedMenuItem::minimize(app, Some("최소화"))?)
        .item(&PredefinedMenuItem::close_window(app, Some("창 닫기"))?)
        .build()?;
    let menu = Menu::new(app)?;
    menu.append(&app_menu)?;
    menu.append(&edit_menu)?;
    menu.append(&window_menu)?;
    Ok(menu)
}

fn restore_saved_window_geometry(app: &tauri::AppHandle, saved: window_state::WindowGeometry) {
    let Some(window) = app.get_webview_window("main") else {
        return;
    };
    let monitors: Vec<window_state::MonitorRect> = window
        .available_monitors()
        .unwrap_or_default()
        .into_iter()
        .map(|m| {
            let scale = m.scale_factor();
            let pos = m.position().to_logical::<f64>(scale);
            let size = m.size().to_logical::<f64>(scale);
            window_state::MonitorRect {
                x: pos.x.round() as i32,
                y: pos.y.round() as i32,
                width: size.width.round() as i32,
                height: size.height.round() as i32,
            }
        })
        .collect();
    let _ = window.set_size(tauri::LogicalSize::new(
        f64::from(saved.width),
        f64::from(saved.height),
    ));
    if let Some((x, y)) = saved.visible_origin(&monitors) {
        let _ = window.set_position(tauri::LogicalPosition::new(x, y));
    }
    if saved.maximized {
        let _ = window.maximize();
    }
}

fn capture_window_geometry(window: &tauri::Window) {
    let Some(state) = window.app_handle().try_state::<AppDesktopState>() else {
        return;
    };
    let maximized = window.is_maximized().unwrap_or(false);
    if maximized {
        state.set_maximized(true);
        return;
    }
    let scale = window.scale_factor().unwrap_or(1.0);
    if let (Ok(size), Ok(pos)) = (window.inner_size(), window.outer_position()) {
        let logical_size = size.to_logical::<f64>(scale);
        let logical_pos = pos.to_logical::<f64>(scale);
        if let Some(geom) = window_state::WindowGeometry::from_logical(
            (logical_size.width, logical_size.height),
            (logical_pos.x, logical_pos.y),
            false,
        ) {
            state.set_geometry(geom);
        }
    }
}

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
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(oauth::OAuthLoopbackState::default())
        .setup(|app| {
            let data_dir = app.path().app_local_data_dir().ok();
            let desktop_state = AppDesktopState::new(data_dir);
            let saved_geom = desktop_state.current_geometry();
            app.manage(desktop_state);
            if let Some(saved) = saved_geom {
                restore_saved_window_geometry(app.handle(), saved);
            }

            #[cfg(target_os = "macos")]
            {
                let macos_menu = build_macos_menu(app.handle())?;
                app.set_menu(macos_menu)?;
                app.on_menu_event(|app_handle, event| {
                    if event.id().as_ref() == "app_quit" {
                        request_app_quit(app_handle);
                    }
                });
            }

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
                    "quit" => request_app_quit(app),
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
        .on_window_event(|window, event| match event {
            WindowEvent::Moved(_) | WindowEvent::Resized(_) => capture_window_geometry(window),
            WindowEvent::CloseRequested { api, .. } => {
                if let Some(state) = window.app_handle().try_state::<AppDesktopState>() {
                    state.persist_geometry();
                    if cfg!(target_os = "macos") && !state.is_quitting() {
                        api.prevent_close();
                        let _ = window.hide();
                    }
                }
            }
            WindowEvent::Destroyed => {
                if let Some(state) = window.app_handle().try_state::<AppDesktopState>() {
                    state.persist_geometry();
                }
            }
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            open_external_url,
            vault::vault_get,
            vault::vault_set,
            vault::vault_delete,
            git::git_branch,
            git::git_status,
            git::git_diff,
            oauth::oauth_start_loopback,
            oauth::oauth_await_loopback,
        ])
        .build(tauri::generate_context!())
        .expect("Aquila Buddy desktop application failed to build");

    app.run(|app_handle, event| {
        if let tauri::RunEvent::Exit = event {
            if let Some(state) = app_handle.try_state::<AppDesktopState>() {
                state.persist_geometry();
            }
        }
        #[cfg(target_os = "macos")]
        if let tauri::RunEvent::Reopen {
            has_visible_windows: false,
            ..
        } = event
        {
            focus_main_window(app_handle);
        }
    });
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
