import { Activity, ArrowRight, CircleCheck, Gauge, ListTodo, Ruler, Sigma } from "lucide-react";
import { actionLabel, actionTone, auditSubject } from "../lib/audit";
import { formatDateTime } from "../lib/format";
import type { RecordAuditLog, RecordEntry, RecordItem, TaskTemplate } from "../types";
import { Badge } from "../components/Badge";

type Destination = "templates" | "accumulate" | "measurement" | "entries" | "activity";

export function OverviewView({
  templates,
  items,
  entries,
  logs,
  loading,
  onNavigate,
}: {
  templates: TaskTemplate[];
  items: RecordItem[];
  entries: RecordEntry[];
  logs: RecordAuditLog[];
  loading: boolean;
  onNavigate: (view: Destination) => void;
}) {
  const activeItems = items.filter((item) => !item.archived);
  const latestEntries = entries.slice(0, 6);
  const nextTemplates = templates.slice(0, 5);

  return (
    <section className="overview-stack">
      <div className="metric-grid" aria-label="Records summary">
        <Metric icon={<ListTodo size={18} />} label="Daily templates" value={templates.filter((template) => template.cadence === "DAILY").length} detail="Recommended first in Today" />
        <Metric icon={<CircleCheck size={18} />} label="Manual templates" value={templates.filter((template) => template.cadence === "MANUAL").length} detail={`${templates.length} active templates`} />
        <Metric icon={<Sigma size={18} />} label="Accumulate" value={activeItems.filter((item) => item.mode === "ACCUMULATE").length} detail="Active record items" />
        <Metric icon={<Ruler size={18} />} label="Measurement" value={activeItems.filter((item) => item.mode === "MEASUREMENT").length} detail="Active record items" />
        <Metric icon={<Gauge size={18} />} label="30-day entries" value={entries.length} detail="Across active and archived items" />
      </div>

      <div className="overview-grid">
        <section className="overview-section">
          <div className="section-heading">
            <div><p className="eyebrow">Reusable definitions</p><h2>Templates</h2></div>
            <button type="button" className="text-button" onClick={() => onNavigate("templates")}>Manage templates<ArrowRight size={15} /></button>
          </div>
          <div className="overview-list">
            {loading && !nextTemplates.length ? <p className="empty-state">Loading templates...</p> : nextTemplates.length ? nextTemplates.map((template) => (
              <button type="button" className="overview-row" key={template.id} onClick={() => onNavigate("templates")}>
                <span className="record-icon task-icon"><ListTodo size={17} /></span>
                <span className="overview-row-copy"><strong>{template.title}</strong><span>{template.steps.length} steps · {template.cadence === "DAILY" ? "Daily" : "Manual"}</span></span>
                <ArrowRight size={16} />
              </button>
            )) : <p className="empty-state">No active templates.</p>}
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
