# Security Policy

## Supported Versions

Security updates are applied to the latest release on the `main` branch and deployed with official releases.

| Version | Supported |
| :--- | :--- |
| `0.1.x` (latest `main`) | Yes |

## Security Architecture & Invariants

Aquila Buddy enforces **Fail-Closed** security boundaries across authentication, database isolation, GitHub reference validation, and external AI/API calls:

1. **OAuth 2.0, PKCE & Session Isolation**:
   - Browser extension authentication enforces PKCE (`S256`) one-time authorization code (`ext_code`) exchange over HTTPS/loopback origins, and web sessions store refresh credentials in `HttpOnly` cookies with open-redirect blocking on `next`/`state`.
2. **Row-Level Data Isolation**:
   - PostgreSQL runtime tables enforce `FORCE ROW LEVEL SECURITY` bound to `app.current_user_id` so recommendation history, bookmarks, and PR drafts stay isolated per authenticated user.
3. **Reference Validation & Secret Scanning**:
   - `GitHubReferenceValidator` validates repository slugs, branch names, and file paths in a single zero-allocation byte LUT pass rejecting control bytes and `..` traversal.
   - PR draft generation scans diffs in a single pass for leaked credential prefixes (`ghp_`, `github_pat_`, `sk-`, `AKIA`, PEM private keys) before submission readiness passes.
4. **Zero Silent Fallbacks**:
   - Failures from GitHub APIs, AI providers, or PostgreSQL return explicit typed errors without fabricated issue lists, scores, or guides.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately via GitHub Security Advisories on `AquilaXk/aquila-buddy` rather than opening a public issue.
