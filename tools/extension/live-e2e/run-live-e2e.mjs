import { createGitHubFixtureServer } from "./fixture-server.mjs";

const server = await createGitHubFixtureServer();

try {
  const issueList = await fetch(`${server.origin}/AquilaXk/aquila-buddy/issues`);
  const html = await issueList.text();
  assertIncludes(html, "/AquilaXk/aquila-buddy/issues/42", "issue list fixture link");
  console.log("extension live e2e passed");
} finally {
  await server.close();
}

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}
