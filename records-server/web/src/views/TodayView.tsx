import { useEffect, useState } from "react";
import { Check, ChevronLeft, ChevronRight, Edit3, Flag, Play, Plus, RotateCcw, Save, Square } from "lucide-react";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { Modal } from "../components/Modal";
import { localToday, message, shiftLocalDate } from "../lib/format";
import type { DailyPlan, TaskTemplate, TodayCommitment } from "../types";
import { TemplateForm } from "./TemplatesView";

export function TodayView({
  localDate,
  plan,
  templates,
  loading,
  onDateChange,
  onChanged,
  onRefresh,
}: {
  localDate: string;
  plan: DailyPlan | null;
  templates: TaskTemplate[];
  loading: boolean;
  onDateChange: (localDate: string) => void;
  onChanged: (message: string) => Promise<void>;
  onRefresh: () => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [ending, setEnding] = useState(false);
  const [resetting, setResetting] = useState(false);
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
    setAdding(false);
    setError(null);
  }, [localDate, plan?.revision, plan?.id]);

  const openCommitments = plan?.commitments.filter((commitment) => commitment.completedAt === null) ?? [];
  const currentCommitment = plan?.commitments.find((commitment) => commitment.id === plan.currentCommitmentId);
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

  if (loading && plan === null && templates.length === 0) {
    return <section className="view-stack"><DateNavigator localDate={localDate} onChange={onDateChange} /><p className="empty-state">Loading selected date...</p></section>;
  }

  if (plan === null || editing) {
    return (
      <section className="view-stack">
        <DateNavigator localDate={localDate} onChange={onDateChange} />
        <PlanEditor
          localDate={localDate}
          plan={plan}
          templates={templates.filter((template) => !template.archived)}
          busy={busy !== null}
          onCancel={plan === null ? undefined : () => setEditing(false)}
          onTemplatesChanged={onChanged}
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
      <DateNavigator localDate={localDate} onChange={onDateChange} />
      <div className="today-status-bar">
        <div>
          <p className="eyebrow">{formatLocalDate(localDate)}</p>
          <div className="today-title"><h2>{stateTitle(plan)}</h2><PlanBadge plan={plan} /></div>
        </div>
        <div className="row-actions">
          {plan.state === "READY" && <button type="button" className="secondary" onClick={() => setEditing(true)} disabled={busy !== null}><Edit3 size={16} />Edit</button>}
          {plan.state !== "READY" && <button type="button" className="secondary" onClick={() => setResetting(true)} disabled={busy !== null}><RotateCcw size={16} />Reset</button>}
          {plan.state !== "ENDED" && <button type="button" className="danger-secondary" onClick={() => setEnding(true)} disabled={busy !== null}><Square size={15} />End today</button>}
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
            <div><p className="eyebrow">Round {plan.completedRounds + 1}</p><h3>{currentCommitment?.title ?? "Current commitment"}</h3></div>
            {plan.nextReviewAt !== null && <span className={reviewDue ? "review-time due" : "review-time"}>{reviewDue ? "Review due" : `Review ${formatTime(plan.nextReviewAt)}`}</span>}
          </div>
          <div className="today-actions">
            {currentCommitment && <button type="button" disabled={busy !== null} onClick={() => void run(`review-${currentCommitment.id}`, () => api.reviewDailyPlanNow(plan, currentCommitment.id), "Round completed")}><Check size={15} />{reviewDue ? "Complete review" : "Review now"}</button>}
            {openCommitments.filter((commitment) => commitment.id !== currentCommitment?.id).map((commitment) => (
              <span className="today-action-pair" key={commitment.id}>
                <button type="button" className="secondary" disabled={busy !== null} onClick={() => void run(`switch-${commitment.id}`, () => api.switchDailyPlanCommitment(plan, commitment.id), "Current commitment switched")}><Play size={15} />Switch to {commitment.title}</button>
                <button type="button" className="secondary" disabled={busy !== null} onClick={() => void run(`review-${commitment.id}`, () => api.reviewDailyPlanNow(plan, commitment.id), "Round completed and commitment switched")}><Check size={15} />Review &amp; switch</button>
              </span>
            ))}
          </div>
        </div>
      )}

      {plan.state === "PAUSED" && (
        <div className="today-focus">
          <p className="eyebrow">{roundSummary(plan.completedRounds)}</p>
          <h3>{plan.pauseReason === "COMMITMENTS_COMPLETED" ? "Today's commitments completed" : "Daily target reached"}</h3>
          {plan.pauseReason === "COMMITMENTS_COMPLETED" && <button type="button" disabled={busy !== null} onClick={() => setAdding(true)}><Plus size={15} />Add commitment</button>}
          {plan.pauseReason === "TARGET_REACHED" && openCommitments.length > 0 && (
            <div className="today-actions">
              {openCommitments.map((commitment) => <button key={commitment.id} type="button" disabled={busy !== null} onClick={() => void run(`continue-${commitment.id}`, () => api.continueDailyPlan(plan, commitment.id), "Another round started")}><Play size={15} />Continue one more round: {commitment.title}</button>)}
            </div>
          )}
          {plan.pauseReason === "TARGET_REACHED" && openCommitments.length === 0 && <button type="button" disabled={busy !== null} onClick={() => setAdding(true)}><Plus size={15} />Add commitment</button>}
        </div>
      )}

      {plan.state === "ENDED" && (
        <div className="today-focus ended-focus">
          <Flag size={20} />
          <div><h3>Today ended</h3><p>{plan.completedRounds} rounds completed{plan.endedAt === null ? "" : ` · ${formatTime(plan.endedAt)}`}</p></div>
        </div>
      )}

      <Commitments
        commitments={plan.commitments}
        currentCommitmentId={plan.currentCommitmentId}
        busy={busy !== null || plan.state === "ENDED"}
        onToggle={(stepId, completed) => run(`step-${stepId}`, () => api.setCommitmentStepCompleted(plan, stepId, completed), completed ? "Step completed" : "Step reopened")}
      />
      {error && <p className="form-error" role="alert">{error}</p>}
      {adding && (
        <AddCommitmentDialog
          templates={templates.filter((template) => !template.archived)}
          busy={busy !== null}
          onClose={() => setAdding(false)}
          onTemplatesChanged={onChanged}
          onAdd={(templateId) => run("add", () => api.addDailyPlanCommitment(plan, templateId), "Commitment added")}
        />
      )}
      <ConfirmDialog
        open={ending}
        title="End today"
        message="End this daily plan? Reset returns it to Ready while keeping commitment completion."
        confirmLabel="End today"
        danger
        busy={busy !== null}
        onCancel={() => setEnding(false)}
        onConfirm={() => { setEnding(false); void run("end", () => api.endDailyPlan(plan), "Today ended"); }}
      />
      <ConfirmDialog
        open={resetting}
        title="Reset today"
        message="Reset execution to Ready? Commitment instances and step completion remain unchanged."
        confirmLabel="Reset to Ready"
        danger
        busy={busy !== null}
        onCancel={() => setResetting(false)}
        onConfirm={() => { setResetting(false); void run("reset", () => api.resetDailyPlan(plan), "Today reset to Ready"); }}
      />
    </section>
  );
}

