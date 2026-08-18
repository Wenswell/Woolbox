import { Activity, ArrowRight, CircleCheck, Gauge, ListTodo, Ruler, Sigma } from "lucide-react";
import { actionLabel, actionTone, auditSubject } from "../lib/audit";
import { formatDateTime } from "../lib/format";
import type { RecordAuditLog, RecordEntry, RecordItem, TodoTask } from "../types";
import { Badge } from "../components/Badge";

type Destination = "tasks" | "accumulate" | "measurement" | "entries" | "activity";

export function OverviewView({
  tasks,
  items,
  entries,
  logs,
  loading,
  onNavigate,
}: {
  tasks: TodoTask[];
  items: RecordItem[];
  entries: RecordEntry[];
  logs: RecordAuditLog[];
  loading: boolean;
  onNavigate: (view: Destination) => void;
}) {
  const openTasks = tasks.filter((task) => task.completedAt === null);
  const activeItems = items.filter((item) => !item.archived);
  const latestEntries = entries.slice(0, 6);
  const nextTasks = [...openTasks].sort((left, right) => left.title.localeCompare(right.title)).slice(0, 5);

  return (
    <section className="overview-stack">
      <div className="metric-grid" aria-label="Records summary">
        <Metric icon={<ListTodo size={18} />} label="Open tasks" value={openTasks.length} detail="Available task inventory" />
        <Metric icon={<CircleCheck size={18} />} label="Completed tasks" value={tasks.length - openTasks.length} detail={`${tasks.length} total tasks`} />
        <Metric icon={<Sigma size={18} />} label="Accumulate" value={activeItems.filter((item) => item.mode === "ACCUMULATE").length} detail="Active record items" />
        <Metric icon={<Ruler size={18} />} label="Measurement" value={activeItems.filter((item) => item.mode === "MEASUREMENT").length} detail="Active record items" />
        <Metric icon={<Gauge size={18} />} label="30-day entries" value={entries.length} detail="Across active and archived items" />
      </div>

      <div className="overview-grid">
        <section className="overview-section">
          <div className="section-heading">
            <div><p className="eyebrow">Task inventory</p><h2>Open tasks</h2></div>
            <button type="button" className="text-button" onClick={() => onNavigate("tasks")}>Manage tasks<ArrowRight size={15} /></button>
          </div>
          <div className="overview-list">
            {loading && !nextTasks.length ? <p className="empty-state">Loading tasks…</p> : nextTasks.length ? nextTasks.map((task) => (
              <button type="button" className="overview-row" key={task.id} onClick={() => onNavigate("tasks")}>
                <span className="record-icon task-icon"><ListTodo size={17} /></span>
                <span className="overview-row-copy"><strong>{task.title}</strong><span>{task.steps.filter((step) => step.completedAt !== null).length} of {task.steps.length} subtasks</span></span>
                <ArrowRight size={16} />
              </button>
            )) : <p className="empty-state">No open tasks.</p>}
          </div>
        </section>

        <section className="overview-section">
          <div className="section-heading">
            <div><p className="eyebrow">Last 30 days</p><h2>Recent entries</h2></div>
            <button type="button" className="text-button" onClick={() => onNavigate("entries")}>View entries<ArrowRight size={15} /></button>
          </div>
          <div className="overview-list">
            {loading && !latestEntries.length ? <p className="empty-state">Loading entries…</p> : latestEntries.length ? latestEntries.map((entry) => (
              <button type="button" className="overview-row" key={entry.id} onClick={() => onNavigate("entries")}>
                <span className={`record-icon ${entry.itemMode === "MEASUREMENT" ? "measurement-icon" : "accumulate-icon"}`}>{entry.itemMode === "MEASUREMENT" ? <Ruler size={17} /> : <Sigma size={17} />}</span>
                <span className="overview-row-copy"><strong>{entry.itemName}</strong><span>{entry.localDate} · {entry.value} {entry.itemUnit}</span></span>
                <ArrowRight size={16} />
              </button>
            )) : <p className="empty-state">No entries in the last 30 days.</p>}
          </div>
        </section>
      </div>

      <section className="overview-section activity-preview">
        <div className="section-heading">
          <div><p className="eyebrow">Audit trail</p><h2>Recent activity</h2></div>
          <button type="button" className="text-button" onClick={() => onNavigate("activity")}>Open activity<ArrowRight size={15} /></button>
        </div>
        <div className="activity-strip">
          {logs.slice(0, 5).map((log) => (
            <div className="activity-item" key={log.id}>
              <span className="record-icon activity-icon"><Activity size={16} /></span>
              <span className="activity-copy"><strong>{auditSubject(log)}</strong><span>{formatDateTime(log.createdAt)}</span></span>
              <Badge tone={actionTone(log.action)}>{actionLabel(log.action)}</Badge>
            </div>
          ))}
          {!logs.length && <p className="empty-state">No activity recorded.</p>}
        </div>
      </section>
    </section>
  );
}

function Metric({ icon, label, value, detail, tone = "default" }: { icon: React.ReactNode; label: string; value: number; detail: string; tone?: "default" | "danger" }) {
  return (
    <article className={`metric-card metric-${tone}`}>
      <div className="metric-label"><span>{icon}</span>{label}</div>
      <strong className="metric-value">{value}</strong>
      <span className="metric-detail">{detail}</span>
    </article>
  );
}
