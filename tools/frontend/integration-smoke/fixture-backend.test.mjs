import assert from "node:assert/strict";
import { createServer } from "node:net";
import test from "node:test";
import { createFrontendFixtureBackend } from "./fixture-backend.mjs";

test("fixture backend serves core success envelopes", async () => {
  const backend = await createFrontendFixtureBackend();

  try {
    const profile = await getJson(`${backend.origin}/api/v1/users/me`);
    assert.equal(profile.success, true);
    assert.equal(profile.status, "success");
    assert.equal(profile.data.login, "aquila-smoke");
    assert.deepEqual(profile.data.primaryLanguages, ["Java", "TypeScript"]);

    const issues = await getJson(`${backend.origin}/api/v1/issues/recommend/history`);
    assert.equal(issues.success, true);
    assert.equal(issues.data[0].id, "issue-1");
    assert.equal(issues.data[0].analysisResultId, "analysis-1");

    const draft = await getJson(`${backend.origin}/api/v1/pr/draft-1`);
    assert.equal(draft.success, true);
    assert.equal(draft.data.sourceIssueId, "issue-1");
    assert.match(draft.data.body, /close #261/);
  } finally {
    await backend.close();
  }
});

test("fixture backend can serve API failure envelopes", async () => {
  const backend = await createFrontendFixtureBackend({ profileFailure: true });

  try {
    const response = await fetch(`${backend.origin}/api/v1/users/me`);
    const payload = await response.json();

    assert.equal(response.status, 200);
    assert.equal(payload.success, false);
    assert.equal(payload.status, "fail");
    assert.equal(payload.data, null);
    assert.equal(payload.message, "fixture profile failure");
  } finally {
    await backend.close();
  }
});

test("fixture backend can bind a configured port", async () => {
  const port = await getAvailablePort();
  const backend = await createFrontendFixtureBackend({ port });

  try {
    assert.equal(backend.origin, `http://127.0.0.1:${port}`);
    const health = await getJson(`${backend.origin}/api/v1/health`);
    assert.equal(health.data.status, "UP");
  } finally {
    await backend.close();
  }
});

async function getJson(url) {
  const response = await fetch(url);
  assert.equal(response.status, 200);
  return response.json();
}

async function getAvailablePort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  assert.ok(address && typeof address !== "string");
  return address.port;
}
