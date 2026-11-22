// Prohibited content shared by the repository hygiene guard (tracked files) and the commit message
// guard (pushed commits and PR titles). Kept Base64-encoded so the guard source stays clean.

export const PROHIBITED_CONTENT_B64 = [
  "QUdFTlRTLm1k",                 // local-only doc ref 1
  "Q0xBVURFLm1k",                 // local-only doc ref 2
  "R0VNSU5JLm1k",                 // local-only doc ref 3
  "Q0xBVURFLmxvY2FsLm1k",         // local-only doc ref 4
  "Q1VSU09SLm1k",                 // local-only doc ref 5
  "Q09QSUxPVC5tZA==",             // local-only doc ref 6
  "QW50aWdyYXZpdHk=",             // tool name 1
  "Q2xhdWRlIENvZGU=",             // tool name 2
  "Q2xhdWRlLVNlc3Npb246",         // session attribution trailer
  "Q28tQXV0aG9yZWQtQnk6",         // attribution header
  "R2VuZXJhdGVkIHdpdGggQ2xhdWRl", // generator trace 1
  "R2VuZXJhdGVkIGJ5IENvZGV4",     // generator trace 2
  "bm9yZXBseUBhbnRocm9waWMuY29t", // attribution email
  "Q29kZVJhYmJpdA==",             // external bot name
  "XC5jb2RlcmFiYml0",             // external bot config
  "XGJjb2RleFxi",                 // prohibited agent CLI token
  "XGJzdWJhZ2VudFxi",             // internal orchestration term
  "YXF1aWxhLXJldmlldw==",         // internal review skill name
  "U2lnbmF0dXJlIExhdmVuZGVy",     // retired design token label
  "ZWFzeUNvbnZlcnQ=",             // cross-repo reference name
];

export const PROHIBITED_PATTERNS = PROHIBITED_CONTENT_B64.map(
  (b64) => new RegExp(Buffer.from(b64, "base64").toString("utf8"), "i")
);

export function prohibitedPatternsIn(_rel, content) {
  return PROHIBITED_PATTERNS.filter((pattern) => pattern.test(content));
}
