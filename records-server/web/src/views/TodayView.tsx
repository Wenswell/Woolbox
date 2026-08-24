import { useEffect, useState } from "react";
import { Check, ChevronLeft, ChevronRight, CirclePlus, ListPlus, Save, Trash2 } from "lucide-react";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { Modal } from "../components/Modal";
import { localToday, message, shiftLocalDate } from "../lib/format";
import type { DailyCheckin, TaskTemplate, TodayState, TodayTask } from "../types";

export function TodayView({
  localDate,
  today,
  templates,
  loading,
  onDateChange,
  onChanged,
  onRefresh,
}: {
  localDate: string;
  today: TodayState | null;
  templates: TaskTemplate[];
  loading: boolean;
  onDateChange: (localDate: string) => void;
  onChanged: (message: string) => Promise<void>;
  onRefresh: () => Promise<void>;
}) {
  const [addingTask, setAddingTask] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    setAddingTask(false);
    setError(null);
  }, [localDate]);

  async function run(key: string, action: () => Promise<unknown>, success: string) {
    setBusy(key);
    setError(null);
    try {
      await action();
      await onChanged(success);
      return true;
    } catch (reason) {
      await onRefresh();
      setError(message(reason));
      return false;
    } finally {
      setBusy(null);
    }
  }

  if (loading && today === null) {
    return <section className="view-stack"><DateNavigator localDate={localDate} onChange={onDateChange} /><p className="empty-state">Loading selected date...</p></section>;
  }

  const state = today ?? { todayTasks: [], dailySuggestions: [], unfinishedTasks: [], dailyCheckin: null };

  return (
    <section className="view-stack">
      <DateNavigator localDate={localDate} onChange={onDateChange} />
      <div className="today-status-bar">
        <div><p className="eyebrow">{formatLocalDate(localDate)}</p><div className="today-title"><h2>Today</h2><Badge tone="neutral">{state.todayTasks.length} tasks</Badge></div></div>
        <button type="button" onClick={() => setAddingTask(true)} disabled={busy !== null}><CirclePlus size={17} />Add task</button>
      </div>

      <HourlyCheckinSection
        checkin={state.dailyCheckin}
        tasks={state.todayTasks}
        localDate={localDate}
        now={now}
        busy={busy !== null}
        onMarkAwake={() => run("mark-awake", () => api.markAwake(localDate), "Wake-up marked")}
        onToggleStep={(task, stepId, completed) => run(`task-step-${stepId}`, () => api.setTodayTaskStepCompleted(stepId, task.revision, completed), completed ? "Subtask completed" : "Subtask reopened")}
        onCheckIn={(hour) => run(`hourly-checkin-${hour}`, () => api.markHourlyCheckin(localDate, hour), "Hourly check-in saved")}
      />

      <TasksSection
        tasks={state.todayTasks}
        busy={busy !== null}
        onToggleStep={(task, stepId, completed) => run(`task-step-${stepId}`, () => api.setTodayTaskStepCompleted(stepId, task.revision, completed), completed ? "Subtask completed" : "Subtask reopened")}
      />

      {error && <p className="form-error" role="alert">{error}</p>}
      {addingTask && (
        <AddTaskDialog
          localDate={localDate}
          today={state}
          templates={templates.filter((template) => !template.archived)}
          busy={busy !== null}
          onClose={() => setAddingTask(false)}
          onAdd={async (key, action) => {
            if (await run(key, action, "Task added to Today")) setAddingTask(false);
          }}
        />
      )}
    </section>
  );
}

