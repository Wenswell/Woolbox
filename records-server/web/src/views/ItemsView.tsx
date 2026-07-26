import { useMemo, useState } from "react";
import { getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { SelectionBar } from "../components/SelectionBar";
import type { RecordItem } from "../types";
import { ItemForm } from "./ItemForm";

export type ItemStatusFilter = "all" | "active" | "archived";

export function ItemsView({
  items,
  loading,
  status,
  onStatusChange,
  onArchive,
  onSaved,
}: {
  items: RecordItem[];
  loading: boolean;
  status: ItemStatusFilter;
  onStatusChange: (status: ItemStatusFilter) => void;
  onArchive: (ids: number[], archived: boolean) => Promise<void>;
  onSaved: () => Promise<void>;
}) {
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [editing, setEditing] = useState<RecordItem | "new" | null>(null);
  const [confirm, setConfirm] = useState<"archive" | "restore" | null>(null);
  const [busy, setBusy] = useState(false);

  const columns = useMemo<ColumnDef<RecordItem>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <input
            aria-label="Select all items"
            type="checkbox"
            checked={table.getIsAllRowsSelected()}
            onChange={table.getToggleAllRowsSelectedHandler()}
          />
        ),
        cell: ({ row }) => (
          <input
            aria-label={`Select ${row.original.name}`}
            type="checkbox"
            checked={row.getIsSelected()}
            onChange={row.getToggleSelectedHandler()}
          />
        ),
      },
      { accessorKey: "name", header: "Name" },
      {
        id: "mode",
        header: "Mode",
        cell: ({ row }) => (
          <Badge tone={row.original.mode === "MEASUREMENT" ? "accent" : "neutral"}>
            {row.original.mode === "MEASUREMENT" ? "Measurement" : "Accumulate"}
          </Badge>
        ),
      },
      {
        id: "type",
        header: "Type",
        cell: ({ row }) => (row.original.mode === "MEASUREMENT" ? "Number" : row.original.valueType),
      },
      {
        id: "unit",
        header: "Unit / range",
        cell: ({ row }) =>
          row.original.mode === "MEASUREMENT"
            ? `${row.original.unit} / ${row.original.minValue}–${row.original.maxValue} · ${row.original.stepValue}`
            : `${row.original.unit} / ${row.original.stepValue}`,
      },
      {
        id: "status",
        header: "Status",
        cell: ({ row }) => (
          <Badge tone={row.original.archived ? "muted" : "accent"}>
            {row.original.archived ? "Archived" : "Active"}
          </Badge>
        ),
      },
      {
        id: "actions",
        header: "",
        cell: ({ row }) => (
          <button type="button" className="link" onClick={() => setEditing(row.original)}>
            Edit
          </button>
        ),
      },
    ],
    [],
  );

  const table = useReactTable({
    data: items,
    columns,
    state: { rowSelection: selection },
    onRowSelectionChange: setSelection,
    getRowId: (row) => String(row.id),
    enableRowSelection: true,
    getCoreRowModel: getCoreRowModel(),
  });

  const selected = items.filter((item) => selection[String(item.id)]);
  const archiveIds = selected.filter((item) => !item.archived).map((item) => item.id);
  const restoreIds = selected.filter((item) => item.archived).map((item) => item.id);

  async function runArchive(archived: boolean) {
    const ids = archived ? archiveIds : restoreIds;
    if (!ids.length) return;
    setBusy(true);
    try {
      await onArchive(ids, archived);
      setSelection({});
      setConfirm(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-panel">
      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Item status">
          {(
            [
              ["all", "All"],
              ["active", "Active"],
              ["archived", "Archived"],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={status === key ? "active" : undefined}
              onClick={() => {
                setSelection({});
                onStatusChange(key);
              }}
            >
              {label}
            </button>
          ))}
        </div>

        <p className="result-count" aria-live="polite">
          {loading ? "Loading…" : `${items.length} items`}
        </p>

        <div className="toolbar-spacer" />

        <button type="button" onClick={() => setEditing("new")}>
          New item
        </button>
      </div>

      <SelectionBar count={selected.length} label="selected">
        <button
          type="button"
          className="secondary"
          disabled={!archiveIds.length}
          onClick={() => setConfirm("archive")}
        >
          Archive selected
        </button>
        <button
          type="button"
          className="secondary"
          disabled={!restoreIds.length}
          onClick={() => setConfirm("restore")}
        >
          Restore selected
        </button>
      </SelectionBar>

      <DataTable table={table} loading={loading} empty={loading ? "Loading items…" : "No record items."} />

      {editing && (
        <ItemForm
          item={editing === "new" ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            setEditing(null);
            await onSaved();
          }}
        />
      )}

      <ConfirmDialog
        open={confirm === "archive"}
        title="Archive items"
        message={`Archive ${archiveIds.length} items?`}
        confirmLabel="Archive"
        busy={busy}
        onCancel={() => setConfirm(null)}
        onConfirm={() => void runArchive(true)}
      />
      <ConfirmDialog
        open={confirm === "restore"}
        title="Restore items"
        message={`Restore ${restoreIds.length} items?`}
        confirmLabel="Restore"
        busy={busy}
        onCancel={() => setConfirm(null)}
        onConfirm={() => void runArchive(false)}
      />
    </section>
  );
}
