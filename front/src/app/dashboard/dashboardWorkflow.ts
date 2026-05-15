import type { DashboardOverview } from "./dashboardData";

export type DashboardWorkflowStatus = "done" | "ready" | "waiting" | "blocked";

export type DashboardWorkflowStep = {
  id: "profile" | "issues" | "guide" | "pr-draft";
  label: string;
  description: string;
  detail: string;
  href: string;
  status: DashboardWorkflowStatus;
};

export type DashboardNextAction = {
  label: string;
  description: string;
  href: string;
};

export type DashboardWorkflow = {
  steps: DashboardWorkflowStep[];
  nextAction: DashboardNextAction;
  completedCount: number;
};

export type DashboardActivityItem = {
  id: string;
  label: string;
  detail: string;
  href: string;
  createdAt: string | null;
};

export function buildDashboardWorkflow(overview: DashboardOverview): DashboardWorkflow {
  const hasProfile = Boolean(overview.profile?.profileReady);
  const hasIssues = overview.issueHistory.length > 0;
  const hasAnalyzedIssue = overview.issueHistory.some((issue) => issue.isAnalyzed);
  const hasDraft = overview.prDrafts.length > 0;

  const steps: DashboardWorkflowStep[] = [
    {
      id: "profile",
      label: "Profile",
      description: "GitHub 활동과 관심 기술을 추천 기준으로 준비합니다.",
      detail: overview.profile ? `@${overview.profile.login}` : "OAuth 로그인 후 분석 가능",
      href: "/profile",
      status: resolveStatus(overview.errors.profile, hasProfile, true)
    },
    {
      id: "issues",
      label: "Issue Candidates",
      description: "추천 이슈를 수집하고 저장된 후보를 확인합니다.",
      detail: hasIssues ? `${overview.issueHistory.length}개 후보 저장됨` : "저장된 후보 없음",
      href: "/issues",
      status: resolveStatus(overview.errors.issueHistory, hasIssues, hasProfile)
    },
    {
      id: "guide",
      label: "Issue Guide",
      description: "추천 이슈의 수정 가이드와 side effect를 확인합니다.",
      detail: hasAnalyzedIssue ? "분석 완료 이슈 있음" : "분석 완료 이슈 없음",
      href: "/issues",
      status: resolveStatus(overview.errors.issueHistory, hasAnalyzedIssue, hasIssues)
    },
    {
      id: "pr-draft",
      label: "PR Draft",
      description: "diff를 바탕으로 PR 제목과 본문 초안을 생성합니다.",
      detail: hasDraft ? `${overview.prDrafts.length}개 초안 저장됨` : "저장된 초안 없음",
      href: "/pr-draft",
      status: resolveStatus(overview.errors.prDrafts, hasDraft, hasAnalyzedIssue)
    }
  ];

  return {
    steps,
    nextAction: resolveNextAction(steps),
    completedCount: steps.filter((step) => step.status === "done").length
  };
}

export function buildDashboardActivities(overview: DashboardOverview): DashboardActivityItem[] {
  const draftActivities = overview.prDrafts.slice(0, 2).map((draft) => ({
    id: `draft-${draft.id}`,
    label: "PR 초안 생성",
    detail: `${draft.upstreamRepo} #${draft.githubIssueNumber}`,
    href: `/pr/${draft.id}`,
    createdAt: draft.createdAt
  }));

  const issueActivities = overview.issueHistory.slice(0, 2).map((issue) => ({
    id: `issue-${issue.id}`,
    label: issue.isAnalyzed ? "가이드 분석 완료" : "추천 이슈 저장",
    detail: `${issue.repoFullName} #${issue.issueNumber}`,
    href: `/issues/${issue.id}`,
    createdAt: null
  }));

  return [...draftActivities, ...issueActivities].slice(0, 4);
}

function resolveStatus(
  hasError: boolean,
  isDone: boolean,
  isReady: boolean
): DashboardWorkflowStatus {
  if (hasError) {
    return "blocked";
  }

  if (isDone) {
    return "done";
  }

  if (isReady) {
    return "ready";
  }

  return "waiting";
}

function resolveNextAction(steps: DashboardWorkflowStep[]): DashboardNextAction {
  const actionableStep = steps.find((step) => step.status === "ready" || step.status === "blocked")
    ?? steps.find((step) => step.status === "waiting")
    ?? steps.at(-1);

  if (!actionableStep) {
    return {
      label: "Dashboard",
      description: "작업 흐름을 확인합니다.",
      href: "/dashboard"
    };
  }

  if (actionableStep.status === "blocked") {
    return {
      label: "API 상태 확인",
      description: `${actionableStep.label} 단계에서 backend 응답을 확인해야 합니다.`,
      href: actionableStep.href
    };
  }

  return {
    label: actionableStep.label,
    description: actionableStep.description,
    href: actionableStep.href
  };
}
