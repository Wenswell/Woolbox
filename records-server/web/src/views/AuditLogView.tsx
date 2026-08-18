import { useMemo, useState } from "react";
import { ChevronDown, Search } from "lucide-react";
import { getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";
import { Badge } from "../components/Badge";
import { DataTable } from "../components/DataTable";
import { actionLabel, actionTone, auditChanges, auditSubject } from "../lib/audit";
import { formatDateTime } from "../lib/format";
import type { RecordAuditLog } from "../types";

type EntityFilter = "" | RecordAuditLog["entityType"];

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
  const [entity, setEntity] = useState<EntityFilter>("");
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return logs.filter((log) => {
      if (entity && log.entityType !== entity) return false;
      if (!normalized) return true;
      return `${auditSubject(log)} ${actionLabel(log.action)} ${auditChanges(log)}`.toLocaleLowerCase().includes(normalized);
    });
  }, [entity, logs, query]);

  const columns = useMemo<ColumnDef<RecordAuditLog>[]>(() => [
    { id: "time", header: "Time", cell: ({ row }) => <span className="time-cell">{formatDateTime(row.original.createdAt)}</span> },
    { id: "object", header: "Object", cell: ({ row }) => <div className="primary-cell"><strong>{auditSubject(row.original)}</strong><span>{row.original.entityType.toLocaleLowerCase()} #{row.original.entityId}</span></div> },
    { id: "action", header: "Action", cell: ({ row }) => <Badge tone={actionTone(row.original.action)}>{actionLabel(row.original.action)}</Badge> },
    { id: "changes", header: "Changes", cell: ({ row }) => <span className="audit-change">{auditChanges(row.original)}</span> },
  ], []);
  const table = useReactTable({ data: filtered, columns, getCoreRowModel: getCoreRowModel() });

  return (
    <section className="view-stack">
      <div className="filter-bar">
        <label className="search-field"><Search size={16} /><input aria-label="Search activity" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search objects, actions and changes" /></label>
        <label>Object<select aria-label="Activity object" value={entity} onChange={(event) => setEntity(event.target.value as EntityFilter)}><option value="">All objects</option><option value="DAILY_PLAN">Daily plans</option><option value="TASK">Tasks</option><option value="STEP">Subtasks</option><option value="ITEM">Record items</option><option value="ENTRY">Entries</option></select></label>
        <span className="result-count">{loading ? "Loading…" : `${filtered.length} operations`}</span>
      </div>
      <DataTable table={table} loading={loading} empty={loading ? "Loading activity…" : "No operations match this view."} />
      {nextBeforeId !== null && <div className="load-more"><button type="button" className="secondary" onClick={onLoadMore} disabled={loading}><ChevronDown size={16} />Load older activity</button></div>}
    </section>
  );
}
