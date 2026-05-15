"use client";

import { useEffect, useState } from "react";
import { getHealth } from "@/lib/api";
import {
  getWorkspaceHealthErrorMessage,
  toWorkspaceHealthViewModel,
  type WorkspaceHealthState
} from "./workspaceHealthStatus";

export function WorkspaceStatus() {
  const [refreshNonce, setRefreshNonce] = useState(0);
  const [healthState, setHealthState] = useState<WorkspaceHealthState>({
    status: "checking",
    checkedAt: null
  });

  useEffect(() => {
    let mounted = true;

    async function checkHealth() {
      try {
        const health = await getHealth();
        if (mounted) {
          setHealthState({
            status: "online",
            health,
            checkedAt: new Date()
          });
        }
      } catch (error) {
        if (mounted) {
          setHealthState({
            status: "offline",
            message: getWorkspaceHealthErrorMessage(error),
            checkedAt: new Date()
          });
        }
      }
    }

    checkHealth();

    return () => {
      mounted = false;
    };
  }, [refreshNonce]);

  const viewModel = toWorkspaceHealthViewModel(healthState);
  const isChecking = healthState.status === "checking";

  function refreshHealth() {
    setHealthState((currentState) => ({
      status: "checking",
      checkedAt: currentState.checkedAt
    }));
    setRefreshNonce((currentNonce) => currentNonce + 1);
  }

  return (
    <div
      aria-live="polite"
      className={`rounded-md border px-3 py-2 text-sm ${viewModel.badgeClassName}`}
    >
      <div className="flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${viewModel.dotClassName}`} />
        <span className="font-semibold">{viewModel.label}</span>
      </div>
      <p className="mt-1 max-w-56 truncate text-xs font-medium opacity-80">{viewModel.detail}</p>
      <div className="mt-2 flex items-center justify-between gap-3 text-xs">
        <span className="font-medium opacity-75">
          {viewModel.checkedAtLabel ? `마지막 확인 ${viewModel.checkedAtLabel}` : "아직 확인 전"}
        </span>
        <button
          className="focus-ring rounded-md border border-current px-2 py-1 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          disabled={isChecking}
          onClick={refreshHealth}
          type="button"
        >
          새로고침
        </button>
      </div>
    </div>
  );
}
