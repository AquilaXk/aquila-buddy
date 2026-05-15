import { WorkspaceStatus } from "./WorkspaceStatus";

type ProtectedLayoutProps = {
  title: string;
  children: React.ReactNode;
};

export function ProtectedLayout({ title, children }: ProtectedLayoutProps) {
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-moss">Workspace</p>
          <h1 className="mt-1 text-2xl font-bold text-ink">{title}</h1>
        </div>
        <WorkspaceStatus />
      </div>
      {children}
    </div>
  );
}
