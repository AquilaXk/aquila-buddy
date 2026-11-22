## Aquila Buddy v0.1.0

웹([buddy.aquilaxk.site](https://buddy.aquilaxk.site)), 크로스 플랫폼 데스크톱 앱(`macOS` · `Windows` · `Linux`), Chrome 확장 프로그램(`Manifest V3`)을 한 번에 제공하는 초기 릴리스입니다.

### 포함 아티팩트

| 대상 | 파일 | 설명 |
| :--- | :--- | :--- |
| **macOS** | `AquilaBuddy-macos-universal.dmg` | Apple Silicon · Intel 통합 유니버설 디스크 이미지 |
| **Windows** | `AquilaBuddy-windows-x64-setup.exe` | Windows 10/11 x64 NSIS 사용자 설치 프로그램 |
| **Linux (x64)** | `AquilaBuddy-linux-x64.AppImage` / `.deb` / `.rpm` | portable 실행 이미지 및 Ubuntu·Fedora 계열 패키지 |
| **Linux (ARM64)** | `AquilaBuddy-linux-arm64.deb` | Ubuntu ARM64 패키지 |
| **Chrome 확장** | `AquilaBuddy-chrome-extension.zip` | GitHub 이슈·비교·PR 화면 도킹 확장 프로그램 (`Manifest V3`) |
| **무결성 검증** | `SHA256SUMS.txt` | 전체 배포 파일 SHA-256 해시 목록 |

### 주요 변경 사항

* **크로스 플랫폼 데스크톱 앱 (`Tauri v2`)**: macOS 키체인 · Windows 자격 증명 관리자 · Linux Secret Service 토큰 저장, RFC 8252 루프백 PKCE `S256` GitHub 로그인, 시스템 트레이 및 로컬 Git 저장소 조회를 지원합니다.
* **무의존성 UI · 백엔드 클린룸 구현**: 외부 UI/유틸/웹폰트/JWT 패키지 없이 OS 시스템 네이티브 폰트 스택, React 19 UI 프리미티브, RFC 7519 `HS256` JWT 공급자, Charikar `SimHash64`, Bitap 퍼지 탐색 및 Unified Diff 상태 머신을 내장했습니다.
* **Chrome 확장 프로그램 (`Manifest V3`)**: PKCE `S256` 인증, 이슈 추천 점수 배지 주입, 드래그 리사이즈 도킹 사이드바 및 PR 폼 자동 기입을 제공합니다.

### 설치 안내 요약

* **macOS**: 첫 실행 시 손상된 앱 또는 미확인 개발자 경고가 뜨면 `xattr -cr "/Applications/Aquila Buddy.app"` 실행 후 여세요.
* **Windows**: SmartScreen 화면에서 `추가 정보` → `실행`을 선택하세요.
* **Linux**: `chmod +x AquilaBuddy-linux-x64.AppImage` 후 실행하거나 `.deb`/`.rpm` 패키지로 설치하세요.
* **Chrome 확장**: 압축을 푼 폴더를 `chrome://extensions`에서 `개발자 모드` → `압축해제된 확장 프로그램을 로드합니다`로 불러오세요.
