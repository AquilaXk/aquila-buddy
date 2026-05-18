import { createServer } from "node:http";

export async function createGitHubFixtureServer() {
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? "/", "http://127.0.0.1");
    const body = renderFixture(url.pathname);
    response.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    });
    response.end(body);
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.off("error", reject);
      resolve();
    });
  });

  const address = server.address();
  if (!address || typeof address === "string") {
    throw new Error("fixture server address was not assigned");
  }

  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    }),
  };
}

function renderFixture(pathname) {
  if (pathname === "/AquilaXk/aquila-buddy/issues") {
    return githubPage(`
      <main id="repo-content-pjax-container">
        <div aria-label="Issues">
          <div class="js-issue-row">
            <a class="Link--primary" href="/AquilaXk/aquila-buddy/issues/42">Improve onboarding copy</a>
          </div>
          <div class="js-issue-row">
            <a class="Link--primary" href="/AquilaXk/aquila-buddy/issues/77">Refactor old settings panel</a>
          </div>
        </div>
      </main>
    `);
  }

  if (pathname === "/AquilaXk/aquila-buddy/issues/42") {
    return githubPage(`
      <main id="repo-content-pjax-container">
        <bdi class="js-issue-title markdown-title">Improve onboarding copy</bdi>
        <div class="edit-comment-hide">
          <task-lists>
            <p>Make first contribution flow easier to understand.</p>
          </task-lists>
        </div>
      </main>
    `);
  }

  if (pathname === "/AquilaXk/aquila-buddy/compare/main...AquilaXk:feature-branch") {
    return githubPage(`
      <main id="repo-content-pjax-container">
        <form id="new_pull_request">
          <input id="pull_request_title" name="pull_request[title]" value="">
          <textarea id="pull_request_body" name="pull_request[body]"></textarea>
        </form>
      </main>
    `);
  }

  return githubPage("<main>not found</main>");
}

function githubPage(content) {
  return `<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <title>GitHub fixture</title>
      </head>
      <body>
        ${content}
      </body>
    </html>`;
}
