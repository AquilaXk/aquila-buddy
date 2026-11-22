const MAX_SECRET_BYTES: usize = 16_384;
const ALLOWED_KEY: &str = "auth_token";

#[cfg(not(test))]
const VAULT_SERVICE: &str = "site.aquilaxk.buddy";

pub fn validate_key(key: &str) -> Result<(), String> {
    if key != ALLOWED_KEY {
        return Err("허용되지 않은 키 이름입니다.".to_string());
    }
    Ok(())
}

pub fn validate_secret(value: &str) -> Result<(), String> {
    if value.is_empty() || value.len() > MAX_SECRET_BYTES {
        return Err("저장할 토큰 길이가 허용 범위를 벗어났습니다.".to_string());
    }
    Ok(())
}

#[cfg(not(test))]
#[tauri::command]
pub fn vault_get(key: String) -> Result<Option<String>, String> {
    validate_key(&key)?;
    let entry = keyring::Entry::new(VAULT_SERVICE, &key)
        .map_err(|err| format!("OS 키체인 초기화에 실패했습니다: {err}"))?;
    match entry.get_password() {
        Ok(secret) => Ok(Some(secret)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(err) => Err(format!("OS 키체인 조회에 실패했습니다: {err}")),
    }
}

#[cfg(not(test))]
#[tauri::command]
pub fn vault_set(key: String, value: String) -> Result<(), String> {
    validate_key(&key)?;
    validate_secret(&value)?;
    let entry = keyring::Entry::new(VAULT_SERVICE, &key)
        .map_err(|err| format!("OS 키체인 초기화에 실패했습니다: {err}"))?;
    entry
        .set_password(&value)
        .map_err(|err| format!("OS 키체인 저장에 실패했습니다: {err}"))
}

#[cfg(not(test))]
#[tauri::command]
pub fn vault_delete(key: String) -> Result<(), String> {
    validate_key(&key)?;
    let entry = keyring::Entry::new(VAULT_SERVICE, &key)
        .map_err(|err| format!("OS 키체인 초기화에 실패했습니다: {err}"))?;
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(err) => Err(format!("OS 키체인 삭제에 실패했습니다: {err}")),
    }
}

#[cfg(test)]
fn test_store() -> &'static std::sync::Mutex<std::collections::BTreeMap<String, String>> {
    use std::collections::BTreeMap;
    use std::sync::{Mutex, OnceLock};

    static STORE: OnceLock<Mutex<BTreeMap<String, String>>> = OnceLock::new();
    STORE.get_or_init(|| Mutex::new(BTreeMap::new()))
}

#[cfg(test)]
#[tauri::command]
pub fn vault_get(key: String) -> Result<Option<String>, String> {
    validate_key(&key)?;
    let guard = test_store().lock().map_err(|err| err.to_string())?;
    Ok(guard.get(&key).cloned())
}

#[cfg(test)]
#[tauri::command]
pub fn vault_set(key: String, value: String) -> Result<(), String> {
    validate_key(&key)?;
    validate_secret(&value)?;
    let mut guard = test_store().lock().map_err(|err| err.to_string())?;
    guard.insert(key, value);
    Ok(())
}

#[cfg(test)]
#[tauri::command]
pub fn vault_delete(key: String) -> Result<(), String> {
    validate_key(&key)?;
    let mut guard = test_store().lock().map_err(|err| err.to_string())?;
    guard.remove(&key);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn vault_roundtrip_and_bounds() {
        assert!(vault_get("".to_string()).is_err());
        assert!(vault_set("../bad".to_string(), "tok".to_string()).is_err());
        assert!(vault_set("other_key".to_string(), "tok".to_string()).is_err());
        assert!(vault_set("auth_token".to_string(), "".to_string()).is_err());
        assert!(vault_set("auth_token".to_string(), "x".repeat(MAX_SECRET_BYTES + 1)).is_err());

        vault_delete("auth_token".to_string()).unwrap();
        assert_eq!(vault_get("auth_token".to_string()).unwrap(), None);

        vault_set("auth_token".to_string(), "jwt.payload.sig".to_string()).unwrap();
        assert_eq!(
            vault_get("auth_token".to_string()).unwrap(),
            Some("jwt.payload.sig".to_string())
        );

        vault_delete("auth_token".to_string()).unwrap();
        assert_eq!(vault_get("auth_token".to_string()).unwrap(), None);
    }
}
