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
        <p className="rounded-md border border-line px-3 py-2 text-sm text-neutral-700">
          API 연결 준비 중
        </p>
      </div>
      {children}
    </div>
  );
}