function PlanEditor({
  localDate,
  plan,
  templates,
  busy,
  onCancel,
  onTemplatesChanged,
  onSave,
}: {
  localDate: string;
  plan: DailyPlan | null;
  templates: TaskTemplate[];
  busy: boolean;
  onCancel?: () => void;
  onTemplatesChanged: (message: string) => Promise<void>;
  onSave: (input: { firstMove: string; primaryTemplateId: number; secondaryTemplateId: number | null }) => Promise<void>;
}) {
  const primary = plan?.commitments.find((commitment) => commitment.role === "PRIMARY");
  const secondary = plan?.commitments.find((commitment) => commitment.role === "SECONDARY");
  const [firstMove, setFirstMove] = useState(plan?.firstMove ?? "");
  const [primaryTemplateId, setPrimaryTemplateId] = useState(primary?.templateId ? String(primary.templateId) : "");
  const [secondaryTemplateId, setSecondaryTemplateId] = useState(secondary?.templateId ? String(secondary.templateId) : "");
  const [creatingTemplate, setCreatingTemplate] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!primaryTemplateId || primaryTemplateId === secondaryTemplateId) return;
    await onSave({
      firstMove,
      primaryTemplateId: Number(primaryTemplateId),
      secondaryTemplateId: secondaryTemplateId ? Number(secondaryTemplateId) : null,
    });
  }

  return (
    <>
      <form className="today-plan-form" onSubmit={(event) => void submit(event)}>
        <div className="section-heading"><div><p className="eyebrow">{formatLocalDate(localDate)}</p><h2>{plan === null ? "Plan today" : "Edit today"}</h2></div></div>
        <div className="form-grid today-plan-grid">
          <label>First move<textarea required autoFocus={plan === null} value={firstMove} disabled={busy} onChange={(event) => setFirstMove(event.target.value)} /></label>
          <label>Primary template<select required value={primaryTemplateId} disabled={busy} onChange={(event) => setPrimaryTemplateId(event.target.value)}><option value="">Choose template</option>{templateOptions(templates, secondaryTemplateId)}</select></label>
          <label>Secondary template<select value={secondaryTemplateId} disabled={busy} onChange={(event) => setSecondaryTemplateId(event.target.value)}><option value="">No secondary template</option>{templateOptions(templates, primaryTemplateId)}</select></label>
        </div>
        <div className="today-template-tools"><button type="button" className="secondary compact" onClick={() => setCreatingTemplate(true)} disabled={busy}><Plus size={15} />New template</button></div>
        {primaryTemplateId && primaryTemplateId === secondaryTemplateId && <p className="form-error" role="alert">Primary and secondary templates must be different.</p>}
        {!primaryTemplateId && templates.length === 0 && <p className="form-error" role="alert">Create a template before planning today.</p>}
        <div className="modal-actions">
          {onCancel && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>}
          <button disabled={busy || !firstMove.trim() || !primaryTemplateId || primaryTemplateId === secondaryTemplateId}><Save size={16} />{busy ? "Saving..." : "Save today"}</button>
        </div>
      </form>
      {creatingTemplate && <TemplateForm onClose={() => setCreatingTemplate(false)} onSaved={async (template) => { if (!primaryTemplateId) setPrimaryTemplateId(String(template.id)); else if (!secondaryTemplateId) setSecondaryTemplateId(String(template.id)); setCreatingTemplate(false); await onTemplatesChanged("Template created"); }} />}
    </>
  );
}

