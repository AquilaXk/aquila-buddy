const MAX_GITHUB_COMPARE_URL_LENGTH = 7_600;
const TRUNCATED_NOTICE = "\n\n...(본문이 URL 길이 제한으로 일부 생략됨 — Aquila Buddy에서 전체 본문을 복사해 붙여넣으세요)";
const SAFE_REPO_SLUG_RE = /^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/;

function validateSafeRepoSlug(upstreamRepo: string): string {
  const normalizedRepo = upstreamRepo.trim();
  if (!normalizedRepo || normalizedRepo.length > 255 || !SAFE_REPO_SLUG_RE.test(normalizedRepo)) {
    throw new Error("유효하지 않은 GitHub 저장소 이름입니다.");
  }
  const slashIndex = normalizedRepo.indexOf("/");
  const owner = normalizedRepo.slice(0, slashIndex);
  const repo = normalizedRepo.slice(slashIndex + 1);
  if (owner === "." || owner === ".." || repo === "." || repo === "..") {
    throw new Error("유효하지 않은 GitHub 저장소 이름입니다.");
  }
  return normalizedRepo;
}

function encodeBranchReference(branch: string, preserveForkOwner: boolean): string {
  const trimmed = branch.trim();
  if (!trimmed) {
    return "main";
  }

  if (preserveForkOwner) {
    const colonIdx = trimmed.indexOf(":");
    if (colonIdx > 0 && colonIdx < trimmed.length - 1) {
      const owner = trimmed.slice(0, colonIdx);
      const branchName = trimmed.slice(colonIdx + 1);
      return `${encodeURIComponent(owner)}:${encodeURIComponent(branchName)}`;
    }
  }

  return encodeURIComponent(trimmed);
}

/**
 * GitHub Compare Quick-Pull URL을 7,600자 상한 이내로 안전하게 생성합니다.
 * headBranch의 owner:branch 포크 구분자(:)는 보존하고 URL 초과 시 본문만 안전 절단합니다.
 */
export function buildSafeGitHubCompareUrl(params: {
  upstreamRepo: string;
  baseBranch: string;
  headBranch: string;
  title: string;
  body: string;
}): { url: string; bodyTruncated: boolean } {
  const normalizedRepo = validateSafeRepoSlug(params.upstreamRepo);
  const encodedBase = encodeBranchReference(params.baseBranch, false);
  const encodedHead = encodeBranchReference(params.headBranch, true);
  const prefix = `https://github.com/${normalizedRepo}/compare/${encodedBase}...${encodedHead}?quick_pull=1&expand=1`;

  const search = new URLSearchParams({
    title: params.title,
    body: params.body,
  });
  const fullUrl = `${prefix}&${search.toString()}`;
  if (fullUrl.length <= MAX_GITHUB_COMPARE_URL_LENGTH) {
    return { url: fullUrl, bodyTruncated: false };
  }

  const normalizedBody = params.body;
  let low = 0;
  let high = normalizedBody.length;
  let bestBody = "";

  while (low <= high) {
    const mid = (low + high) >>> 1;
    const candidateBody = `${normalizedBody.slice(0, mid)}${TRUNCATED_NOTICE}`;
    const candidateSearch = new URLSearchParams({
      title: params.title,
      body: candidateBody,
    });
    const candidateUrl = `${prefix}&${candidateSearch.toString()}`;
    if (candidateUrl.length <= MAX_GITHUB_COMPARE_URL_LENGTH) {
      bestBody = candidateBody;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  const truncatedSearch = new URLSearchParams({
    title: params.title,
    body: bestBody,
  });

  return {
    url: `${prefix}&${truncatedSearch.toString()}`,
    bodyTruncated: true,
  };
}
