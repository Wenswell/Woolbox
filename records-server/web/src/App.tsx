import { useCallback, useEffect, useMemo, useState } from "react";
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef, type RowSelectionState } from "@tanstack/react-table";
import { useForm } from "react-hook-form";
import { api } from "./api";
import type { EntryInput, ItemInput, RecordEntry, RecordItem, ValueType } from "./types";

const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10);

export function App() {
  const [view, setView] = useState<"entries" | "items">("entries");
  const [items, setItems] = useState<RecordItem[]>([]);
  const [entries, setEntries] = useState<RecordEntry[]>([]);
  const [filters, setFilters] = useState({ startDate: today, endDate: today, itemId: "" });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadItems = useCallback(async () => setItems(await api.items()), []);
  const loadEntries = useCallback(async () => setEntries(await api.entries(filters.startDate, filters.endDate, filters.itemId)), [filters]);
  const refresh = useCallback(async () => {
    setLoading(true); setError(null);
    try { await Promise.all([loadItems(), loadEntries()]); } catch (reason) { setError(message(reason)); } finally { setLoading(false); }
  }, [loadEntries, loadItems]);
  useEffect(() => { void refresh(); }, [refresh]);

  return <main>
    <header><div><p className="eyebrow">Records administration</p><h1>Records</h1></div><button className="secondary" onClick={() => void refresh()}>Refresh</button></header>
    <nav aria-label="Views"><button className={view === "entries" ? "active" : ""} onClick={() => setView("entries")}>Entries</button><button className={view === "items" ? "active" : ""} onClick={() => setView("items")}>Record items</button></nav>
    {error && <div className="error" role="alert">{error}<button onClick={() => void refresh()}>Retry</button></div>}
    {view === "entries"
      ? <EntriesView entries={entries} items={items} filters={filters} loading={loading} onFilters={setFilters} onRefresh={loadEntries} onError={setError} />
      : <ItemsView items={items} loading={loading} onRefresh={loadItems} onError={setError} />}
  </main>;
}

