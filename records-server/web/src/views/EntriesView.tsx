import { useMemo, useState } from "react";
import { getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { SelectionBar } from "../components/SelectionBar";
import { localToday, minute, shiftLocalDate } from "../lib/format";
import type { RecordEntry, RecordItem } from "../types";

export type EntryFilters = {
  startDate: string;
  endDate: string;
  itemId: string;
};

type DatePreset = "today" | "7d" | "30d" | "custom";

function resolvePreset(filters: EntryFilters): DatePreset {
  const today = localToday();
  if (filters.startDate === today && filters.endDate === today) return "today";
  if (filters.startDate === shiftLocalDate(today, -6) && filters.endDate === today) return "7d";
  if (filters.startDate === shiftLocalDate(today, -29) && filters.endDate === today) return "30d";
  return "custom";
}

function presetRange(preset: Exclude<DatePreset, "custom">): Pick<EntryFilters, "startDate" | "endDate"> {
  const today = localToday();
  if (preset === "today") return { startDate: today, endDate: today };
  if (preset === "7d") return { startDate: shiftLocalDate(today, -6), endDate: today };
  return { startDate: shiftLocalDate(today, -29), endDate: today };
}

export function EntriesView({
  entries,
  items,
  filters,
  loading,
  onFilters,
  onDelete,
}: {
  entries: RecordEntry[];
  items: RecordItem[];
  filters: EntryFilters;
  loading: boolean;
  onFilters: (value: EntryFilters) => void;
  onDelete: (ids: number[]) => Promise<void>;
}) {
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const preset = resolvePreset(filters);

  const columns = useMemo<ColumnDef<RecordEntry>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <input
            aria-label="Select all entries"
            type="checkbox"
            checked={table.getIsAllRowsSelected()}
            onChange={table.getToggleAllRowsSelectedHandler()}
          />
        ),
        cell: ({ row }) => (
          <input
            aria-label={`Select ${row.original.itemName}`}
            type="checkbox"
            checked={row.getIsSelected()}
            onChange={row.getToggleSelectedHandler()}
          />
        ),
      },
      { accessorKey: "localDate", header: "Date" },
      {
        id: "time",
        header: "Time",
        cell: ({ row }) => minute(row.original.localMinute),
      },
      { accessorKey: "itemName", header: "Item" },
      {
        id: "mode",
        header: "Mode",
        cell: ({ row }) => (
          <Badge tone={row.original.itemMode === "MEASUREMENT" ? "accent" : "neutral"}>
            {row.original.itemMode === "MEASUREMENT" ? "Measurement" : "Accumulate"}
          </Badge>
        ),
      },
      {
        id: "value",
        header: "Value",
        cell: ({ row }) => `${row.original.value} ${row.original.itemUnit}`,
      },
    ],
    [],
  );

  const table = useReactTable({
    data: entries,
    columns,
    state: { rowSelection: selection },
    onRowSelectionChange: setSelection,
    getRowId: (row) => String(row.id),
    enableRowSelection: true,
    getCoreRowModel: getCoreRowModel(),
  });

  const ids = Object.keys(selection)
    .filter((id) => selection[id])
    .map(Number);

  function updateFilters(patch: Partial<EntryFilters>) {
    onFilters({ ...filters, ...patch });
  }

  async function confirmDelete() {
    setBusy(true);
    try {
      await onDelete(ids);
      setSelection({});
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-panel">
      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Date range">
          {(
            [
              ["today", "Today"],
              ["7d", "7 days"],
              ["30d", "30 days"],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={preset === key ? "active" : undefined}
              onClick={() => updateFilters(presetRange(key))}
            >
              {label}
            </button>
          ))}
        </div>

        <label>
          From
          <input
            type="date"
            value={filters.startDate}
            onChange={(event) => updateFilters({ startDate: event.target.value })}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={filters.endDate}
            onChange={(event) => updateFilters({ endDate: event.target.value })}
          />
        </label>
        <label>
          Item
          <select value={filters.itemId} onChange={(event) => updateFilters({ itemId: event.target.value })}>
            <option value="">All items</option>
            {items.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>

        <p className="result-count" aria-live="polite">
          {loading ? "Loading…" : `${entries.length} entries`}
        </p>
      </div>

      <SelectionBar count={ids.length} label="selected">
        <button type="button" className="danger" onClick={() => setConfirmOpen(true)}>
          Delete selected
        </button>
      </SelectionBar>

      <DataTable
        table={table}
        loading={loading}
        empty={loading ? "Loading entries…" : "No entries in this range."}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="Delete entries"
        message={`Delete ${ids.length} entries? This cannot be undone.`}
        confirmLabel="Delete"
        danger
        busy={busy}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void confirmDelete()}
      />
    </section>
  );
}
