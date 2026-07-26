import type { ReactNode } from "react";

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: "neutral" | "accent" | "danger" | "warning" | "muted";
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