function EntriesView({ entries, items, filters, loading, onFilters, onRefresh, onError }: { entries: RecordEntry[]; items: RecordItem[]; filters: {startDate:string;endDate:string;itemId:string}; loading:boolean; onFilters:(value:{startDate:string;endDate:string;itemId:string})=>void; onRefresh:()=>Promise<void>; onError:(value:string|null)=>void }) {
  const [draft, setDraft] = useState(filters);
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [editing, setEditing] = useState<RecordEntry | null>(null);
  const columns = useMemo<ColumnDef<RecordEntry>[]>(() => [
    { id: "select", header: ({ table }) => <input aria-label="Select all entries" type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />, cell: ({ row }) => <input aria-label={`Select ${row.original.itemName}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} /> },
    { accessorKey: "localDate", header: "Date" }, { id: "time", header: "Time", cell: ({ row }) => minute(row.original.localMinute) },
    { accessorKey: "itemName", header: "Item" }, { accessorKey: "value", header: "Value", cell: ({ row }) => `${row.original.value} ${row.original.itemUnit}` },
    { id: "actions", header: "", cell: ({ row }) => <button className="link" onClick={() => setEditing(row.original)}>Edit</button> },
  ], []);
  const table = useReactTable({ data: entries, columns, state: { rowSelection: selection }, onRowSelectionChange: setSelection, getRowId: row => String(row.id), enableRowSelection: true, getCoreRowModel: getCoreRowModel() });
  const ids = Object.keys(selection).filter(id => selection[id]).map(Number);
  async function removeSelected() { if (!confirm(`Delete ${ids.length} entries?`)) return; try { await api.deleteEntries(ids); setSelection({}); await onRefresh(); } catch (reason) { onError(message(reason)); } }
  return <section>
    <div className="toolbar"><label>From<input type="date" value={draft.startDate} onChange={e => setDraft({...draft,startDate:e.target.value})} /></label><label>To<input type="date" value={draft.endDate} onChange={e => setDraft({...draft,endDate:e.target.value})} /></label><label>Item<select value={draft.itemId} onChange={e => setDraft({...draft,itemId:e.target.value})}><option value="">All items</option>{items.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button onClick={() => onFilters(draft)}>Apply</button><button className="danger" disabled={!ids.length} onClick={() => void removeSelected()}>Delete selected</button></div>
    <DataTable table={table} empty={loading ? "Loading..." : "No entries in this range."} />
    {editing && <EntryForm entry={editing} items={items.filter(item => !item.archived || item.id === editing.itemId)} onClose={() => setEditing(null)} onSaved={async () => { setEditing(null); await onRefresh(); }} onError={onError} />}
  </section>;
}

function ItemsView({ items, loading, onRefresh, onError }: { items:RecordItem[];loading:boolean;onRefresh:()=>Promise<void>;onError:(value:string|null)=>void }) {
  const [selection, setSelection] = useState<RowSelectionState>({});
  const [editing, setEditing] = useState<RecordItem | "new" | null>(null);
  const columns = useMemo<ColumnDef<RecordItem>[]>(() => [
    { id:"select", header:({table})=><input aria-label="Select all items" type="checkbox" checked={table.getIsAllRowsSelected()} onChange={table.getToggleAllRowsSelectedHandler()} />, cell:({row})=><input aria-label={`Select ${row.original.name}`} type="checkbox" checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()} /> },
    { accessorKey:"name", header:"Name" }, { accessorKey:"valueType", header:"Type" }, { id:"unit", header:"Unit / step", cell:({row})=>`${row.original.unit} / ${row.original.stepValue}` },
    { id:"status", header:"Status", cell:({row})=><span className={row.original.archived ? "status archived" : "status"}>{row.original.archived ? "Archived" : "Active"}</span> },
    { id:"actions", header:"", cell:({row})=><button className="link" onClick={()=>setEditing(row.original)}>Edit</button> },
  ], []);
  const table = useReactTable({ data:items, columns, state:{rowSelection:selection}, onRowSelectionChange:setSelection, getRowId:row=>String(row.id), enableRowSelection:true, getCoreRowModel:getCoreRowModel() });
  const selected = items.filter(item => selection[String(item.id)]);
  async function archive(archived:boolean) { const ids=selected.filter(item=>item.archived!==archived).map(item=>item.id); if(!ids.length)return; try { await api.archive(ids,archived);setSelection({});await onRefresh(); } catch(reason){onError(message(reason));} }
  return <section><div className="toolbar"><button onClick={()=>setEditing("new")}>New item</button><button className="secondary" disabled={!selected.some(item=>!item.archived)} onClick={()=>void archive(true)}>Archive selected</button><button className="secondary" disabled={!selected.some(item=>item.archived)} onClick={()=>void archive(false)}>Restore selected</button></div><DataTable table={table} empty={loading?"Loading...":"No record items."} />{editing && <ItemForm item={editing === "new" ? undefined : editing} onClose={()=>setEditing(null)} onSaved={async()=>{setEditing(null);await onRefresh();}} onError={onError} />}</section>;
}

function DataTable({ table, empty }: { table: ReturnType<typeof useReactTable<any>>; empty:string }) { return <div className="table-wrap"><table><thead>{table.getHeaderGroups().map(group=><tr key={group.id}>{group.headers.map(header=><th key={header.id}>{flexRender(header.column.columnDef.header,header.getContext())}</th>)}</tr>)}</thead><tbody>{table.getRowModel().rows.length ? table.getRowModel().rows.map(row=><tr key={row.id}>{row.getVisibleCells().map(cell=><td key={cell.id}>{flexRender(cell.column.columnDef.cell,cell.getContext())}</td>)}</tr>) : <tr><td colSpan={table.getAllColumns().length} className="empty">{empty}</td></tr>}</tbody></table></div>; }

function EntryForm({entry,items,onClose,onSaved,onError}:{entry:RecordEntry;items:RecordItem[];onClose:()=>void;onSaved:()=>Promise<void>;onError:(value:string|null)=>void}) { const {register,handleSubmit,formState:{isSubmitting}}=useForm<{itemId:string;occurredAt:string;localDate:string;localTime:string;value:string}>({defaultValues:{itemId:String(entry.itemId),occurredAt:toLocalDateTime(entry.occurredAt),localDate:entry.localDate,localTime:minute(entry.localMinute),value:String(entry.value)}}); return <Modal title="Edit entry" onClose={onClose}><form onSubmit={handleSubmit(async values=>{const input:EntryInput={itemId:Number(values.itemId),occurredAt:new Date(values.occurredAt).getTime(),localDate:values.localDate,localMinute:timeMinute(values.localTime),value:Number(values.value)};try{await api.saveEntry(input,entry.id);await onSaved();}catch(reason){onError(message(reason));}})}><label>Item<select {...register("itemId",{required:true})}>{items.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Occurred at<input type="datetime-local" {...register("occurredAt",{required:true})}/></label><label>Local date<input type="date" {...register("localDate",{required:true})}/></label><label>Local time<input type="time" {...register("localTime",{required:true})}/></label><label>Value<input type="number" step="any" min="0.000001" {...register("value",{required:true})}/></label><FormActions saving={isSubmitting} onClose={onClose}/></form></Modal>; }

function ItemForm({item,onClose,onSaved,onError}:{item?:RecordItem;onClose:()=>void;onSaved:()=>Promise<void>;onError:(value:string|null)=>void}) { const {register,watch,handleSubmit,formState:{isSubmitting}}=useForm<any>({defaultValues:{name:item?.name??"",description:item?.description??"",valueType:item?.valueType??"NUMBER",defaultValue:item?.defaultValue??"",stepValue:item?.stepValue??1,unit:item?.unit??"次",goalValue:item?.goalValue??"",goalPeriod:item?.goalPeriod??"DAY",priority:item?.priority??0,windows:item?.windows.map(w=>`${minute(w.startMinute)}-${minute(w.endMinute)}`).join("\n")??""}}); const valueType=watch("valueType") as ValueType; return <Modal title={item?"Edit record item":"New record item"} onClose={onClose}><form onSubmit={handleSubmit(async values=>{const input:ItemInput={name:values.name,description:values.description,valueType:values.valueType,defaultValue:numberOrNull(values.defaultValue),stepValue:Number(values.stepValue),unit:values.unit,goalValue:numberOrNull(values.goalValue),goalPeriod:values.goalValue?values.goalPeriod:null,priority:Number(values.priority),windows:parseWindows(values.windows)};try{await api.saveItem(input,item?.id);await onSaved();}catch(reason){onError(message(reason));}})}><label>Name<input {...register("name",{required:true})}/></label><label>Description<textarea {...register("description")}/></label><div className="grid"><label>Type<select {...register("valueType")}><option value="NUMBER">Number</option><option value="DURATION">Duration</option></select></label><label>Unit{valueType==="DURATION"?<select {...register("unit")}><option>秒</option><option>分</option><option>小时</option></select>:<input {...register("unit",{required:true})}/>}</label><label>Default value<input type="number" min="0" step="any" {...register("defaultValue")}/></label><label>Step<input type="number" min="0.000001" step="any" {...register("stepValue",{required:true})}/></label><label>Goal<input type="number" min="0" step="any" {...register("goalValue")}/></label><label>Goal period<select {...register("goalPeriod")}><option value="DAY">Day</option><option value="WEEK">Week</option></select></label><label>Priority<input type="number" step="1" {...register("priority",{required:true})}/></label></div><label>Suggested windows<textarea placeholder="08:00-09:00, one per line" {...register("windows")}/></label><FormActions saving={isSubmitting} onClose={onClose}/></form></Modal>; }

function Modal({title,onClose,children}:{title:string;onClose:()=>void;children:React.ReactNode}) { return <div className="modal-backdrop" role="presentation"><div className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-title"><h2>{title}</h2><button className="icon" aria-label="Close" onClick={onClose}>×</button></div>{children}</div></div>; }
function FormActions({saving,onClose}:{saving:boolean;onClose:()=>void}) { return <div className="form-actions"><button type="button" className="secondary" onClick={onClose}>Cancel</button><button disabled={saving}>{saving?"Saving...":"Save"}</button></div>; }
function minute(value:number){return `${String(Math.floor(value/60)).padStart(2,"0")}:${String(value%60).padStart(2,"0")}`;}
function timeMinute(value:string){const [hour,minuteValue]=value.split(":").map(Number);return hour*60+minuteValue;}
function toLocalDateTime(epoch:number){const date=new Date(epoch-dateOffset(epoch));return date.toISOString().slice(0,16);}
function dateOffset(epoch:number){return new Date(epoch).getTimezoneOffset()*60_000;}
function numberOrNull(value:string|number){return value===""?null:Number(value);}
function parseWindows(value:string){return value.split(/\n|,/).map(v=>v.trim()).filter(Boolean).map(window=>{const [start,end]=window.split("-");return {startMinute:timeMinute(start),endMinute:timeMinute(end)};});}
function message(reason:unknown){return reason instanceof Error?reason.message:"Request failed.";}