function HourlyCheckinSection({ checkin, tasks, localDate, now, busy, onMarkAwake, onToggleStep, onCheckIn }: {
  checkin: DailyCheckin | null;
  tasks: TodayTask[];
  localDate: string;
  now: number;
  busy: boolean;
  onMarkAwake: () => Promise<unknown>;
  onToggleStep: (task: TodayTask, stepId: number, completed: boolean) => Promise<unknown>;
  onCheckIn: (hour: number) => Promise<unknown>;
}) {
  const isToday = localDate === localToday();
  if (!checkin) {
    return <section className="today-panel"><p className="eyebrow">Hourly check-in</p><h3>Not awake yet</h3><button type="button" disabled={!isToday || busy} onClick={() => void onMarkAwake()}><Check size={16} />Mark awake</button></section>;
  }

  const current = new Date(now);
  const firstCheckin = new Date(checkin.wokeAt);
  firstCheckin.setMinutes(0, 0, 0);
  firstCheckin.setHours(firstCheckin.getHours() + 1);
  const hour = current.getHours();
  const due = isToday && now >= firstCheckin.getTime() && !checkin.hourlyCheckins.some((entry) => entry.hour === hour);
  const next = new Date(now);
  next.setMinutes(0, 0, 0);
  next.setHours(next.getHours() + 1);

  return <section className="today-panel"><div className="today-panel-heading"><div><p className="eyebrow">Hourly check-in</p><h3>{due ? "Check in now" : isToday ? `Next check-in ${formatTime(next.getTime())}` : "Check-ins recorded"}</h3></div><Badge tone="neutral">{checkin.hourlyCheckins.length} hours</Badge></div>
    {due && <><div className="today-step-list">{tasks.flatMap((task) => task.steps.map((step) => ({ task, step }))).map(({ task, step }) => <StepToggle key={step.id} task={task} stepId={step.id} title={step.title} completed={step.completedAt !== null} busy={busy} onToggle={onToggleStep} />)}</div><button type="button" disabled={busy} onClick={() => void onCheckIn(hour)}><Check size={16} />Check in</button></>}
  </section>;
}

function TasksSection({ tasks, busy, onToggleStep }: {
  tasks: TodayTask[];
  busy: boolean;
  onToggleStep: (task: TodayTask, stepId: number, completed: boolean) => Promise<unknown>;
}) {
  return (
    <section className="today-tasks">
      <div className="section-heading"><div><p className="eyebrow">Tasks</p><h3>{tasks.filter((task) => task.completedAt === null).length} open · {tasks.filter((task) => task.completedAt !== null).length} completed</h3></div></div>
      {tasks.length === 0 ? <p className="empty-state">No tasks for this date.</p> : <div className="task-list">{tasks.map((task, index) => {
        const completedSteps = task.steps.filter((step) => step.completedAt !== null).length;
        const nextStep = task.steps.find((step) => step.completedAt === null);
        return <div className="task-row" key={task.id}>
          <div className="task-heading">
            <span className="step-index">{index + 1}</span>
            <div><strong className={task.completedAt === null ? undefined : "completed-text"}>{task.title}</strong><span>{`${completedSteps} / ${task.steps.length} steps`}{nextStep ? ` · Next: ${nextStep.title}` : " · All subtasks complete"}</span></div>
            <Badge tone={task.completedAt === null ? "neutral" : "muted"}>{task.completedAt === null ? "Open" : `Completed ${formatTime(task.completedAt)}`}</Badge>
          </div>
          <div className="today-step-list">{task.steps.map((step) => <StepToggle key={step.id} task={task} stepId={step.id} title={step.title} completed={step.completedAt !== null} busy={busy} onToggle={onToggleStep} />)}</div>
        </div>;
      })}</div>}
    </section>
  );
}

function StepToggle({ task, stepId, title, completed, busy, onToggle }: {
  task: TodayTask;
  stepId: number;
  title: string;
  completed: boolean;
  busy: boolean;
  onToggle: (task: TodayTask, stepId: number, completed: boolean) => Promise<unknown>;
}) {
  return <label className={completed ? "today-step completed" : "today-step"}><input type="checkbox" checked={completed} disabled={busy} onChange={(event) => void onToggle(task, stepId, event.target.checked)} /><span><strong>{task.title}</strong> · {title}</span></label>;
}

type AddMode = "RECOMMENDED" | "TEMPLATES" | "NEW";