function AddCommitmentDialog({
  templates,
  busy,
  onClose,
  onTemplatesChanged,
  onAdd,
}: {
  templates: TaskTemplate[];
  busy: boolean;
  onClose: () => void;
  onTemplatesChanged: (message: string) => Promise<void>;
  onAdd: (templateId: number) => Promise<void>;
}) {
  const [templateId, setTemplateId] = useState(templates[0] ? String(templates[0].id) : "");
  const [creatingTemplate, setCreatingTemplate] = useState(false);

  if (creatingTemplate) {
    return <TemplateForm onClose={() => setCreatingTemplate(false)} onSaved={async (template) => { setTemplateId(String(template.id)); setCreatingTemplate(false); await onTemplatesChanged("Template created"); }} />;
  }

  return (
    <Modal title="Add commitment" onClose={onClose}>
      <form className="item-form" onSubmit={(event) => { event.preventDefault(); if (templateId) void onAdd(Number(templateId)); }}>
        <label>Template<select aria-label="Commitment template" required value={templateId} disabled={busy} onChange={(event) => setTemplateId(event.target.value)}><option value="">Choose template</option>{templates.map((template) => <option key={template.id} value={template.id}>{template.title} · {template.cadence === "DAILY" ? "Daily" : "Manual"}</option>)}</select></label>
        <button type="button" className="secondary" disabled={busy} onClick={() => setCreatingTemplate(true)}><Plus size={15} />New template</button>
        <div className="modal-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>Cancel</button><button disabled={busy || !templateId}><Plus size={15} />Add commitment</button></div>
      </form>
    </Modal>
  );
}

