import { useEffect, useState } from "react";
import { Check, ChevronLeft, ChevronRight, CirclePlus, Edit3, Flag, ListPlus, Play, RotateCcw, Save, Square, Trash2 } from "lucide-react";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { Modal } from "../components/Modal";
import { localToday, message, shiftLocalDate } from "../lib/format";
import type { DailyCheckin, DailyFocus, TaskTemplate, TodayState, TodayTask } from "../types";

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
  const [focusMode, setFocusMode] = useState<FocusFormMode | null>(null);
  const [ending, setEnding] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    setAddingTask(false);
    setFocusMode(null);
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

  const state = today ?? { todayTasks: [], dailySuggestions: [], unfinishedTasks: [], dailyFocus: null, dailyCheckin: null };
  const focus = state.dailyFocus;
  const openTasks = state.todayTasks.filter((task) => task.completedAt === null);

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

      <FocusSection
        focus={focus}
        tasks={state.todayTasks}
        now={now}
        busy={busy !== null}
        onPlan={() => setFocusMode("CREATE")}
        onEdit={() => setFocusMode("EDIT")}
        onRefocus={() => setFocusMode("REFOCUS")}
        onContinue={() => setFocusMode("CONTINUE")}
        onStart={() => focus && run("focus-start", () => api.startDailyFocus(focus), "Focus started")}
        onReview={(taskId) => focus && run(`focus-review-${taskId}`, () => api.reviewDailyFocusNow(focus, taskId), "Focus round completed")}
        onSwitch={(taskId) => focus && run(`focus-switch-${taskId}`, () => api.switchDailyFocusTask(focus, taskId), "Focus task switched")}
        onEnd={() => setEnding(true)}
        onReset={() => setResetting(true)}
      />

      <TasksSection
        tasks={state.todayTasks}
        focus={focus}
        busy={busy !== null}
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
      {focusMode && (
        <FocusForm
          mode={focusMode}
          localDate={localDate}
          focus={focus}
          tasks={openTasks}
          busy={busy !== null}
          onClose={() => setFocusMode(null)}
          onSave={async (input) => {
            const action = focusMode === "CREATE"
              ? () => api.createDailyFocus({ localDate, firstMove: input.firstMove, primaryTaskId: input.primaryTaskId, secondaryTaskId: input.secondaryTaskId })
              : focusMode === "EDIT" && focus
                ? () => api.updateDailyFocus(focus, input)
                : focusMode === "REFOCUS" && focus
                  ? () => api.refocusDailyFocus(focus, input.primaryTaskId, input.secondaryTaskId)
                  : focus
                    ? () => api.continueDailyFocus(focus, input.primaryTaskId, input.secondaryTaskId)
                    : null;
            if (action && await run("focus-save", action, focusMode === "CONTINUE" ? "Another Focus round started" : "Focus updated")) setFocusMode(null);
          }}
        />
      )}
      <ConfirmDialog
        open={ending}
        title="End Focus"
        message="End today's Focus? Today tasks remain available and keep their completion state."
        confirmLabel="End Focus"
        danger
        busy={busy !== null}
        onCancel={() => setEnding(false)}
        onConfirm={() => { setEnding(false); if (focus) void run("focus-end", () => api.endDailyFocus(focus), "Focus ended"); }}
      />
      <ConfirmDialog
        open={resetting}
        title="Reset Focus"
        message="Reset Focus to Ready? Today task completion remains unchanged."
        confirmLabel="Reset to Ready"
        danger
        busy={busy !== null}
        onCancel={() => setResetting(false)}
        onConfirm={() => { setResetting(false); if (focus) void run("focus-reset", () => api.resetDailyFocus(focus), "Focus reset to Ready"); }}
      />
    </section>
  );
}