function AddTaskDialog({ localDate, today, templates, busy, onClose, onAdd }: {
  localDate: string;
  today: TodayState;
  templates: TaskTemplate[];
  busy: boolean;
  onClose: () => void;
  onAdd: (key: string, action: () => Promise<unknown>) => Promise<void>;
}) {
  const [mode, setMode] = useState<AddMode>(today.dailySuggestions.length > 0 || today.unfinishedTasks.length > 0 ? "RECOMMENDED" : "NEW");
  const [title, setTitle] = useState("");
  const [steps, setSteps] = useState<string[]>([""]);
  const recommended = [...today.dailySuggestions.map((template) => ({ key: `template-${template.id}`, title: template.title, detail: "Daily template", action: () => api.createTodayTaskFromTemplate(localDate, template.id) })), ...today.unfinishedTasks.map((task) => ({ key: `task-${task.id}`, title: task.title, detail: "Unfinished yesterday", action: () => api.createTodayTaskFromTask(localDate, task.id) }))];

  return (
    <Modal title="Add task" onClose={onClose} size="lg">
      <div className="segmented" role="group" aria-label="Task source"><button type="button" className={mode === "RECOMMENDED" ? "active" : undefined} onClick={() => setMode("RECOMMENDED")}>Recommended</button><button type="button" className={mode === "TEMPLATES" ? "active" : undefined} onClick={() => setMode("TEMPLATES")}>Templates</button><button type="button" className={mode === "NEW" ? "active" : undefined} onClick={() => setMode("NEW")}>New task</button></div>
      {mode === "RECOMMENDED" && <div className="task-source-list">{recommended.length === 0 ? <p className="empty-state">No recommendations for this date.</p> : recommended.map((item) => <button type="button" className="task-source-row" key={item.key} disabled={busy} onClick={() => void onAdd(`add-${item.key}`, item.action)}><span><strong>{item.title}</strong><small>{item.detail}</small></span><ListPlus size={17} /></button>)}</div>}
      {mode === "TEMPLATES" && <div className="task-source-list">{templates.length === 0 ? <p className="empty-state">No active templates.</p> : templates.map((template) => <button type="button" className="task-source-row" key={template.id} disabled={busy} onClick={() => void onAdd(`add-template-${template.id}`, () => api.createTodayTaskFromTemplate(localDate, template.id))}><span><strong>{template.title}</strong><small>{template.cadence === "DAILY" ? "Daily recommendation" : "Manual"} · {template.steps.length} steps</small></span><ListPlus size={17} /></button>)}</div>}
      {mode === "NEW" && <form className="item-form" onSubmit={(event) => { event.preventDefault(); if (title.trim() && steps.length > 0 && steps.every((step) => step.trim())) void onAdd("add-new", () => api.createTodayTask({ localDate, title, steps: steps.map((step) => ({ title: step })) })); }}><label>Title<input autoFocus required value={title} disabled={busy} onChange={(event) => setTitle(event.target.value)} /></label><section className="form-section"><div className="section-heading"><div><p className="eyebrow">Subtasks</p><h3>Checklist</h3></div><button type="button" className="secondary compact" disabled={busy} onClick={() => setSteps((current) => [...current, ""])}><CirclePlus size={15} />Add step</button></div><div className="step-editor-list">{steps.map((step, index) => <div className="template-step-row" key={index}><span className="step-index">{index + 1}</span><input aria-label={`Task step ${index + 1}`} required value={step} onChange={(event) => setSteps((current) => current.map((value, itemIndex) => itemIndex === index ? event.target.value : value))} /><button type="button" className="icon-button danger-icon" disabled={steps.length === 1} aria-label={`Remove task step ${index + 1}`} onClick={() => setSteps((current) => current.filter((_, itemIndex) => itemIndex !== index))}><Trash2 size={14} /></button></div>)}</div></section><div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Cancel</button><button disabled={busy || !title.trim() || steps.length === 0 || steps.some((step) => !step.trim())}><Save size={16} />Add task</button></div></form>}
    </Modal>
  );
}

function DateNavigator({ localDate, onChange }: { localDate: string; onChange: (localDate: string) => void }) {
  const today = localToday();
  return <div className="today-date-toolbar"><button type="button" className="icon-button" aria-label="Previous date" title="Previous date" onClick={() => onChange(shiftLocalDate(localDate, -1))}><ChevronLeft size={18} /></button><label className="today-date-input"><input type="date" aria-label="Today date" value={localDate} onChange={(event) => { if (event.target.value) onChange(event.target.value); }} /></label><button type="button" className="icon-button" aria-label="Next date" title="Next date" onClick={() => onChange(shiftLocalDate(localDate, 1))}><ChevronRight size={18} /></button><button type="button" className="secondary compact" disabled={localDate === today} onClick={() => onChange(today)}>Today</button></div>;
}

function formatTime(value: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(value);
}

function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}
