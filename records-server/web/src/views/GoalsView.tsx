import { useState } from "react";
import { Archive, ArchiveRestore, Edit3, Plus, Repeat2, Save, Trash2 } from "lucide-react";
import { api } from "../api";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { localToday, message } from "../lib/format";
import type { Goal, GoalInput } from "../types";

export function GoalsView({ goals, loading, onChanged, onError }: {
  goals: Goal[];
  loading: boolean;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [editing, setEditing] = useState<Goal | "new" | null>(null);
  const [retrying, setRetrying] = useState<Goal | null>(null);
  const [recording, setRecording] = useState<Goal | null>(null);
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState<{ goal: Goal; action: "archive" | "restore" | "delete" } | null>(null);

  async function record() {
    if (!recording) return;
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) return;
    setBusy(true);
    try {
      await api.createGoalEntry(recording.id, parsed);
      setRecording(null);
      setValue("");
      await onChanged("Goal progress recorded");
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  async function toggle(goal: Goal, subgoalId: number, completed: boolean) {
    try {
      await api.setGoalSubgoalCompleted(subgoalId, completed);
      await onChanged(completed ? "Subgoal completed" : "Subgoal reopened");
    } catch (reason) {
      onError(message(reason));
    }
  }

  async function applyConfirmation() {
    if (!confirming) return;
    setBusy(true);
    try {
      if (confirming.action === "delete") await api.deleteGoal(confirming.goal.id);
      else await api.archiveGoals([confirming.goal.id], confirming.action === "archive");
      const action = confirming.action;
      setConfirming(null);
      await onChanged(action === "delete" ? "Goal deleted" : action === "archive" ? "Goal archived" : "Goal restored");
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-stack">
      <div className="filter-bar">
        <span className="result-count">{loading ? "Loading…" : `${goals.length} goals`}</span>
        <div className="toolbar-spacer" />
        <button type="button" onClick={() => setEditing("new")}><Plus size={16} />New goal</button>
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Goal</th><th>Shared progress</th><th>Subgoals</th><th>Date range</th><th /></tr></thead>
          <tbody>
            {!loading && goals.length === 0 && <tr><td className="empty" colSpan={5}>No goals yet.</td></tr>}
            {goals.map((goal) => (
              (() => {
                const expired = !goal.subgoals.every((subgoal) => subgoal.completedAt !== null) && localToday() > goal.endDate;
                const completed = goal.subgoals.length > 0 && goal.subgoals.every((subgoal) => subgoal.completedAt !== null);
                const pending = localToday() < goal.startDate;
                return (
              <tr key={goal.id}>
                <td><div className="primary-cell"><strong>{goal.title}</strong><span>{completed ? "Completed" : pending ? `Starts ${goal.startDate}` : expired ? "Expired" : goal.description || "No description"}</span></div></td>
                <td>{goal.archived || completed || pending || expired ? <strong>{goal.currentValue} {goal.unit}</strong> : <button type="button" className="table-link" onClick={() => { setRecording(goal); setValue(goal.defaultValue?.toString() ?? ""); }}><strong>{goal.currentValue} {goal.unit}</strong></button>}</td>
                <td>
                  <div className="goal-subgoal-list">
                    {goal.subgoals.map((subgoal) => {
                      const eligible = goal.currentValue >= subgoal.targetValue;
                      return <label key={subgoal.id} title={eligible ? "Ready to complete" : `${subgoal.targetValue - goal.currentValue} ${goal.unit} remaining`}>
                        <input type="checkbox" checked={subgoal.completedAt !== null} disabled={goal.archived || completed || pending || expired || (!eligible && subgoal.completedAt === null)} onChange={(event) => void toggle(goal, subgoal.id, event.target.checked)} />
                        <span>{subgoal.title} · {subgoal.targetValue} {goal.unit}</span>
                      </label>;
                    })}
                  </div>
                </td>
                <td>{goal.startDate} – {goal.endDate}</td>
                <td><div className="row-actions">
                  {!goal.archived && !completed && !expired && <button type="button" className="icon-button" aria-label={`Edit ${goal.title}`} title="Edit" onClick={() => setEditing(goal)}><Edit3 size={16} /></button>}
                  {(completed || expired) && <button type="button" className="icon-button" aria-label={`Try ${goal.title} again`} title="Try again" onClick={() => setRetrying(goal)}><Repeat2 size={16} /></button>}
                  <button type="button" className="icon-button" aria-label={`${goal.archived ? "Restore" : "Archive"} ${goal.title}`} title={goal.archived ? "Restore" : "Archive"} onClick={() => setConfirming({ goal, action: goal.archived ? "restore" : "archive" })}>{goal.archived ? <ArchiveRestore size={16} /> : <Archive size={16} />}</button>
                  {goal.archived && <button type="button" className="icon-button danger-icon" aria-label={`Delete ${goal.title}`} title="Delete permanently" onClick={() => setConfirming({ goal, action: "delete" })}><Trash2 size={16} /></button>}
                </div></td>
              </tr>
                );
              })()
            ))}
          </tbody>
        </table>
      </div>
      {editing && <GoalForm key={editing === "new" ? "new" : editing.id} goal={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onSaved={async () => { setEditing(null); await onChanged("Goal saved"); }} />}
      {retrying && <GoalForm key={`retry-${retrying.id}`} source={retrying} onClose={() => setRetrying(null)} onSaved={async () => { setRetrying(null); await onChanged("New goal created"); }} />}
      {recording && (
        <Modal title={`Record ${recording.title}`} onClose={() => setRecording(null)}>
          <form className="item-form" onSubmit={(event) => { event.preventDefault(); void record(); }}>
            <label>Value<input autoFocus type="number" min="0.000001" step="any" value={value} onChange={(event) => setValue(event.target.value)} /></label>
            <p className="field-hint">Shared progress is currently {recording.currentValue} {recording.unit}.</p>
            <div className="modal-actions"><button type="button" className="secondary" onClick={() => setRecording(null)}>Cancel</button><button disabled={busy || !(Number(value) > 0)}><Plus size={16} />Record</button></div>
          </form>
        </Modal>
      )}
      <ConfirmDialog
        open={confirming !== null}
        title={`${confirming?.action === "delete" ? "Delete" : confirming?.action === "archive" ? "Archive" : "Restore"} goal`}
        message={confirming?.action === "delete" ? "Delete this goal, its subgoals, and all progress permanently?" : `${confirming?.action === "archive" ? "Archive" : "Restore"} ${confirming?.goal.title ?? "this goal"}?`}
        confirmLabel={confirming?.action === "delete" ? "Delete permanently" : confirming?.action === "archive" ? "Archive" : "Restore"}
        danger={confirming?.action === "delete"}
        busy={busy}
        onCancel={() => setConfirming(null)}
        onConfirm={() => void applyConfirmation()}
      />
    </section>
  );
}

function GoalForm({ goal, source, onClose, onSaved }: { goal?: Goal; source?: Goal; onClose: () => void; onSaved: () => Promise<void> }) {
  const definition = goal ?? source;
  const locked = (goal?.currentValue ?? 0) > 0;
  const [title, setTitle] = useState(definition?.title ?? "");
  const [description, setDescription] = useState(definition?.description ?? "");
  const [unit, setUnit] = useState(definition?.unit ?? "次");
  const [defaultValue, setDefaultValue] = useState(definition?.defaultValue?.toString() ?? "");
  const [stepValue, setStepValue] = useState(definition?.stepValue?.toString() ?? "1");
  const [startDate, setStartDate] = useState(goal?.startDate ?? localToday());
  const [endDate, setEndDate] = useState(goal?.endDate ?? (source ? retryEndDate(source) : nextYear()));
  const [subgoals, setSubgoals] = useState<Array<{ id: number | null; title: string; targetValue: string }>>(
    definition?.subgoals.map((item) => ({ id: goal ? item.id : null, title: item.title, targetValue: item.targetValue.toString() }))
      ?? [{ id: null, title: "", targetValue: "" }],
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    const input: GoalInput = {
      title,
      description,
      unit,
      defaultValue: defaultValue.trim() ? Number(defaultValue) : null,
      stepValue: Number(stepValue),
      startDate,
      endDate,
      subgoals: subgoals.map((item) => ({ id: item.id, title: item.title, targetValue: Number(item.targetValue) })),
    };
    setSaving(true);
    setFormError(null);
    try {
      await api.saveGoal(input, goal?.id);
      await onSaved();
    } catch (reason) {
      setFormError(message(reason));
    } finally {
      setSaving(false);
    }
  }

  return <Modal title={`${goal ? "Edit" : "New"} goal`} onClose={onClose} size="lg">
    <form className="item-form" onSubmit={(event) => { event.preventDefault(); void save(); }}>
      <section className="form-section">
        <div className="form-grid form-grid-2"><label>Title<input autoFocus value={title} onChange={(event) => setTitle(event.target.value)} required /></label><label>Unit<input value={unit} disabled={locked} onChange={(event) => setUnit(event.target.value)} required /></label></div>
        <label>Description<textarea value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <div className="form-grid form-grid-2"><label>Default value<input type="number" min="0.000001" step="any" value={defaultValue} onChange={(event) => setDefaultValue(event.target.value)} /></label><label>Step<input type="number" min="0.000001" step="any" disabled={locked} value={stepValue} onChange={(event) => setStepValue(event.target.value)} required /></label></div>
        <div className="form-grid form-grid-2"><label>Start date<input type="date" disabled={locked} value={startDate} onChange={(event) => setStartDate(event.target.value)} required /></label><label>End date<input type="date" disabled={locked} value={endDate} onChange={(event) => setEndDate(event.target.value)} required /></label></div>
      </section>
      <section className="form-section">
        <div className="section-heading"><h3>Subgoals</h3>{!locked && <button type="button" className="secondary compact" onClick={() => setSubgoals([...subgoals, { id: null, title: "", targetValue: "" }])}><Plus size={14} />Add</button>}</div>
        <div className="goal-editor-list">{subgoals.map((subgoal, index) => <div className="goal-edit-row" key={subgoal.id ?? `new-${index}`}><input aria-label={`Subgoal ${index + 1} title`} disabled={locked} placeholder="Subgoal" value={subgoal.title} onChange={(event) => setSubgoals(subgoals.map((item, itemIndex) => itemIndex === index ? { ...item, title: event.target.value } : item))} required /><input aria-label={`Subgoal ${index + 1} target`} disabled={locked} type="number" min="0.000001" step="any" placeholder="Target" value={subgoal.targetValue} onChange={(event) => setSubgoals(subgoals.map((item, itemIndex) => itemIndex === index ? { ...item, targetValue: event.target.value } : item))} required />{!locked && subgoals.length > 1 && <button type="button" className="icon-button danger-icon" aria-label={`Remove subgoal ${index + 1}`} onClick={() => setSubgoals(subgoals.filter((_, itemIndex) => itemIndex !== index))}><Trash2 size={15} /></button>}</div>)}</div>
        {locked && <p className="field-hint">Dates, unit, step, and subgoals are fixed after progress is recorded.</p>}
      </section>
      {formError && <p className="form-error" role="alert">{formError}</p>}
      <div className="modal-actions"><button type="button" className="secondary" onClick={onClose} disabled={saving}>Cancel</button><button disabled={saving}><Save size={16} />{saving ? "Saving…" : "Save changes"}</button></div>
    </form>
  </Modal>;
}

function nextYear(): string {
  const date = new Date();
  date.setFullYear(date.getFullYear() + 1);
  return date.toISOString().slice(0, 10);
}

function retryEndDate(source: Goal): string {
  const start = new Date(`${source.startDate}T00:00:00Z`);
  const end = new Date(`${source.endDate}T00:00:00Z`);
  const result = new Date(`${localToday()}T00:00:00Z`);
  result.setUTCDate(result.getUTCDate() + Math.round((end.getTime() - start.getTime()) / 86_400_000));
  return result.toISOString().slice(0, 10);
}
