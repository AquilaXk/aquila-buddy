import type { HealthResponse } from "@/lib/api";

export type WorkspaceHealthState =
  | {
      status: "checking";
      checkedAt: Date | null;
    }
  | {
      status: "online";
      health: HealthResponse;
      checkedAt: Date;
    }
  | {
      status: "offline";
      message: string;
      checkedAt: Date;
    };

export type WorkspaceHealthViewModel = {
  label: string;
  detail: string;
  badgeClassName: string;
  dotClassName: string;
  checkedAtLabel: string | null;
};

export function toWorkspaceHealthViewModel(state: WorkspaceHealthState): WorkspaceHealthViewModel {
  if (state.status === "checking") {
    return {
      label: "API 확인 중",
      detail: "backend health 확인",
      badgeClassName: "border-line bg-white text-neutral-700",
      dotClassName: "bg-neutral-400",
      checkedAtLabel: state.checkedAt ? formatCheckedAt(state.checkedAt) : null
    };
  }

  if (state.status === "online") {
    return {
      label: "API Online",
      detail: `${state.health.service} ${state.health.status}`,
      badgeClassName: "border-green-200 bg-green-50 text-green-800",
      dotClassName: "bg-green-600",
      checkedAtLabel: formatCheckedAt(state.checkedAt)
    };
  }

  return {
    label: "API Offline",
    detail: state.message,
    badgeClassName: "border-red-200 bg-red-50 text-red-700",
    dotClassName: "bg-red-500",
    checkedAtLabel: formatCheckedAt(state.checkedAt)
  };
}

export function getWorkspaceHealthErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  return "backend health 응답 없음";
}

function formatCheckedAt(value: Date): string {
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZone: "Asia/Seoul"
  }).format(value);
}
