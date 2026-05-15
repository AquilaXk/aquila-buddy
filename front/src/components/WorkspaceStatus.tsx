"use client";

import { useEffect, useState } from "react";
import { getHealth } from "@/lib/api";
import {
  getWorkspaceHealthErrorMessage,
  toWorkspaceHealthViewModel,
  type WorkspaceHealthState
} from "./workspaceStatus";

export function WorkspaceStatus() {
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
  }, []);

  const viewModel = toWorkspaceHealthViewModel(healthState);

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
    </div>
  );
}