function templateOptions(templates: TaskTemplate[], unavailableId: string) {
  return templates.filter((template) => String(template.id) !== unavailableId).map((template) => (
    <option key={template.id} value={template.id}>{template.title} · {template.cadence === "DAILY" ? "Daily" : "Manual"}</option>
  ));
}

function DateNavigator({ localDate, onChange }: { localDate: string; onChange: (localDate: string) => void }) {
  const today = localToday();
  return (
    <div className="today-date-toolbar">
      <button type="button" className="icon-button" aria-label="Previous date" title="Previous date" onClick={() => onChange(shiftLocalDate(localDate, -1))}><ChevronLeft size={18} /></button>
      <label className="today-date-input"><input type="date" aria-label="Today plan date" value={localDate} onChange={(event) => { if (event.target.value) onChange(event.target.value); }} /></label>
      <button type="button" className="icon-button" aria-label="Next date" title="Next date" onClick={() => onChange(shiftLocalDate(localDate, 1))}><ChevronRight size={18} /></button>
      <button type="button" className="secondary compact" disabled={localDate === today} onClick={() => onChange(today)}>Today</button>
    </div>
  );
}

function Commitments({
  commitments,
  currentCommitmentId,
  busy,
  onToggle,
}: {
  commitments: TodayCommitment[];
  currentCommitmentId: number | null;
  busy: boolean;
  onToggle: (stepId: number, completed: boolean) => Promise<void>;
}) {
  return (
    <section className="today-commitments">
      <div className="section-heading"><div><p className="eyebrow">Commitments</p><h3>{commitments.length} planned</h3></div></div>
      <div className="commitment-list">
        {commitments.map((commitment, index) => {
          const completed = commitment.steps.filter((step) => step.completedAt !== null).length;
          return (
            <div className={commitment.id === currentCommitmentId ? "commitment-row current" : "commitment-row"} key={commitment.id}>
              <div className="commitment-heading commitment-heading-static">
                <span className="step-index">{index + 1}</span>
                <div><strong>{commitment.title}</strong><span>{completed} / {commitment.steps.length} steps · {roleLabel(commitment)} · {commitment.cadence === "DAILY" ? "Daily" : "Manual"}</span></div>
                {commitment.completedAt !== null ? <Badge tone="muted">Completed</Badge> : commitment.id === currentCommitmentId ? <Badge tone="accent">Current</Badge> : <Badge tone="neutral">Open</Badge>}
              </div>
              <div className="today-step-list">
                {commitment.steps.map((step) => (
                  <label key={step.id} className={step.completedAt === null ? "today-step" : "today-step completed"}>
                    <input type="checkbox" checked={step.completedAt !== null} disabled={busy} onChange={(event) => void onToggle(step.id, event.target.checked)} />
                    <span>{step.title}</span>
                  </label>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function PlanBadge({ plan }: { plan: DailyPlan }) {
  const values = {
    READY: { tone: "warning" as const, label: "Ready" },
    ACTIVE: { tone: "accent" as const, label: "Active" },
    PAUSED: { tone: "neutral" as const, label: plan.pauseReason === "COMMITMENTS_COMPLETED" ? "Completed" : "Target reached" },
    ENDED: { tone: "muted" as const, label: "Ended" },
  };
  const value = values[plan.state];
  return <Badge tone={value.tone}>{value.label}</Badge>;
}

function stateTitle(plan: DailyPlan) {
  if (plan.state === "READY") return "First move";
  if (plan.state === "ACTIVE") return plan.nextReviewAt !== null && Date.now() >= plan.nextReviewAt ? "Hourly review" : "Current round";
  if (plan.state === "PAUSED") return plan.pauseReason === "COMMITMENTS_COMPLETED" ? "Commitments complete" : "Daily target reached";
  return "Daily plan closed";
}

function roundSummary(completedRounds: number) {
  if (completedRounds === 0) return "Completed before the first review";
  return `${completedRounds} ${completedRounds === 1 ? "round" : "rounds"} completed`;
}

function roleLabel(commitment: TodayCommitment) {
  if (commitment.role === "PRIMARY") return "Primary";
  if (commitment.role === "SECONDARY") return "Secondary";
  return "Added";
}

function formatTime(value: number) {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(value);
}

function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}
