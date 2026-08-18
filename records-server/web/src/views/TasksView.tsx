import { useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Check, Edit3, Plus, Save, Search, Trash2 } from "lucide-react";
import { getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { message } from "../lib/format";
import type { TaskRepeatMode, TodoStep, TodoTask } from "../types";

type TaskFilter = "open" | "completed";

export function TasksView({
  localDate,
  tasks,
  loading,
  onChanged,
  onError,
}: {
  localDate: string;
  tasks: TodoTask[];
  loading: boolean;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [filter, setFilter] = useState<TaskFilter>("open");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [deleting, setDeleting] = useState<TodoTask | null>(null);
  const [busy, setBusy] = useState(false);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return tasks
      .filter((task) => (filter === "completed") === (task.completedAt !== null))
      .filter((task) => !normalized || `${task.title} ${task.steps.map((step) => step.title).join(" ")}`.toLocaleLowerCase().includes(normalized))
      .sort((left, right) => left.title.localeCompare(right.title));
  }, [filter, query, tasks]);

  const columns = useMemo<ColumnDef<TodoTask>[]>(() => [
    {
      id: "task",
      header: "Task",
      cell: ({ row }) => (
        <button type="button" className="table-link primary-cell" onClick={() => setEditing(row.original.id)}>
          <strong>{row.original.title}</strong>
          <span>{row.original.steps.find((step) => step.completedAt === null)?.title ?? "All subtasks completed"}</span>
        </button>
      ),
    },
    {
      id: "repeat",
      header: "Repeat",
      cell: ({ row }) => <Badge tone="neutral">{repeatLabel(row.original.repeatMode)}</Badge>,
    },
    {
      id: "progress",
      header: "Progress",
      cell: ({ row }) => {
        const completed = row.original.steps.filter((step) => step.completedAt !== null).length;
        const progress = row.original.steps.length ? completed / row.original.steps.length : 0;
        return (
          <div className="progress-cell">
            <span>{completed} / {row.original.steps.length}</span>
            <span className="progress-track"><span style={{ width: `${progress * 100}%` }} /></span>
          </div>
        );
      },
    },
    {
      id: "status",
      header: "Status",
      cell: ({ row }) => {
        const status = taskStatus(row.original);
        return <Badge tone={status.tone}>{status.label}</Badge>;
      },
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <div className="row-actions">
          <button type="button" className="icon-button" aria-label={`Edit ${row.original.title}`} title="Edit task" onClick={() => setEditing(row.original.id)}><Edit3 size={16} /></button>
          <button type="button" className="icon-button danger-icon" aria-label={`Delete ${row.original.title}`} title="Delete task" onClick={() => setDeleting(row.original)}><Trash2 size={16} /></button>
        </div>
      ),
    },
  ], []);

  const table = useReactTable({ data: filtered, columns, getRowId: (row) => String(row.id), getCoreRowModel: getCoreRowModel() });
  const editingTask = typeof editing === "number" ? tasks.find((task) => task.id === editing) : undefined;

  async function deleteTask() {
    if (!deleting) return;
    setBusy(true);
    try {
      await api.deleteTask(deleting.id, localDate);
      setDeleting(null);
      if (editing === deleting.id) setEditing(null);
      await onChanged("Task deleted");
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="view-stack">
      <div className="filter-bar">
        <div className="segmented" role="group" aria-label="Task status">
          {(["open", "completed"] as const).map((value) => (
            <button key={value} type="button" className={filter === value ? "active" : undefined} onClick={() => setFilter(value)}>{capitalize(value)}</button>
          ))}
        </div>
        <label className="search-field"><Search size={16} /><input aria-label="Search tasks" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tasks and subtasks" /></label>
        <span className="result-count">{loading ? "Loading…" : `${filtered.length} tasks`}</span>
        <div className="toolbar-spacer" />
        <button type="button" onClick={() => setEditing("new")}><Plus size={16} />New task</button>
      </div>

      <DataTable table={table} loading={loading} empty={loading ? "Loading tasks…" : "No tasks match this view."} />

      {editing === "new" && (
        <NewTaskForm
          onClose={() => setEditing(null)}
          localDate={localDate}
          onSaved={async () => { setEditing(null); await onChanged("Task created"); }}
        />
      )}
      {editingTask && (
        <TaskEditor
          task={editingTask}
          localDate={localDate}
          onClose={() => setEditing(null)}
          onChanged={onChanged}
          onError={onError}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete task"
        message={`Delete “${deleting?.title ?? ""}” and all of its subtasks? This cannot be undone.`}
        confirmLabel="Delete task"
        danger
        busy={busy}
        onCancel={() => setDeleting(null)}
        onConfirm={() => void deleteTask()}
      />
    </section>
  );
}

export function NewTaskForm({ localDate, onClose, onSaved }: { localDate: string; onClose: () => void; onSaved: (task: TodoTask) => Promise<void> }) {
  const [title, setTitle] = useState("");
  const [repeatMode, setRepeatMode] = useState<TaskRepeatMode>("ONCE");
  const [steps, setSteps] = useState([""]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await api.createTask({ title, repeatMode, localDate, steps: steps.map((step) => ({ title: step })) });
      await onSaved(result.task);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="New task" onClose={onClose} size="lg">
      <form className="item-form" onSubmit={(event) => void submit(event)}>
        <div className="form-grid">
          <label>Title<input autoFocus required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <RepeatModeField value={repeatMode} onChange={setRepeatMode} />
        </div>
        <section className="form-section">
          <div className="section-heading"><div><p className="eyebrow">Subtasks</p><h3>Required work</h3></div><button type="button" className="secondary compact" onClick={() => setSteps((current) => [...current, ""])}><Plus size={15} />Add subtask</button></div>
          <div className="step-editor-list">
            {steps.map((step, index) => (
              <div className="step-edit-row" key={index}>
                <span className="step-index">{index + 1}</span>
                <input aria-label={`Subtask ${index + 1}`} required value={step} onChange={(event) => setSteps((current) => current.map((value, itemIndex) => itemIndex === index ? event.target.value : value))} />
                <button type="button" className="icon-button danger-icon" aria-label={`Remove subtask ${index + 1}`} title="Remove subtask" disabled={steps.length === 1} onClick={() => setSteps((current) => current.filter((_, itemIndex) => itemIndex !== index))}><Trash2 size={16} /></button>
              </div>
            ))}
          </div>
        </section>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="modal-actions"><button type="button" className="secondary" onClick={onClose} disabled={busy}>Cancel</button><button disabled={busy}><Plus size={16} />{busy ? "Creating…" : "Create task"}</button></div>
      </form>
    </Modal>
  );
}

export function TaskEditor({
  task,
  localDate,
  onClose,
  onChanged,
  onError,
}: {
  task: TodoTask;
  localDate: string;
  onClose: () => void;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [title, setTitle] = useState(task.title);
  const [repeatMode, setRepeatMode] = useState(task.repeatMode);
  const [newStep, setNewStep] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [deletingStep, setDeletingStep] = useState<TodoStep | null>(null);

  useEffect(() => {
    setTitle(task.title);
    setRepeatMode(task.repeatMode);
  }, [task.id, task.repeatMode, task.title]);

  async function run(key: string, action: () => Promise<unknown>, success: string) {
    setBusy(key);
    try {
      await action();
      await onChanged(success);
      return true;
    } catch (reason) {
      onError(message(reason));
      return false;
    } finally {
      setBusy(null);
    }
  }

  async function addStep(event: React.FormEvent) {
    event.preventDefault();
    if (await run("add", () => api.createStep(task.id, newStep, localDate), "Subtask added")) {
      setNewStep("");
    }
  }

  function moveStep(index: number, offset: number) {
    const reordered = [...task.steps];
    const target = index + offset;
    if (target < 0 || target >= reordered.length) return;
    [reordered[index], reordered[target]] = [reordered[target]!, reordered[index]!];
    void run("order", () => api.reorderSteps(task.id, reordered.map((step) => step.id), localDate), "Subtasks reordered");
  }

  return (
    <Modal title="Edit task" onClose={onClose} size="lg">
      <div className="item-form">
        <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void run("task", () => api.updateTask(task.id, { title, repeatMode, localDate }), "Task saved"); }}>
          <label>Title<input required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <RepeatModeField value={repeatMode} onChange={setRepeatMode} disabled={busy !== null} />
          <div className="inline-form-action"><button disabled={busy !== null}><Save size={16} />{busy === "task" ? "Saving…" : "Save task"}</button></div>
        </form>

        <section className="form-section">
          <div className="section-heading"><div><p className="eyebrow">Subtasks</p><h3>{task.steps.filter((step) => step.completedAt !== null).length} of {task.steps.length} completed</h3></div></div>
          <div className="step-editor-list">
            {task.steps.map((step, index) => (
              <TaskStepRow
                key={step.id}
                step={step}
                index={index}
                total={task.steps.length}
                disabled={busy !== null}
                onComplete={(completed) => run(`complete-${step.id}`, () => api.setStepCompleted(step.id, completed, localDate), completed ? "Subtask completed" : "Subtask reopened")}
                onRename={(value) => run(`rename-${step.id}`, () => api.updateStep(step.id, value, localDate), "Subtask saved")}
                onMove={(offset) => moveStep(index, offset)}
                onDelete={() => setDeletingStep(step)}
              />
            ))}
          </div>
          <form className="add-step-form" onSubmit={(event) => void addStep(event)}>
            <input aria-label="New subtask" required value={newStep} onChange={(event) => setNewStep(event.target.value)} placeholder="Add another subtask" />
            <button disabled={busy !== null}><Plus size={16} />{busy === "add" ? "Adding…" : "Add"}</button>
          </form>
        </section>
        <div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Done</button></div>
      </div>
      <ConfirmDialog
        open={deletingStep !== null}
        title="Delete subtask"
        message={`Delete “${deletingStep?.title ?? ""}”? This cannot be undone.`}
        confirmLabel="Delete subtask"
        danger
        busy={busy !== null}
        onCancel={() => setDeletingStep(null)}
        onConfirm={() => {
          if (!deletingStep) return;
          const id = deletingStep.id;
          setDeletingStep(null);
          void run(`delete-${id}`, () => api.deleteStep(id, localDate), "Subtask deleted");
        }}
      />
    </Modal>
  );
}

function TaskStepRow({
  step,
  index,
  total,
  disabled,
  onComplete,
  onRename,
  onMove,
  onDelete,
}: {
  step: TodoStep;
  index: number;
  total: number;
  disabled: boolean;
  onComplete: (completed: boolean) => Promise<void>;
  onRename: (title: string) => Promise<void>;
  onMove: (offset: number) => void;
  onDelete: () => void;
}) {
  const [title, setTitle] = useState(step.title);
  useEffect(() => setTitle(step.title), [step.title]);
  return (
    <div className="step-edit-row">
      <label className="check-control" title={step.completedAt === null ? "Complete subtask" : "Reopen subtask"}>
        <input type="checkbox" aria-label={`${step.completedAt === null ? "Complete" : "Reopen"} ${step.title}`} checked={step.completedAt !== null} disabled={disabled} onChange={(event) => void onComplete(event.target.checked)} />
        <Check size={14} />
      </label>
      <input aria-label={`Edit ${step.title}`} value={title} onChange={(event) => setTitle(event.target.value)} />
      <button type="button" className="icon-button" aria-label={`Save ${step.title}`} title="Save subtask" disabled={disabled || !title.trim() || title === step.title} onClick={() => void onRename(title)}><Save size={16} /></button>
      <button type="button" className="icon-button" aria-label={`Move ${step.title} up`} title="Move up" disabled={disabled || index === 0} onClick={() => onMove(-1)}><ArrowUp size={16} /></button>
      <button type="button" className="icon-button" aria-label={`Move ${step.title} down`} title="Move down" disabled={disabled || index === total - 1} onClick={() => onMove(1)}><ArrowDown size={16} /></button>
      <button type="button" className="icon-button danger-icon" aria-label={`Delete ${step.title}`} title="Delete subtask" disabled={disabled || total === 1} onClick={onDelete}><Trash2 size={16} /></button>
    </div>
  );
}

function taskStatus(task: TodoTask): { label: string; tone: "accent" | "muted" } {
  if (task.completedAt !== null) return { label: "Completed", tone: "muted" };
  return { label: "Open", tone: "accent" };
}

function RepeatModeField({ value, onChange, disabled = false }: { value: TaskRepeatMode; onChange: (value: TaskRepeatMode) => void; disabled?: boolean }) {
  return (
    <fieldset className="field-group">
      <legend>Repeat</legend>
      <div className="segmented" role="group" aria-label="Repeat">
        {(["ONCE", "DAILY"] as const).map((mode) => (
          <button key={mode} type="button" disabled={disabled} className={value === mode ? "active" : undefined} onClick={() => onChange(mode)}>{repeatLabel(mode)}</button>
        ))}
      </div>
    </fieldset>
  );
}

function repeatLabel(value: TaskRepeatMode) {
  return value === "DAILY" ? "Daily" : "Once";
}

function capitalize(value: string): string {
  return `${value[0]!.toUpperCase()}${value.slice(1)}`;
}
