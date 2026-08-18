import { useEffect, useMemo, useState } from "react";
import { Check, Edit3, Flag, Play, Save, Square } from "lucide-react";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { message } from "../lib/format";
import type { DailyPlan, TodoTask } from "../types";

export function TodayView({
  localDate,
  plan,
  tasks,
  loading,
  onChanged,
  onRefresh,
}: {
  localDate: string;
  plan: DailyPlan | null;
  tasks: TodoTask[];
  loading: boolean;
  onChanged: (message: string) => Promise<void>;
  onRefresh: () => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [ending, setEnding] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (plan?.state !== "ACTIVE") return;
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [plan?.state]);

  useEffect(() => {
    setEditing(false);
    setError(null);
  }, [plan?.revision, plan?.id]);

  const taskById = useMemo(() => new Map(tasks.map((task) => [task.id, task])), [tasks]);
  const commitments = plan
    ? [
      plan.primaryTaskId === null ? undefined : taskById.get(plan.primaryTaskId),
      plan.secondaryTaskId === null ? undefined : taskById.get(plan.secondaryTaskId),
    ].filter((task): task is TodoTask => task !== undefined)
    : [];
  const openCommitments = commitments.filter((task) => task.completedAt === null);
  const currentTask = plan?.currentTaskId === null || plan?.currentTaskId === undefined
    ? undefined
    : taskById.get(plan.currentTaskId);
  const reviewDue = plan?.state === "ACTIVE" && plan.nextReviewAt !== null && now >= plan.nextReviewAt;

  async function run(key: string, action: () => Promise<unknown>, success: string) {
    setBusy(key);
    setError(null);
    try {
      await action();
      await onChanged(success);
    } catch (reason) {
      await onRefresh();
      setError(message(reason));
    } finally {
      setBusy(null);
    }
  }

  if (loading && plan === null && tasks.length === 0) {
    return <p className="empty-state">Loading today...</p>;
  }

  if (plan === null || editing) {
    return (
      <section className="view-stack">
        <PlanEditor
          localDate={localDate}
          plan={plan}
          tasks={tasks}
          busy={busy !== null}
          onCancel={plan === null ? undefined : () => setEditing(false)}
          onSave={(input) => run(
            "plan",
            () => plan === null ? api.createDailyPlan({ localDate, ...input }) : api.updateDailyPlan(plan, input),
            plan === null ? "Today planned" : "Today updated",
          )}
        />
        {error && <p className="form-error" role="alert">{error}</p>}
      </section>
    );
  }

  return (
    <section className="view-stack">
      <div className="today-status-bar">
        <div>
          <p className="eyebrow">{formatLocalDate(localDate)}</p>
          <div className="today-title"><h2>{stateTitle(plan)}</h2><PlanBadge state={plan.state} /></div>
        </div>
        <div className="row-actions">
          {(plan.state === "READY" || plan.state === "ACTIVE") && (
            <button type="button" className="secondary" onClick={() => setEditing(true)} disabled={busy !== null}><Edit3 size={16} />Edit</button>
          )}
          {plan.state !== "ENDED" && (
            <button type="button" className="danger-secondary" onClick={() => setEnding(true)} disabled={busy !== null}><Square size={15} />End today</button>
          )}
        </div>
      </div>

      {plan.state === "READY" && (
        <div className="today-focus">
          <p className="eyebrow">First move</p>
          <h3>{plan.firstMove}</h3>
          <button type="button" onClick={() => void run("start", () => api.startDailyPlan(plan), "First round started")} disabled={busy !== null}>
            <Check size={17} />{busy === "start" ? "Starting..." : "First move done"}
          </button>
        </div>
      )}

      {plan.state === "ACTIVE" && (
        <div className="today-focus">
          <div className="today-focus-heading">
            <div><p className="eyebrow">Round {plan.completedRounds + 1}</p><h3>{currentTask?.title ?? "Current commitment"}</h3></div>
            {plan.nextReviewAt !== null && <span className={reviewDue ? "review-time due" : "review-time"}>{reviewDue ? "Review due" : `Review ${formatTime(plan.nextReviewAt)}`}</span>}
          </div>
          {currentTask && <CurrentSteps task={currentTask} busy={busy !== null} onToggle={(stepId, completed) => run(`step-${stepId}`, () => api.setStepCompleted(stepId, completed, localDate), completed ? "Subtask completed" : "Subtask reopened")} />}
          {reviewDue && (
            <div className="today-actions">
              {openCommitments.map((task) => (
                <button key={task.id} type="button" className={task.id === currentTask?.id ? undefined : "secondary"} disabled={busy !== null} onClick={() => void run(`review-${task.id}`, () => api.reviewDailyPlan(plan, task.id), task.id === currentTask?.id ? "Current commitment continued" : "Commitment switched")}>
                  <Play size={15} />{task.id === currentTask?.id ? "Continue current" : `Switch to ${task.title}`}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {plan.state === "ENOUGH" && (
        <div className="today-focus">
          <p className="eyebrow">{plan.completedRounds} rounds completed</p>
          <h3>Enough for today</h3>
          {openCommitments.length > 0 && (
            <div className="today-actions">
              {openCommitments.map((task) => (
                <button key={task.id} type="button" disabled={busy !== null} onClick={() => void run(`extra-${task.id}`, () => api.startExtraRound(plan, task.id), "Extra round started")}><Play size={15} />Start {task.title}</button>
              ))}
            </div>
          )}
        </div>
      )}

      {plan.state === "ENDED" && (
        <div className="today-focus ended-focus">
          <Flag size={20} />
          <div><h3>Today ended</h3><p>{plan.completedRounds} rounds completed{plan.endedAt === null ? "" : ` · ${formatTime(plan.endedAt)}`}</p></div>
        </div>
      )}

      <Commitments tasks={commitments} currentTaskId={plan.currentTaskId} />
      {error && <p className="form-error" role="alert">{error}</p>}
      <ConfirmDialog
        open={ending}
        title="End today"
        message="End this daily plan? It will become read-only."
        confirmLabel="End today"
        danger
        busy={busy !== null}
        onCancel={() => setEnding(false)}
        onConfirm={() => {
          setEnding(false);
          void run("end", () => api.endDailyPlan(plan), "Today ended");
        }}
      />
    </section>
  );
}

function PlanEditor({
  localDate,
  plan,
  tasks,
  busy,
  onCancel,
  onSave,
}: {
  localDate: string;
  plan: DailyPlan | null;
  tasks: TodoTask[];
  busy: boolean;
  onCancel?: () => void;
  onSave: (input: { firstMove: string; primaryTaskId: number; secondaryTaskId: number | null }) => Promise<void>;
}) {
  const [firstMove, setFirstMove] = useState(plan?.firstMove ?? "");
  const [primaryTaskId, setPrimaryTaskId] = useState(plan?.primaryTaskId ? String(plan.primaryTaskId) : "");
  const [secondaryTaskId, setSecondaryTaskId] = useState(plan?.secondaryTaskId ? String(plan.secondaryTaskId) : "");
  const primaryTask = tasks.find((task) => task.id === Number(primaryTaskId));
  const secondaryTask = tasks.find((task) => task.id === Number(secondaryTaskId));
  const primaryLocked = primaryTask?.completedAt !== null && primaryTask !== undefined;
  const secondaryLocked = secondaryTask?.completedAt !== null && secondaryTask !== undefined;
  const selectable = tasks.filter((task) => task.completedAt === null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!primaryTaskId || primaryTaskId === secondaryTaskId) return;
    await onSave({
      firstMove,
      primaryTaskId: Number(primaryTaskId),
      secondaryTaskId: secondaryTaskId ? Number(secondaryTaskId) : null,
    });
  }

  return (
    <form className="today-plan-form" onSubmit={(event) => void submit(event)}>
      <div className="section-heading">
        <div><p className="eyebrow">{formatLocalDate(localDate)}</p><h2>{plan === null ? "Plan today" : "Edit today"}</h2></div>
      </div>
      <div className="form-grid today-plan-grid">
        <label>First move<textarea required autoFocus={plan === null} value={firstMove} disabled={busy || plan?.state === "ACTIVE"} onChange={(event) => setFirstMove(event.target.value)} /></label>
        <label>Primary task<select required value={primaryTaskId} disabled={busy || primaryLocked} onChange={(event) => setPrimaryTaskId(event.target.value)}><option value="">Choose task</option>{taskOptions(tasks, selectable, secondaryTaskId, primaryTaskId)}</select></label>
        <label>Secondary task<select value={secondaryTaskId} disabled={busy || secondaryLocked} onChange={(event) => setSecondaryTaskId(event.target.value)}><option value="">No secondary task</option>{taskOptions(tasks, selectable, primaryTaskId, secondaryTaskId)}</select></label>
      </div>
      {primaryTaskId && primaryTaskId === secondaryTaskId && <p className="form-error" role="alert">Primary and secondary tasks must be different.</p>}
      {!primaryTaskId && selectable.length === 0 && <p className="form-error" role="alert">Create or reopen a task before planning today.</p>}
      <div className="modal-actions">
        {onCancel && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>}
        <button disabled={busy || !firstMove.trim() || !primaryTaskId || primaryTaskId === secondaryTaskId}><Save size={16} />{busy ? "Saving..." : "Save today"}</button>
      </div>
    </form>
  );
}

function taskOptions(tasks: TodoTask[], selectable: TodoTask[], unavailableId: string, selectedId: string) {
  const ids = new Set(selectable.map((task) => task.id));
  if (selectedId) ids.add(Number(selectedId));
  return tasks.filter((task) => ids.has(task.id) && String(task.id) !== unavailableId).map((task) => (
    <option key={task.id} value={task.id}>{task.title}{task.completedAt === null ? "" : " (Completed)"}</option>
  ));
}

function CurrentSteps({ task, busy, onToggle }: { task: TodoTask; busy: boolean; onToggle: (stepId: number, completed: boolean) => Promise<void> }) {
  return (
    <div className="today-step-list">
      {task.steps.map((step) => (
        <label key={step.id} className={step.completedAt === null ? "today-step" : "today-step completed"}>
          <input type="checkbox" checked={step.completedAt !== null} disabled={busy} onChange={(event) => void onToggle(step.id, event.target.checked)} />
          <span>{step.title}</span>
        </label>
      ))}
    </div>
  );
}

function Commitments({ tasks, currentTaskId }: { tasks: TodoTask[]; currentTaskId: number | null }) {
  return (
    <section className="today-commitments">
      <div className="section-heading"><div><p className="eyebrow">Commitments</p><h3>1 + 1</h3></div></div>
      <div className="commitment-list">
        {tasks.map((task, index) => {
          const completed = task.steps.filter((step) => step.completedAt !== null).length;
          return (
            <div className={task.id === currentTaskId ? "commitment-row current" : "commitment-row"} key={task.id}>
              <span className="step-index">{index + 1}</span>
              <div><strong>{task.title}</strong><span>{completed} / {task.steps.length} subtasks · {task.repeatMode === "DAILY" ? "Daily" : "Once"}</span></div>
              {task.completedAt !== null ? <Badge tone="muted">Completed</Badge> : task.id === currentTaskId ? <Badge tone="accent">Current</Badge> : <Badge tone="neutral">Open</Badge>}
            </div>
          );
        })}
      </div>
    </section>
  );
}

function PlanBadge({ state }: { state: DailyPlan["state"] }) {
  const values = {
    READY: { tone: "warning" as const, label: "Ready" },
    ACTIVE: { tone: "accent" as const, label: "Active" },
    ENOUGH: { tone: "neutral" as const, label: "Enough" },
    ENDED: { tone: "muted" as const, label: "Ended" },
  };
  const value = values[state];
  return <Badge tone={value.tone}>{value.label}</Badge>;
}

function stateTitle(plan: DailyPlan) {
  if (plan.state === "READY") return "First move";
  if (plan.state === "ACTIVE") return plan.nextReviewAt !== null && Date.now() >= plan.nextReviewAt ? "Hourly review" : "Current round";
  if (plan.state === "ENOUGH") return "Daily commitment complete";
  return "Daily plan closed";
}

function formatTime(value: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(value);
}

function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}