function FocusSection({ focus, tasks, now, busy, onPlan, onEdit, onRefocus, onContinue, onStart, onReview, onSwitch, onEnd, onReset }: {
  focus: DailyFocus | null;
  tasks: TodayTask[];
  now: number;
  busy: boolean;
  onPlan: () => void;
  onEdit: () => void;
  onRefocus: () => void;
  onContinue: () => void;
  onStart: () => void;
  onReview: (taskId: number) => void;
  onSwitch: (taskId: number) => void;
  onEnd: () => void;
  onReset: () => void;
}) {
  if (!focus) {
    const hasOpenTasks = tasks.some((task) => task.completedAt === null);
    return <section className="today-focus"><p className="eyebrow">Focus</p><h3>No Focus planned</h3><p className="supporting-copy">Select up to two Today tasks for hourly review.</p><button type="button" disabled={!hasOpenTasks || busy} onClick={onPlan}><Flag size={16} />Plan Focus</button></section>;
  }

  const primary = tasks.find((task) => task.id === focus.primaryTaskId);
  const secondary = tasks.find((task) => task.id === focus.secondaryTaskId);
  const current = tasks.find((task) => task.id === focus.currentTaskId);
  const selectedOpen = [primary, secondary].filter((task): task is TodayTask => task !== undefined && task.completedAt === null);
  const reviewDue = focus.state === "ACTIVE" && focus.nextReviewAt !== null && now >= focus.nextReviewAt;

  return (
    <section className="today-focus">
      <div className="today-focus-heading">
        <div><p className="eyebrow">Focus</p><div className="today-title"><h3>{focusTitle(focus)}</h3><FocusBadge focus={focus} /></div></div>
        <div className="row-actions">
          {focus.state === "READY" && <button type="button" className="secondary" disabled={busy} onClick={onEdit}><Edit3 size={15} />Edit</button>}
          {focus.state !== "READY" && <button type="button" className="secondary" disabled={busy} onClick={onReset}><RotateCcw size={15} />Reset</button>}
          {focus.state !== "ENDED" && <button type="button" className="danger-secondary" disabled={busy} onClick={onEnd}><Square size={14} />End Focus</button>}
        </div>
      </div>

      {focus.state === "READY" && <><p className="supporting-copy">First move: {focus.firstMove}</p><p className="focus-selection">Primary: {primary?.title ?? "Unavailable"}{secondary ? ` · Secondary: ${secondary.title}` : ""}</p><button type="button" disabled={busy || primary?.completedAt !== null} onClick={onStart}><Check size={16} />First move done</button></>}
      {focus.state === "ACTIVE" && <><div><p className="eyebrow">Round {focus.completedRounds + 1}</p><h3>{current?.title ?? "Current Focus"}</h3></div>{focus.nextReviewAt !== null && <span className={reviewDue ? "review-time due" : "review-time"}>{reviewDue ? "Review due" : `Review ${formatTime(focus.nextReviewAt)}`}</span>}<div className="today-actions">{current && <button type="button" disabled={busy} onClick={() => onReview(current.id)}><Check size={15} />{reviewDue ? "Complete review" : "Review now"}</button>}{selectedOpen.filter((task) => task.id !== current?.id).map((task) => <span className="today-action-pair" key={task.id}><button type="button" className="secondary" disabled={busy} onClick={() => onSwitch(task.id)}><Play size={15} />Switch to {task.title}</button><button type="button" className="secondary" disabled={busy} onClick={() => onReview(task.id)}><Check size={15} />Review &amp; switch</button></span>)}</div></>}
      {focus.state === "PAUSED" && <><p className="eyebrow">{roundSummary(focus.completedRounds)}</p><p className="supporting-copy">{focus.pauseReason === "TARGET_REACHED" ? "The four-round Focus target is complete. Other Today tasks remain independent." : "The selected Focus tasks are complete. This does not mark the daily target as reached."}</p><button type="button" disabled={busy || !tasks.some((task) => task.completedAt === null)} onClick={focus.pauseReason === "TARGET_REACHED" ? onContinue : onRefocus}><Play size={15} />{focus.pauseReason === "TARGET_REACHED" ? "Continue Focus" : "Choose new Focus"}</button></>}
      {focus.state === "ENDED" && <div className="ended-focus"><Flag size={20} /><div><h3>Focus ended</h3><p>{focus.completedRounds} rounds completed{focus.endedAt === null ? "" : ` · ${formatTime(focus.endedAt)}`}</p></div></div>}
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
  if (!checkin) return <section className="today-focus"><p className="eyebrow">Hourly check-in</p><h3>Not awake yet</h3><button type="button" disabled={!isToday || busy} onClick={() => void onMarkAwake()}><Check size={16} />Mark awake</button></section>;
  const current = new Date(now);
  const firstCheckin = new Date(checkin.wokeAt);
  firstCheckin.setMinutes(0, 0, 0);
  firstCheckin.setHours(firstCheckin.getHours() + 1);
  const hour = current.getHours();
  const due = isToday && now >= firstCheckin.getTime() && !checkin.hourlyCheckins.some((entry) => entry.hour === hour);
  const next = new Date(now);
  next.setMinutes(0, 0, 0);
  next.setHours(next.getHours() + 1);
  return <section className="today-focus"><div className="today-focus-heading"><div><p className="eyebrow">Hourly check-in</p><h3>{due ? "Check in now" : `Next check-in ${next.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`}</h3></div><Badge tone="neutral">{checkin.hourlyCheckins.length} hours</Badge></div>
    {due && <><div className="today-step-list">{tasks.flatMap((task) => task.steps.map((step) => ({ task, step }))).map(({ task, step }) => <label key={step.id} className={step.completedAt === null ? "today-step" : "today-step completed"}><input type="checkbox" checked={step.completedAt !== null} disabled={busy} onChange={(event) => void onToggleStep(task, step.id, event.target.checked)} /><span><strong>{task.title}</strong> · {step.title}</span></label>)}</div><button type="button" disabled={busy} onClick={() => void onCheckIn(hour)}><Check size={16} />Check in</button></>}
  </section>;
}

function TasksSection({ tasks, focus }: {
  tasks: TodayTask[];
  focus: DailyFocus | null;
}) {
  return (
    <section className="today-tasks">
      <div className="section-heading"><div><p className="eyebrow">Tasks</p><h3>{tasks.filter((task) => task.completedAt === null).length} open · {tasks.filter((task) => task.completedAt !== null).length} completed</h3></div></div>
      {tasks.length === 0 ? <p className="empty-state">No tasks for this date.</p> : <div className="task-list">{tasks.map((task, index) => {
        const current = task.id === focus?.currentTaskId;
        const focusRole = task.id === focus?.primaryTaskId ? "Primary Focus" : task.id === focus?.secondaryTaskId ? "Secondary Focus" : null;
        const completedSteps = task.steps.filter((step) => step.completedAt !== null).length;
        const nextStep = task.steps.find((step) => step.completedAt === null);
        return <div className={current ? "task-row current" : "task-row"} key={task.id}>
          <div className="task-heading">
            <span className="step-index">{index + 1}</span>
            <div><strong className={task.completedAt === null ? undefined : "completed-text"}>{task.title}</strong><span>{`${completedSteps} / ${task.steps.length} steps`}{focusRole ? ` · ${focusRole}` : ""}{nextStep ? ` · Next: ${nextStep.title}` : " · All subtasks complete"}</span></div>
            {task.completedAt !== null ? <Badge tone="muted">Completed {formatTime(task.completedAt)}</Badge> : current ? <Badge tone="accent">Current</Badge> : focusRole ? <Badge tone="neutral">Focus</Badge> : <Badge tone="neutral">Open</Badge>}
          </div>
        </div>;
      })}</div>}
    </section>
  );
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

type FocusFormMode = "CREATE" | "EDIT" | "REFOCUS" | "CONTINUE";

function FocusForm({ mode, localDate, focus, tasks, busy, onClose, onSave }: {
  mode: FocusFormMode;
  localDate: string;
  focus: DailyFocus | null;
  tasks: TodayTask[];
  busy: boolean;
  onClose: () => void;
  onSave: (input: { firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) => Promise<void>;
}) {
  const availableIds = new Set(tasks.map((task) => task.id));
  const [firstMove, setFirstMove] = useState(focus?.firstMove ?? "");
  const [primaryId, setPrimaryId] = useState(focus && availableIds.has(focus.primaryTaskId) ? String(focus.primaryTaskId) : tasks[0] ? String(tasks[0].id) : "");
  const [secondaryId, setSecondaryId] = useState(focus?.secondaryTaskId && availableIds.has(focus.secondaryTaskId) ? String(focus.secondaryTaskId) : "");
  const needsFirstMove = mode === "CREATE" || mode === "EDIT";
  const title = mode === "CREATE" ? "Plan Focus" : mode === "EDIT" ? "Edit Focus" : mode === "REFOCUS" ? "Choose new Focus" : "Continue Focus";

  return <Modal title={title} onClose={onClose}><form className="item-form" onSubmit={(event) => { event.preventDefault(); if (primaryId && primaryId !== secondaryId && (!needsFirstMove || firstMove.trim())) void onSave({ firstMove: needsFirstMove ? firstMove : focus?.firstMove ?? "", primaryTaskId: Number(primaryId), secondaryTaskId: secondaryId ? Number(secondaryId) : null }); }}>{needsFirstMove && <label>First move<textarea required autoFocus value={firstMove} disabled={busy} onChange={(event) => setFirstMove(event.target.value)} /></label>}<label>Primary Focus<select required value={primaryId} disabled={busy} onChange={(event) => setPrimaryId(event.target.value)}><option value="">Choose task</option>{taskOptions(tasks, secondaryId)}</select></label><label>Secondary Focus<select value={secondaryId} disabled={busy} onChange={(event) => setSecondaryId(event.target.value)}><option value="">No secondary task</option>{taskOptions(tasks, primaryId)}</select></label><p className="supporting-copy">{formatLocalDate(localDate)} · Only Focus tasks participate in hourly reviews.</p><div className="modal-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>Cancel</button><button disabled={busy || !primaryId || primaryId === secondaryId || (needsFirstMove && !firstMove.trim())}><Save size={16} />{mode === "CONTINUE" ? "Continue Focus" : "Save Focus"}</button></div></form></Modal>;
}

function taskOptions(tasks: TodayTask[], unavailableId: string) {
  return tasks.filter((task) => String(task.id) !== unavailableId).map((task) => <option key={task.id} value={task.id}>{task.title}{task.steps.length > 0 ? ` · ${task.steps.length} steps` : ""}</option>);
}

function DateNavigator({ localDate, onChange }: { localDate: string; onChange: (localDate: string) => void }) {
  const today = localToday();
  return <div className="today-date-toolbar"><button type="button" className="icon-button" aria-label="Previous date" title="Previous date" onClick={() => onChange(shiftLocalDate(localDate, -1))}><ChevronLeft size={18} /></button><label className="today-date-input"><input type="date" aria-label="Today date" value={localDate} onChange={(event) => { if (event.target.value) onChange(event.target.value); }} /></label><button type="button" className="icon-button" aria-label="Next date" title="Next date" onClick={() => onChange(shiftLocalDate(localDate, 1))}><ChevronRight size={18} /></button><button type="button" className="secondary compact" disabled={localDate === today} onClick={() => onChange(today)}>Today</button></div>;
}

function FocusBadge({ focus }: { focus: DailyFocus }) {
  const value = focus.state === "READY" ? { tone: "warning" as const, label: "Ready" } : focus.state === "ACTIVE" ? { tone: "accent" as const, label: "Active" } : focus.state === "ENDED" ? { tone: "muted" as const, label: "Ended" } : focus.pauseReason === "TARGET_REACHED" ? { tone: "neutral" as const, label: "Target reached" } : { tone: "neutral" as const, label: "Focus cleared" };
  return <Badge tone={value.tone}>{value.label}</Badge>;
}

function focusTitle(focus: DailyFocus) {
  if (focus.state === "READY") return "First move";
  if (focus.state === "ACTIVE") return "Current round";
  if (focus.state === "ENDED") return "Focus ended";
  return focus.pauseReason === "TARGET_REACHED" ? "Today's Focus is enough" : "Selected Focus completed";
}

function roundSummary(rounds: number) {
  return rounds === 0 ? "Before the first review" : `${rounds} ${rounds === 1 ? "round" : "rounds"} completed`;
}

function formatTime(value: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(value);
}

function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}
