import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const runner = await readFile(new URL("./run-pgvector-integration.sh", import.meta.url), "utf8");

test("pgvector integration runner documents compose and dry-run options", () => {
  assert.match(runner, /--compose/);
  assert.match(runner, /--dry-run/);
  assert.match(runner, /tools\/test\/compose\.pgvector-integration\.yaml/);
  assert.match(runner, /PGVECTOR_TEST_DB_PORT/);
});

test("pgvector integration runner can own docker compose lifecycle", () => {
  assert.match(runner, /docker compose/);
  assert.match(runner, /PGVECTOR_COMPOSE_PROJECT_NAME/);
  assert.match(runner, /-p "\$COMPOSE_PROJECT_NAME"/);
  assert.match(runner, /trap .*compose_down.*EXIT/);
  assert.match(runner, /compose_up/);
  assert.match(runner, /compose_down/);
});

test("pgvector integration runner exports compose database defaults", () => {
  assert.match(runner, /jdbc:postgresql:\/\/localhost:\$\{PGVECTOR_TEST_DB_PORT:-54329\}\/aquila_buddy/);
  assert.match(runner, /PGVECTOR_TEST_DB_USER:-aquila/);
  assert.match(runner, /PGVECTOR_TEST_DB_PASSWORD:-aquila/);
});

test("pgvector integration runner rejects invalid compose port before docker is used", () => {
  const result = spawnSync("bash", ["tools/test/run-pgvector-integration.sh", "--compose", "--dry-run"], {
    cwd: new URL("../..", import.meta.url),
    env: {
      ...process.env,
      PGVECTOR_TEST_DB_PORT: "not-a-port",
    },
    encoding: "utf8",
  });

  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}\n${result.stderr}`, /PGVECTOR_TEST_DB_PORT must be a positive integer/);
});
