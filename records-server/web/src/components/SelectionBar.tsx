import type { ReactNode } from "react";

export function SelectionBar({
  count,
  label,
  children,
}: {
  count: number;
  label: string;
  children: ReactNode;
}) {
  if (count <= 0) {
    return null;
  }

  return (
    <div className="selection-bar" role="status">
      <span>
        {count} {label}
      </span>
      <div className="selection-actions">{children}</div>
    </div>
  );
}
