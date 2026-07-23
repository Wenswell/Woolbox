import { useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { api } from "./api";
import type { EntryAuditSnapshot, ItemAuditSnapshot, ItemInput, RecordAuditLog, RecordEntry, RecordItem, RecordMode, StepAuditSnapshot, TaskAuditSnapshot, ValueType } from "./types";

const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
type View = "entries" | "items" | "logs";

export function App() {
  const [view, setView] = useState<View>("entries");
  const [items, setItems] = useState<RecordItem[]>([]);
  const [entries, setEntries] = useState<RecordEntry[]>([]);
  const [logs, setLogs] = useState<RecordAuditLog[]>([]);
  const [nextLogId, setNextLogId] = useState<number | null>(null);
  const [filters, setFilters] = useState({ startDate: today, endDate: today, itemId: "" });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadItems = useCallback(async () => setItems(await api.items()), []);
  const loadEntries = useCallback(async () => setEntries(await api.entries(filters.startDate, filters.endDate, filters.itemId)), [filters]);
  const loadLogs = useCallback(async (beforeId?: number) => {
    const page = await api.auditLogs(beforeId);
    setLogs((current) => beforeId === undefined ? page.logs : [...current, ...page.logs]);
    setNextLogId(page.nextBeforeId);
  }, []);
  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      await Promise.all([loadItems(), loadEntries(), loadLogs()]);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }, [loadEntries, loadItems, loadLogs]);
  useEffect(() => { void refresh(); }, [refresh]);

  return <main>
    <header><div><p className="eyebrow">Records administration</p><h1>Records</h1></div><button className="secondary" onClick={() => void refresh()}>Refresh</button></header>
    <nav aria-label="Views"><button className={view === "entries" ? "active" : ""} onClick={() => setView("entries")}>Entries</button><button className={view === "items" ? "active" : ""} onClick={() => setView("items")}>Record items</button><button className={view === "logs" ? "active" : ""} onClick={() => setView("logs")}>Logs</button></nav>
    {error && <div className="error" role="alert">{error}<button onClick={() => void refresh()}>Retry</button></div>}
    {view === "entries" && <EntriesView entries={entries} items={items} filters={filters} loading={loading} onFilters={setFilters} onRefresh={refresh} onError={setError} />}
    {view === "items" && <ItemsView items={items} loading={loading} onRefresh={refresh} onError={setError} />}
    {view === "logs" && <AuditLogView logs={logs} loading={loading} nextLogId={nextLogId} onLoadMore={() => nextLogId !== null && void loadLogs(nextLogId)} />}
  </main>;
}

