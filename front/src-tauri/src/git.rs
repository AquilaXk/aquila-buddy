use serde::Serialize;
use std::io::Read;
use std::path::{Component, Path, PathBuf};
use std::process::{Command, Stdio};

const MAX_STATUS_ENTRIES: usize = 2_000;
const MAX_STATUS_BYTES: usize = 262_144;
const MAX_DIFF_BYTES: usize = 65_536;
const MAX_REL_PATH_BYTES: usize = 1_024;

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct LocalGitStatusEntry {
    pub path: String,
    pub status: String,
}

fn validate_repo_dir(repo_dir: &str) -> Result<PathBuf, String> {
    if repo_dir.is_empty() || repo_dir.len() > 2_048 || repo_dir.contains('\0') {
        return Err("유효하지 않은 저장소 경로입니다.".to_string());
    }
    let path = PathBuf::from(repo_dir);
    if !path.is_dir() || !path.join(".git").exists() {
        return Err("Git 저장소 루트 디렉터리가 아닙니다.".to_string());
    }
    Ok(path)
}

fn validate_rel_file_path(file_path: &str) -> Result<&str, String> {
    if file_path.is_empty()
        || file_path.len() > MAX_REL_PATH_BYTES
        || file_path.contains('\0')
        || file_path.starts_with('-')
    {
        return Err("허용되지 않은 파일 경로입니다.".to_string());
    }
    let path = Path::new(file_path);
    if path.is_absolute() {
        return Err("상대 경로만 조회할 수 있습니다.".to_string());
    }
    for component in path.components() {
        if matches!(component, Component::ParentDir | Component::RootDir | Component::Prefix(_)) {
            return Err("상위 디렉터리 참조가 포함된 경로는 허용되지 않습니다.".to_string());
        }
    }
    Ok(file_path)
}

fn run_git_bounded(
    repo: &Path,
    args: &[&str],
    max_bytes: usize,
    limit_message: &str,
    failure_message: &str,
) -> Result<Vec<u8>, String> {
    let mut child = Command::new("git")
        .arg("-c")
        .arg("core.fsmonitor=false")
        .arg("--literal-pathspecs")
        .arg("-C")
        .arg(repo)
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|err| format!("git 실행에 실패했습니다: {err}"))?;

    let mut stdout = child
        .stdout
        .take()
        .ok_or_else(|| "git 출력 파이프를 열 수 없습니다.".to_string())?;

    let mut buffer = Vec::with_capacity(max_bytes.min(8_192));
    let mut chunk = [0u8; 4_096];
    let mut total_read = 0usize;

    loop {
        let to_read = chunk.len().min(max_bytes + 1 - total_read);
        if to_read == 0 {
            let _ = child.kill();
            let _ = child.wait();
            return Err(limit_message.to_string());
        }
        match stdout.read(&mut chunk[..to_read]) {
            Ok(0) => break,
            Ok(n) => {
                total_read += n;
                if total_read > max_bytes {
                    let _ = child.kill();
                    let _ = child.wait();
                    return Err(limit_message.to_string());
                }
                buffer.extend_from_slice(&chunk[..n]);
            }
            Err(err) => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(format!("git 출력 읽기에 실패했습니다: {err}"));
            }
        }
    }

    let status = child
        .wait()
        .map_err(|err| format!("git 종료 대기 중 오류가 발생했습니다: {err}"))?;
    if !status.success() {
        return Err(failure_message.to_string());
    }

    Ok(buffer)
}

