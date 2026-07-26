import { useMemo } from "react";
import { getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";
import { Badge } from "../components/Badge";
import { DataTable } from "../components/DataTable";
import { actionLabel, actionTone, auditChanges, auditSubject } from "../lib/audit";
import { formatDateTime } from "../lib/format";
import type { RecordAuditLog } from "../types";

export function AuditLogView({
  logs,
  loading,
  nextBeforeId,
  onLoadMore,
}: {
  logs: RecordAuditLog[];
  loading: boolean;
  nextBeforeId: number | null;
  onLoadMore: () => void;
}) {
  const columns = useMemo<ColumnDef<RecordAuditLog>[]>(
    () => [
      {
        id: "time",
        header: "Time",
        cell: ({ row }) => formatDateTime(row.original.createdAt),
      },
      {
        id: "object",
        header: "Object",
        cell: ({ row }) => auditSubject(row.original),
      },
      {
        id: "action",
        header: "Action",
        cell: ({ row }) => <Badge tone={actionTone(row.original.action)}>{actionLabel(row.original.action)}</Badge>,
      },
      {
        id: "changes",
        header: "Changes",
        cell: ({ row }) => <span className="audit-change">{auditChanges(row.original)}</span>,
      },
    ],
    [],
  );

  const table = useReactTable({
    data: logs,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <section className="view-panel">
      <div className="toolbar">
        <p className="result-count" aria-live="polite">
          {loading ? "Loading…" : `${logs.length} operations`}
        </p>
      </div>

      <DataTable table={table} loading={loading} empty={loading ? "Loading logs…" : "No logged operations."} />

      {nextBeforeId !== null && (
        <div className="toolbar footer-toolbar">
          <button type="button" className="secondary" onClick={onLoadMore}>
            Load older
          </button>
        </div>
      )}
    </section>
  );
}
