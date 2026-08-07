import { useEffect, useMemo, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { SelectionBar } from "../components/SelectionBar";
import { localToday, message, minute, shiftLocalDate } from "../lib/format";
import type { RecordEntry, RecordItem, RecordMode } from "../types";

export type EntryFilters = { startDate: string; endDate: string; itemId: string; mode: "" | RecordMode };
type DatePreset = "today" | "7d" | "30d" | "custom";

export function EntriesView({
  entries,
  items,
  filters,
  loading,
  onFilters,
  onChanged,
  onError,
}: {
  entries: RecordEntry[];
  items: RecordItem[];
  filters: EntryFilters;
  loading: boolean;
  onFilters: (value: EntryFilters) => void;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const preset = resolvePreset(filters);
  const selectableItems = items.filter((item) => !filters.mode || item.mode === filters.mode);

  useEffect(() => {
    if (filters.itemId && !selectableItems.some((item) => String(item.id) === filters.itemId)) {
      onFilters({ ...filters, itemId: "" });
    }
  }, [filters, selectableItems]);

  const columns = useMemo<ColumnDef<RecordEntry>[]>(() => [
    {
      id: "select",
      header: ({ table }) => <input aria-label="Select all entries" type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />,
      cell: ({ row }) => <input aria-label={`Select ${row.original.itemName}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} />,
    },
    { accessorKey: "localDate", header: "Date" },
    { id: "time", header: "Time", cell: ({ row }) => minute(row.original.localMinute) },
    {
      id: "item",
      header: "Record item",
      cell: ({ row }) => <div className="primary-cell"><strong>{row.original.itemName}</strong><span>{row.original.itemUnit}</span></div>,
    },
    {
      id: "mode",
      header: "Mode",
      cell: ({ row }) => <Badge tone={row.original.itemMode === "MEASUREMENT" ? "accent" : "neutral"}>{row.original.itemMode === "MEASUREMENT" ? "Measurement" : "Accumulate"}</Badge>,
    },
    { id: "value", header: "Value", cell: ({ row }) => <strong>{row.original.value} {row.original.itemUnit}</strong> },
    { id: "created", header: "Added", cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString("en", { month: "short", day: "numeric" }) },
  ], []);
  const table = useReactTable({ data: entries, columns, state: { rowSelection: selection }, onRowSelectionChange: setSelection, getRowId: (row) => String(row.id), enableRowSelection: true, getCoreRowModel: getCoreRowModel() });
  const ids = Object.keys(selection).filter((id) => selection[id]).map(Number);

  function updateFilters(patch: Partial<EntryFilters>) {
    setSelection({});
    onFilters({ ...filters, ...patch });
  }

  async function deleteSelected() {
    setBusy(true);
    try {
      await api.deleteEntries(ids);
      setSelection({});
      setConfirmOpen(false);
      await onChanged(`Deleted ${ids.length} entr${ids.length === 1 ? "y" : "ies"}`);
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-stack">
      <div className="filter-bar filter-bar-wrap">
        <div className="segmented" role="group" aria-label="Entry date range">
          {(["today", "7d", "30d"] as const).map((value) => <button key={value} type="button" className={preset === value ? "active" : undefined} onClick={() => updateFilters(presetRange(value))}>{value === "today" ? "Today" : value === "7d" ? "7 days" : "30 days"}</button>)}
        </div>
        <label>From<input aria-label="From" type="date" value={filters.startDate} onChange={(event) => updateFilters({ startDate: event.target.value })} /></label>
        <label>To<input aria-label="To" type="date" value={filters.endDate} onChange={(event) => updateFilters({ endDate: event.target.value })} /></label>
        <label>Mode<select aria-label="Mode" value={filters.mode} onChange={(event) => updateFilters({ mode: event.target.value as EntryFilters["mode"], itemId: "" })}><option value="">All modes</option><option value="ACCUMULATE">Accumulate</option><option value="MEASUREMENT">Measurement</option></select></label>
        <label className="filter-grow">Record item<select aria-label="Item" value={filters.itemId} onChange={(event) => updateFilters({ itemId: event.target.value })}><option value="">All items</option>{selectableItems.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <span className="result-count">{loading ? "Loading…" : `${entries.length} entries`}</span>
        <div className="toolbar-spacer" />
        <button type="button" onClick={() => setAdding(true)} disabled={!items.some((item) => !item.archived)}><Plus size={16} />Add entry</button>
      </div>

      <SelectionBar count={ids.length} label="selected">
        <button type="button" className="danger-secondary" onClick={() => setConfirmOpen(true)}><Trash2 size={16} />Delete selected</button>
      </SelectionBar>
      <DataTable table={table} loading={loading} empty={loading ? "Loading entries…" : "No entries match this range."} />

      {adding && <EntryForm items={items.filter((item) => !item.archived)} onClose={() => setAdding(false)} onSaved={async () => { setAdding(false); await onChanged("Entry added"); }} onError={onError} />}
      <ConfirmDialog open={confirmOpen} title="Delete entries" message={`Delete ${ids.length} selected entr${ids.length === 1 ? "y" : "ies"}? This cannot be undone.`} confirmLabel="Delete entries" danger busy={busy} onCancel={() => setConfirmOpen(false)} onConfirm={() => void deleteSelected()} />
    </section>
  );
}

function EntryForm({ items, onClose, onSaved, onError }: { items: RecordItem[]; onClose: () => void; onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [itemId, setItemId] = useState(String(items[0]?.id ?? ""));
  const [occurredAt, setOccurredAt] = useState(nowLocalInput());
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const item = items.find((candidate) => String(candidate.id) === itemId);
  const inputStep = item?.mode === "MEASUREMENT" ? item.stepValue : "any";

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!item) return;
    setBusy(true);
    try {
      const date = new Date(occurredAt);
      const localDate = occurredAt.slice(0, 10);
      const localMinute = date.getHours() * 60 + date.getMinutes();
      await api.createEntry({ itemId: item.id, occurredAt: date.getTime(), localDate, localMinute, value: Number(value) });
      await onSaved();
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Add entry" onClose={onClose}>
      <form className="item-form" onSubmit={(event) => void submit(event)}>
        <label>Record item<select autoFocus value={itemId} onChange={(event) => setItemId(event.target.value)}>{items.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name} · {candidate.mode === "MEASUREMENT" ? "Measurement" : "Accumulate"}</option>)}</select></label>
        <div className="form-grid form-grid-2">
          <label>When<input type="datetime-local" required value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} /></label>
          <label>Value<input type="number" required min={item?.mode === "ACCUMULATE" ? 0.000001 : item?.minValue ?? undefined} max={item?.mode === "MEASUREMENT" ? item.maxValue ?? undefined : undefined} step={inputStep} value={value} onChange={(event) => setValue(event.target.value)} /></label>
        </div>
        {item && <p className="field-hint">Allowed: {item.mode === "MEASUREMENT" ? `${item.minValue}–${item.maxValue} ${item.unit} in steps of ${item.stepValue}` : `any positive value in ${item.unit} · suggested step ${item.stepValue}`}</p>}
        <div className="modal-actions"><button type="button" className="secondary" onClick={onClose} disabled={busy}>Cancel</button><button disabled={busy}><Plus size={16} />{busy ? "Adding…" : "Add entry"}</button></div>
      </form>
    </Modal>
  );
}

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

function nowLocalInput(): string {
  const date = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