#[tauri::command]
pub fn git_status(repo_dir: String) -> Result<Vec<LocalGitStatusEntry>, String> {
    let repo = validate_repo_dir(&repo_dir)?;
    let stdout = run_git_bounded(
        &repo,
        &["status", "--porcelain=v1", "-z"],
        MAX_STATUS_BYTES,
        "변경 상태 출력 크기가 상한(256KB)을 초과했습니다.",
        "git status 실행이 실패했습니다.",
    )?;

    let mut entries = Vec::new();
    let mut parts = stdout.split(|b| *b == 0);
    while let Some(raw_record) = parts.next() {
        if raw_record.len() < 4 || raw_record[2] != b' ' {
            continue;
        }
        let status = String::from_utf8_lossy(&raw_record[..2]).trim().to_string();
        let path = String::from_utf8_lossy(&raw_record[3..]).into_owned();
        if raw_record[0] == b'R' || raw_record[0] == b'C' {
            let _ = parts.next();
        }
        entries.push(LocalGitStatusEntry { path, status });
        if entries.len() > MAX_STATUS_ENTRIES {
            return Err("변경 파일 목록 상한(2,000건)을 초과했습니다.".to_string());
        }
    }

    Ok(entries)
}

#[tauri::command]
pub fn git_diff(repo_dir: String, file_path: Option<String>) -> Result<String, String> {
    let repo = validate_repo_dir(&repo_dir)?;
    let mut args = vec!["diff", "--no-ext-diff", "--no-textconv", "--no-color", "--"];

    let validated_owned;
    if let Some(ref rel_path) = file_path {
        validated_owned = validate_rel_file_path(rel_path)?.to_string();
        args.push(&validated_owned);
    }

    let stdout = run_git_bounded(
        &repo,
        &args,
        MAX_DIFF_BYTES,
        "diff 크기가 64KB 상한을 초과했습니다.",
        "git diff 실행이 실패했습니다.",
    )?;

    Ok(String::from_utf8_lossy(&stdout).into_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn create_temp_repo(label: &str) -> PathBuf {
        let nonce = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let dir = std::env::temp_dir().join(format!("aquila-buddy-git-{label}-{nonce}"));
        fs::create_dir_all(&dir).unwrap();
        assert!(Command::new("git").args(["init"]).current_dir(&dir).status().unwrap().success());
        assert!(Command::new("git")
            .args(["config", "user.email", "ci@example.com"])
            .current_dir(&dir)
            .status()
            .unwrap()
            .success());
        assert!(Command::new("git")
            .args(["config", "user.name", "CI"])
            .current_dir(&dir)
            .status()
            .unwrap()
            .success());
        fs::write(dir.join("README.md"), "hello\n").unwrap();
        assert!(Command::new("git").args(["add", "README.md"]).current_dir(&dir).status().unwrap().success());
        assert!(Command::new("git").args(["commit", "-m", "init"]).current_dir(&dir).status().unwrap().success());
        dir
    }

    #[test]
    fn status_diff_bounds_and_path_traversal_guards() {
        let repo = create_temp_repo("guards");
        fs::write(repo.join("README.md"), "hello\nworld\n").unwrap();

        let repo_str = repo.to_string_lossy().into_owned();
        let status = git_status(repo_str.clone()).unwrap();
        assert_eq!(
            status,
            vec![LocalGitStatusEntry {
                path: "README.md".to_string(),
                status: "M".to_string(),
            }]
        );

        let diff = git_diff(repo_str.clone(), Some("README.md".to_string())).unwrap();
        assert!(diff.contains("+world"));

        assert!(git_diff(repo_str.clone(), Some("--output=/tmp/pwn".to_string())).is_err());
        assert!(git_diff(repo_str.clone(), Some("../etc/passwd".to_string())).is_err());
        assert!(git_diff(repo_str.clone(), Some("/etc/passwd".to_string())).is_err());
        assert!(git_status("/nonexistent/aquila-buddy-dir".to_string()).is_err());

        fs::write(repo.join("README.md"), "x".repeat(MAX_DIFF_BYTES + 2_048)).unwrap();
        let err = git_diff(repo_str, Some("README.md".to_string())).unwrap_err();
        assert!(err.contains("64KB"));

        let _ = fs::remove_dir_all(&repo);
    }
}
