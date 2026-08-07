import { useMemo, useState } from "react";
import { Archive, ArchiveRestore, Edit3, Plus, Search, Trash2 } from "lucide-react";
import { getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { SelectionBar } from "../components/SelectionBar";
import { message } from "../lib/format";
import type { RecordItem, RecordMode } from "../types";
import { ItemForm } from "./ItemForm";

type StatusFilter = "active" | "archived" | "all";
type Confirmation = "archive" | "restore" | "delete" | null;

export function RecordItemsView({
  mode,
  items,
  loading,
  onChanged,
  onError,
}: {
  mode: RecordMode;
  items: RecordItem[];
  loading: boolean;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [status, setStatus] = useState<StatusFilter>("active");
  const [query, setQuery] = useState("");
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [editing, setEditing] = useState<RecordItem | "new" | null>(null);
  const [confirm, setConfirm] = useState<Confirmation>(null);
  const [busy, setBusy] = useState(false);
  const label = mode === "ACCUMULATE" ? "accumulate" : "measurement";

  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return items
      .filter((item) => item.mode === mode)
      .filter((item) => status === "all" || item.archived === (status === "archived"))
      .filter((item) => !normalized || `${item.name} ${item.description} ${item.unit}`.toLocaleLowerCase().includes(normalized))
      .sort((left, right) => left.priority - right.priority || left.name.localeCompare(right.name));
  }, [items, mode, query, status]);

  const columns = useMemo<ColumnDef<RecordItem>[]>(() => [
    {
      id: "select",
      header: ({ table }) => (
        <input aria-label={`Select all ${label} items`} type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />
      ),
      cell: ({ row }) => (
        <input aria-label={`Select ${row.original.name}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} />
      ),
    },
    {
      id: "name",
      header: "Record item",
      cell: ({ row }) => (
        <div className="primary-cell">
          <strong>{row.original.name}</strong>
          <span>{row.original.description || "No description"}</span>
        </div>
      ),
    },
    {
      id: "rule",
      header: mode === "ACCUMULATE" ? "Goal" : "Range",
      cell: ({ row }) => mode === "ACCUMULATE"
        ? row.original.goalValue === null
          ? "No goal"
          : `${row.original.goalValue} ${row.original.unit} / ${row.original.goalPeriod === "DAY" ? "day" : "7 days"}`
        : `${row.original.minValue}–${row.original.maxValue} ${row.original.unit}`,
    },
    {
      id: "step",
      header: "Step",
      cell: ({ row }) => `${row.original.stepValue} ${row.original.unit}`,
    },
    { accessorKey: "priority", header: "Priority" },
    {
      id: "status",
      header: "Status",
      cell: ({ row }) => <Badge tone={row.original.archived ? "muted" : "accent"}>{row.original.archived ? "Archived" : "Active"}</Badge>,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <button type="button" className="icon-button" aria-label={`Edit ${row.original.name}`} title="Edit" onClick={() => setEditing(row.original)}>
          <Edit3 size={16} />
        </button>
      ),
    },
  ], [label, mode]);

  const table = useReactTable({
    data: filtered,
    columns,
    state: { rowSelection: selection },
    onRowSelectionChange: setSelection,
    getRowId: (row) => String(row.id),
    enableRowSelection: true,
    getCoreRowModel: getCoreRowModel(),
  });
  const selected = filtered.filter((item) => selection[String(item.id)]);
  const activeIds = selected.filter((item) => !item.archived).map((item) => item.id);
  const archivedIds = selected.filter((item) => item.archived).map((item) => item.id);

  async function applyConfirmation() {
    if (!confirm) return;
    setBusy(true);
    try {
      if (confirm === "archive") await api.archive(activeIds, true);
      if (confirm === "restore") await api.archive(archivedIds, false);
      if (confirm === "delete") await Promise.all(archivedIds.map((id) => api.deleteItem(id)));
      const count = confirm === "archive" ? activeIds.length : archivedIds.length;
      setSelection({});
      setConfirm(null);
      await onChanged(`${capitalize(confirm)}d ${count} ${label} item${count === 1 ? "" : "s"}`);
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-stack">
      <div className="filter-bar">
        <div className="segmented" role="group" aria-label={`${label} status`}>
          {(["active", "archived", "all"] as const).map((value) => (
            <button key={value} type="button" className={status === value ? "active" : undefined} onClick={() => { setStatus(value); setSelection({}); }}>
              {capitalize(value)}
            </button>
          ))}
        </div>
        <label className="search-field">
          <Search size={16} />
          <input aria-label={`Search ${label} items`} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search name, description or unit" />
        </label>
        <span className="result-count">{loading ? "Loading…" : `${filtered.length} items`}</span>
        <div className="toolbar-spacer" />
        <button type="button" onClick={() => setEditing("new")}><Plus size={16} />New {label}</button>
      </div>

      <SelectionBar count={selected.length} label="selected">
        <button type="button" className="secondary" disabled={!activeIds.length} onClick={() => setConfirm("archive")}><Archive size={16} />Archive</button>
        <button type="button" className="secondary" disabled={!archivedIds.length} onClick={() => setConfirm("restore")}><ArchiveRestore size={16} />Restore</button>
        <button type="button" className="danger-secondary" disabled={!archivedIds.length} onClick={() => setConfirm("delete")}><Trash2 size={16} />Delete permanently</button>
      </SelectionBar>

      <DataTable table={table} loading={loading} empty={loading ? `Loading ${label} items…` : `No ${label} items match this view.`} />

      {editing && (
        <ItemForm
          mode={mode}
          item={editing === "new" ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            setEditing(null);
            await onChanged(`${capitalize(label)} saved`);
          }}
        />
      )}

      <ConfirmDialog
        open={confirm !== null}
        title={confirm === "delete" ? `Delete ${label} items permanently` : `${capitalize(confirm ?? "")} ${label} items`}
        message={confirm === "delete"
          ? `Delete ${archivedIds.length} archived ${label} item${archivedIds.length === 1 ? "" : "s"} and all of their entries? This cannot be undone.`
          : `${capitalize(confirm ?? "")} ${confirm === "archive" ? activeIds.length : archivedIds.length} ${label} item${(confirm === "archive" ? activeIds.length : archivedIds.length) === 1 ? "" : "s"}?`}
        confirmLabel={confirm === "delete" ? "Delete permanently" : capitalize(confirm ?? "Confirm")}
        danger={confirm === "delete"}
        busy={busy}
        onCancel={() => setConfirm(null)}
        onConfirm={() => void applyConfirmation()}
      />
    </section>
  );
}

function capitalize(value: string): string {
  return value ? `${value[0]!.toUpperCase()}${value.slice(1)}` : value;
}
