import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, CalendarCheck, ClipboardList, LayoutDashboard, ListTodo, RefreshCw, Ruler, Sigma } from "lucide-react";
import { api } from "./api";
import { Toast } from "./components/Toast";
import { localToday, message, shiftLocalDate } from "./lib/format";
import type { DailyPlan, RecordAuditLog, RecordEntry, RecordItem, TodoTask } from "./types";
import { AuditLogView } from "./views/AuditLogView";
import { EntriesView, type EntryFilters } from "./views/EntriesView";
import { OverviewView } from "./views/OverviewView";
import { RecordItemsView } from "./views/RecordItemsView";
import { TasksView } from "./views/TasksView";
import { TodayView } from "./views/TodayView";

type View = "overview" | "today" | "tasks" | "accumulate" | "measurement" | "entries" | "activity";
const VIEWS: Record<View, { label: string; description: string }> = {
  overview: { label: "Overview", description: "Records status and recent work" },
  today: { label: "Today", description: "Daily commitments and hourly review" },
  tasks: { label: "Tasks", description: "Open outcomes and ordered subtasks" },
  accumulate: { label: "Accumulate", description: "Counters, durations and rolling goals" },
  measurement: { label: "Measurement", description: "Bounded numerical measurements" },
  entries: { label: "Entries", description: "Recorded values across every tracker" },
  activity: { label: "Activity", description: "Immutable audit history for every change" },
};
const NAVIGATION = [
  { title: "Workspace", items: [{ id: "overview" as const, icon: LayoutDashboard }, { id: "today" as const, icon: CalendarCheck }, { id: "tasks" as const, icon: ListTodo }] },
  { title: "Records", items: [{ id: "accumulate" as const, icon: Sigma }, { id: "measurement" as const, icon: Ruler }, { id: "entries" as const, icon: ClipboardList }] },
  { title: "System", items: [{ id: "activity" as const, icon: Activity }] },
];

export function App() {
  const [view, setView] = useState<View>(() => viewFromHash());
  const [activeItems, setActiveItems] = useState<RecordItem[]>([]);
  const [archivedItems, setArchivedItems] = useState<RecordItem[]>([]);
  const [tasks, setTasks] = useState<TodoTask[]>([]);
  const [todayDate, setTodayDate] = useState(localToday);
  const [todayTasks, setTodayTasks] = useState<TodoTask[]>([]);
  const [dailyPlan, setDailyPlan] = useState<DailyPlan | null>(null);
  const [entries, setEntries] = useState<RecordEntry[]>([]);
  const [logs, setLogs] = useState<RecordAuditLog[]>([]);
  const [nextLogId, setNextLogId] = useState<number | null>(null);
  const [filters, setFilters] = useState<EntryFilters>(() => {
    const today = localToday();
    return { startDate: shiftLocalDate(today, -29), endDate: today, itemId: "", mode: "" };
  });
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const allItems = useMemo(() => [...activeItems, ...archivedItems], [activeItems, archivedItems]);

  const loadLogs = useCallback(async (beforeId?: number) => {
    const page = await api.auditLogs(beforeId);
    setLogs((current) => beforeId === undefined ? page.logs : [...current, ...page.logs]);
    setNextLogId(page.nextBeforeId);
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const today = localToday();
      const taskListRequest = api.tasks(today);
      const todayTaskListRequest = todayDate === today ? taskListRequest : api.tasks(todayDate);
      const [active, archived, taskList, todayTaskList, plan, entryList, auditPage] = await Promise.all([
        api.items(false),
        api.items(true),
        taskListRequest,
        todayTaskListRequest,
        api.dailyPlan(todayDate),
        api.entries(filters.startDate, filters.endDate, filters.itemId, filters.mode || undefined),
        api.auditLogs(),
      ]);
      setActiveItems(active);
      setArchivedItems(archived);
      setTasks(taskList);
      setTodayTasks(todayTaskList);
      setDailyPlan(plan);
      setEntries(entryList);
      setLogs(auditPage.logs);
      setNextLogId(auditPage.nextBeforeId);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }, [filters, todayDate]);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    document.title = `${VIEWS[view].label} · Records`;
    window.history.replaceState(null, "", `#${view}`);
  }, [view]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  async function changed(success: string) {
    setToast(success);
    await refresh();
  }

  function changeTodayDate(value: string) {
    setLoading(true);
    setDailyPlan(null);
    setTodayTasks([]);
    setTodayDate(value);
  }

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">R</span><div><strong>Records</strong><span>Administration</span></div></div>
        <nav className="primary-nav" aria-label="Primary navigation">
          {NAVIGATION.map((group) => (
            <div className="nav-group" key={group.title}>
              <p>{group.title}</p>
              <div>
                {group.items.map((item) => {
                  const Icon = item.icon;
                  return <button key={item.id} type="button" className={view === item.id ? "active" : undefined} aria-current={view === item.id ? "page" : undefined} onClick={() => setView(item.id)}><Icon size={17} />{VIEWS[item.id].label}</button>;
                })}
              </div>
            </div>
          ))}
        </nav>
        <div className="sidebar-footer"><span className={error ? "status-dot error-dot" : "status-dot"} /><span>{error ? "Connection issue" : "Server connected"}</span></div>
      </aside>

      <div className="content-shell">
        <header className="page-header">
          <div><p className="eyebrow">Records administration</p><h1>{VIEWS[view].label}</h1><p className="page-description">{VIEWS[view].description}</p></div>
          <button type="button" className="icon-button header-action" aria-label="Refresh current view" title="Refresh" onClick={() => void refresh()} disabled={loading}><RefreshCw size={18} className={loading ? "spin" : undefined} /></button>
        </header>

        {error && <div className="error-banner" role="alert"><span><strong>Could not refresh data.</strong>{error}</span><button type="button" className="secondary compact" onClick={() => void refresh()}>Retry</button></div>}

        <main className="page-content">
          {view === "overview" && <OverviewView tasks={tasks} items={allItems} entries={entries} logs={logs} loading={loading} onNavigate={setView} />}
          {view === "today" && <TodayView localDate={todayDate} plan={dailyPlan} tasks={todayTasks} loading={loading} onDateChange={changeTodayDate} onChanged={changed} onRefresh={refresh} />}
          {view === "tasks" && <TasksView localDate={localToday()} tasks={tasks} loading={loading} onChanged={changed} onError={setError} />}
          {view === "accumulate" && <RecordItemsView mode="ACCUMULATE" items={allItems} loading={loading} onChanged={changed} onError={setError} />}
          {view === "measurement" && <RecordItemsView mode="MEASUREMENT" items={allItems} loading={loading} onChanged={changed} onError={setError} />}
          {view === "entries" && <EntriesView entries={entries} items={allItems} filters={filters} loading={loading} onFilters={setFilters} onChanged={changed} onError={setError} />}
          {view === "activity" && <AuditLogView logs={logs} loading={loading} nextBeforeId={nextLogId} onLoadMore={() => { if (nextLogId !== null) void loadLogs(nextLogId); }} />}
        </main>
      </div>
      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}

function viewFromHash(): View {
  const value = window.location.hash.slice(1);
  return value in VIEWS ? value as View : "overview";
}