function EntriesView({ entries, items, filters, loading, onFilters, onRefresh, onError }: { entries: RecordEntry[]; items: RecordItem[]; filters: { startDate: string; endDate: string; itemId: string }; loading: boolean; onFilters: (value: { startDate: string; endDate: string; itemId: string }) => void; onRefresh: () => Promise<void>; onError: (value: string | null) => void }) {
  const [draft, setDraft] = useState(filters);
  const [selection, setSelection] = useState<RowSelectionState>({});
  const columns = useMemo<ColumnDef<RecordEntry>[]>(() => [
    { id: "select", header: ({ table }) => <input aria-label="Select all entries" type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />, cell: ({ row }) => <input aria-label={`Select ${row.original.itemName}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} /> },
    { accessorKey: "localDate", header: "Date" }, { id: "time", header: "Time", cell: ({ row }) => minute(row.original.localMinute) }, { accessorKey: "itemName", header: "Item" }, { id: "value", header: "Value", cell: ({ row }) => `${row.original.value} ${row.original.itemUnit}` },
  ], []);
  const table = useReactTable({ data: entries, columns, state: { rowSelection: selection }, onRowSelectionChange: setSelection, getRowId: (row) => String(row.id), enableRowSelection: true, getCoreRowModel: getCoreRowModel() });
  const ids = Object.keys(selection).filter((id) => selection[id]).map(Number);
  async function removeSelected() {
    if (!confirm(`Delete ${ids.length} entries?`)) return;
    try { await api.deleteEntries(ids); setSelection({}); await onRefresh(); } catch (reason) { onError(message(reason)); }
  }
  return <section><div className="toolbar"><label>From<input type="date" value={draft.startDate} onChange={(event) => setDraft({ ...draft, startDate: event.target.value })} /></label><label>To<input type="date" value={draft.endDate} onChange={(event) => setDraft({ ...draft, endDate: event.target.value })} /></label><label>Item<select value={draft.itemId} onChange={(event) => setDraft({ ...draft, itemId: event.target.value })}><option value="">All items</option>{items.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button onClick={() => onFilters(draft)}>Apply</button><button className="danger" disabled={!ids.length} onClick={() => void removeSelected()}>Delete selected</button></div><DataTable table={table} empty={loading ? "Loading..." : "No entries in this range."} /></section>;
}

function ItemsView({ items, loading, onRefresh, onError }: { items: RecordItem[]; loading: boolean; onRefresh: () => Promise<void>; onError: (value: string | null) => void }) {
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [editing, setEditing] = useState<RecordItem | "new" | null>(null);
  const columns = useMemo<ColumnDef<RecordItem>[]>(() => [
    { id: "select", header: ({ table }) => <input aria-label="Select all items" type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />, cell: ({ row }) => <input aria-label={`Select ${row.original.name}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} /> },
    { accessorKey: "name", header: "Name" }, { id: "type", header: "Type", cell: ({ row }) => row.original.mode === "MEASUREMENT" ? "Measurement" : row.original.valueType }, { id: "unit", header: "Unit / range", cell: ({ row }) => row.original.mode === "MEASUREMENT" ? `${row.original.unit} / ${row.original.minValue}–${row.original.maxValue} · ${row.original.stepValue}` : `${row.original.unit} / ${row.original.stepValue}` },
    { id: "status", header: "Status", cell: ({ row }) => <span className={row.original.archived ? "status archived" : "status"}>{row.original.archived ? "Archived" : "Active"}</span> }, { id: "actions", header: "", cell: ({ row }) => <button className="link" onClick={() => setEditing(row.original)}>Edit</button> },
  ], []);
  const table = useReactTable({ data: items, columns, state: { rowSelection: selection }, onRowSelectionChange: setSelection, getRowId: (row) => String(row.id), enableRowSelection: true, getCoreRowModel: getCoreRowModel() });
  const selected = items.filter((item) => selection[String(item.id)]);
  async function archive(archived: boolean) {
    const ids = selected.filter((item) => item.archived !== archived).map((item) => item.id);
    if (!ids.length) return;
    try { await api.archive(ids, archived); setSelection({}); await onRefresh(); } catch (reason) { onError(message(reason)); }
  }
  return <section><div className="toolbar"><button onClick={() => setEditing("new")}>New item</button><button className="secondary" disabled={!selected.some((item) => !item.archived)} onClick={() => void archive(true)}>Archive selected</button><button className="secondary" disabled={!selected.some((item) => item.archived)} onClick={() => void archive(false)}>Restore selected</button></div><DataTable table={table} empty={loading ? "Loading..." : "No record items."} />{editing && <ItemForm item={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onSaved={async () => { setEditing(null); await onRefresh(); }} />}</section>;
}

function AuditLogView({ logs, loading, nextLogId, onLoadMore }: { logs: RecordAuditLog[]; loading: boolean; nextLogId: number | null; onLoadMore: () => void }) {
  const columns = useMemo<ColumnDef<RecordAuditLog>[]>(() => [
    { id: "time", header: "Time", cell: ({ row }) => formatDateTime(row.original.createdAt) }, { id: "object", header: "Object", cell: ({ row }) => auditSubject(row.original) }, { id: "action", header: "Action", cell: ({ row }) => actionLabel(row.original.action) }, { id: "changes", header: "Changes", cell: ({ row }) => <span className="audit-change">{auditChanges(row.original)}</span> },
  ], []);
  const table = useReactTable({ data: logs, columns, getCoreRowModel: getCoreRowModel() });
  return <section><DataTable table={table} empty={loading ? "Loading..." : "No logged operations."} />{nextLogId !== null && <div className="toolbar"><button className="secondary" onClick={onLoadMore}>Load older operations</button></div>}</section>;
}

function DataTable({ table, empty }: { table: ReturnType<typeof useReactTable<any>>; empty: string }) { return <div className="table-wrap"><table><thead>{table.getHeaderGroups().map((group) => <tr key={group.id}>{group.headers.map((header) => <th key={header.id}>{flexRender(header.column.columnDef.header, header.getContext())}</th>)}</tr>)}</thead><tbody>{table.getRowModel().rows.length ? table.getRowModel().rows.map((row) => <tr key={row.id}>{row.getVisibleCells().map((cell) => <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}</tr>) : <tr><td colSpan={table.getAllColumns().length} className="empty">{empty}</td></tr>}</tbody></table></div>; }

function ItemForm({ item, onClose, onSaved }: { item?: RecordItem; onClose: () => void; onSaved: () => Promise<void> }) {
  const [formError, setFormError] = useState<string | null>(null);
  const { register, watch, handleSubmit, formState: { isSubmitting } } = useForm<ItemFormValues>({
    defaultValues: {
      name: item?.name ?? "",
      description: item?.description ?? "",
      mode: item?.mode ?? "ACCUMULATE",
      valueType: item?.valueType ?? "NUMBER",
      defaultValue: item?.defaultValue ?? "",
      minValue: item?.minValue ?? "",
      maxValue: item?.maxValue ?? "",
      stepValue: item?.stepValue ?? 1,
      unit: item?.unit ?? "次",
      goalValue: item?.goalValue ?? "",
      goalPeriod: item?.goalPeriod ?? "DAY",
      priority: item?.priority ?? 0,
      windows: item?.windows.map((window) => `${minute(window.startMinute)}-${minute(window.endMinute)}`).join("\n") ?? "",
    },
  });
  const mode = watch("mode");
  const valueType = watch("valueType");
  const measurement = mode === "MEASUREMENT";

  return <Modal title={item ? "Edit record item" : "New record item"} onClose={onClose}><form onSubmit={handleSubmit(async (values) => {
    setFormError(null);
    const input: ItemInput = {
      name: values.name,
      description: values.description,
      mode: values.mode,
      valueType: measurement ? "NUMBER" : values.valueType,
      defaultValue: measurement ? null : numberOrNull(values.defaultValue),
      minValue: measurement ? Number(values.minValue) : null,
      maxValue: measurement ? Number(values.maxValue) : null,
      stepValue: Number(values.stepValue),
      unit: values.unit,
      goalValue: measurement ? null : numberOrNull(values.goalValue),
      goalPeriod: measurement || !values.goalValue ? null : values.goalPeriod,
      priority: Number(values.priority),
      windows: measurement ? [] : parseWindows(values.windows),
    };
    try { await api.saveItem(input, item?.id); await onSaved(); } catch (reason) { setFormError(message(reason)); }
  })}><label>Name<input {...register("name", { required: true })} /></label><label>Description<textarea {...register("description")} /></label><div className="grid"><label>Mode<select {...register("mode")}><option value="ACCUMULATE">Accumulate</option><option value="MEASUREMENT">Measurement</option></select></label>{measurement ? <label>Type<input value="Number" readOnly /></label> : <label>Type<select {...register("valueType")}><option value="NUMBER">Number</option><option value="DURATION">Duration</option></select></label>}<label>Unit{valueType === "DURATION" && !measurement ? <select {...register("unit")}><option>秒</option><option>分</option><option>小时</option></select> : <input {...register("unit", { required: true })} />}</label>{measurement ? <><label>Minimum<input type="number" step="any" {...register("minValue", { required: true })} /></label><label>Maximum<input type="number" step="any" {...register("maxValue", { required: true })} /></label></> : <label>Default value<input type="number" min="0" step="any" {...register("defaultValue")} /></label>}<label>Step<input type="number" min="0.000001" step="any" {...register("stepValue", { required: true })} /></label>{!measurement && <><label>Goal<input type="number" min="0" step="any" {...register("goalValue")} /></label><label>Goal period<select {...register("goalPeriod")}><option value="DAY">Day</option><option value="WEEK">Week</option></select></label></>}<label>Priority<input type="number" step="1" {...register("priority", { required: true })} /></label></div>{!measurement && <label>Suggested windows<textarea placeholder="08:00-09:00, one per line" {...register("windows")} /></label>}{formError && <p className="form-error" role="alert">{formError}</p>}<FormActions saving={isSubmitting} onClose={onClose} /></form></Modal>;
}

interface ItemFormValues { name: string; description: string; mode: RecordMode; valueType: ValueType; defaultValue: string | number; minValue: string | number; maxValue: string | number; stepValue: string | number; unit: string; goalValue: string | number; goalPeriod: "DAY" | "WEEK"; priority: string | number; windows: string }

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) { return <div className="modal-backdrop" role="presentation"><div className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-title"><h2>{title}</h2><button className="icon" aria-label="Close" onClick={onClose}>x</button></div>{children}</div></div>; }
function FormActions({ saving, onClose }: { saving: boolean; onClose: () => void }) { return <div className="form-actions"><button type="button" className="secondary" onClick={onClose}>Cancel</button><button disabled={saving}>{saving ? "Saving..." : "Save"}</button></div>; }
function minute(value: number) { return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`; }
function numberOrNull(value: string | number) { return value === "" ? null : Number(value); }
function parseWindows(value: string) { return value.split(/\n|,/).map((item) => item.trim()).filter(Boolean).map((window) => { const [start, end] = window.split("-"); return { startMinute: timeMinute(start), endMinute: timeMinute(end) }; }); }
function timeMinute(value: string) { const [hour, minuteValue] = value.split(":").map(Number); return hour * 60 + minuteValue; }
function message(reason: unknown) { return reason instanceof Error ? reason.message : "Request failed."; }
function formatDateTime(epoch: number) { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" }).format(epoch); }
function auditSubject(log: RecordAuditLog) { const snapshot = log.after ?? log.before; if (!snapshot) return `${log.entityType} #${log.entityId}`; if ("name" in snapshot) return `Record item: ${snapshot.name}`; if ("itemName" in snapshot) return `Entry: ${snapshot.itemName}`; return "taskTitle" in snapshot ? `Subtask: ${snapshot.taskTitle}` : `Task: ${snapshot.title}`; }
function actionLabel(action: RecordAuditLog["action"]) { return ({ ITEM_CREATED: "Created item", ITEM_UPDATED: "Updated item", ITEM_ARCHIVED: "Archived item", ITEM_RESTORED: "Restored item", ITEM_DELETED: "Deleted item", ENTRY_CREATED: "Created entry", ENTRY_DELETED: "Deleted entry", TASK_CREATED: "Created task", TASK_UPDATED: "Updated task", TASK_COMPLETED: "Completed task", TASK_REOPENED: "Reopened task", TASK_DELETED: "Deleted task", STEP_CREATED: "Created subtask", STEP_UPDATED: "Updated subtask", STEP_COMPLETED: "Completed subtask", STEP_REOPENED: "Reopened subtask", STEP_DELETED: "Deleted subtask" })[action]; }
function auditChanges(log: RecordAuditLog) {
  const before = log.before;
  const after = log.after;
  if (log.action === "ITEM_CREATED" && after && "name" in after) return itemDescription(after);
  if (log.action === "ITEM_DELETED" && before && "name" in before) return itemDescription(before);
  if (log.action === "ENTRY_CREATED" && after && isEntrySnapshot(after)) return entryDescription(after);
  if (log.action === "ENTRY_DELETED" && before && isEntrySnapshot(before)) return entryDescription(before);
  if (log.action === "TASK_CREATED" && after && isTaskSnapshot(after)) return taskDescription(after);
  if (log.action === "TASK_DELETED" && before && isTaskSnapshot(before)) return taskDescription(before);
  if (log.action === "STEP_CREATED" && after && isStepSnapshot(after)) return stepDescription(after);
  if (log.action === "STEP_DELETED" && before && isStepSnapshot(before)) return stepDescription(before);
  if ((log.action === "TASK_COMPLETED" || log.action === "TASK_REOPENED") && after && isTaskSnapshot(after)) return after.completedAt === null ? "Status: Open" : "Status: Completed";
  if ((log.action === "STEP_COMPLETED" || log.action === "STEP_REOPENED") && after && isStepSnapshot(after)) return after.completedAt === null ? "Status: Open" : "Status: Completed";
  if (log.action === "TASK_UPDATED" && before && after && isTaskSnapshot(before) && isTaskSnapshot(after)) return fieldChange("Title", before.title, after.title) ?? "No visible field changes.";
  if (log.action === "STEP_UPDATED" && before && after && isStepSnapshot(before) && isStepSnapshot(after)) return fieldChange("Title", before.title, after.title) ?? "No visible field changes.";
  if (log.action === "ITEM_ARCHIVED") return "Status: Active to Archived";
  if (log.action === "ITEM_RESTORED") return "Status: Archived to Active";
  if (!before || !after || !("name" in before) || !("name" in after)) return "No details.";
  const changes = [fieldChange("Name", before.name, after.name), fieldChange("Description", before.description, after.description), fieldChange("Mode", before.mode, after.mode), fieldChange("Type", before.valueType, after.valueType), fieldChange("Default", before.defaultValue, after.defaultValue), fieldChange("Minimum", before.minValue, after.minValue), fieldChange("Maximum", before.maxValue, after.maxValue), fieldChange("Step", before.stepValue, after.stepValue), fieldChange("Unit", before.unit, after.unit), fieldChange("Goal", itemGoal(before), itemGoal(after)), fieldChange("Priority", before.priority, after.priority), fieldChange("Suggested windows", itemWindows(before), itemWindows(after))].filter((value): value is string => value !== null);
  return changes.join("; ") || "No visible field changes.";
}
function itemDescription(item: ItemAuditSnapshot) { return item.mode === "MEASUREMENT" ? `${item.name}; ${item.minValue}–${item.maxValue} ${item.unit}; step ${item.stepValue}` : `${item.name}; ${itemGoal(item)}; step ${item.stepValue} ${item.unit}`; }
function entryDescription(entry: EntryAuditSnapshot) { return `${entry.itemName}; ${entry.localDate} ${minute(entry.localMinute)}; ${entry.value}`; }
function taskDescription(task: TaskAuditSnapshot) { return task.stepCount === undefined ? task.title : `${task.title}; ${task.stepCount} subtasks`; }
function stepDescription(step: StepAuditSnapshot) { return `${step.taskTitle}; ${step.title}`; }
function isEntrySnapshot(snapshot: NonNullable<RecordAuditLog["before"]>): snapshot is EntryAuditSnapshot { return "itemName" in snapshot; }
function isTaskSnapshot(snapshot: NonNullable<RecordAuditLog["before"]>): snapshot is TaskAuditSnapshot { return "title" in snapshot && !("taskTitle" in snapshot) && !("name" in snapshot); }
function isStepSnapshot(snapshot: NonNullable<RecordAuditLog["before"]>): snapshot is StepAuditSnapshot { return "taskTitle" in snapshot; }
function itemGoal(item: ItemAuditSnapshot) { return item.goalValue === null ? "No target" : `Target ${item.goalValue} ${item.unit} / ${item.goalPeriod === "DAY" ? "day" : "week"}`; }
function itemWindows(item: ItemAuditSnapshot) { return item.windows.map((window) => `${minute(window.startMinute)}-${minute(window.endMinute)}`).join(", ") || "None"; }
function fieldChange(label: string, before: unknown, after: unknown) { return String(before) === String(after) ? null : `${label}: ${String(before)} to ${String(after)}`; }
