import { useEffect, useMemo, useState } from "react";
import { Archive, ArchiveRestore, ArrowDown, ArrowUp, Edit3, Plus, Save, Search, Trash2 } from "lucide-react";
import { getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";
import { api } from "../api";
import { Badge } from "../components/Badge";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { DataTable } from "../components/DataTable";
import { Modal } from "../components/Modal";
import { message } from "../lib/format";
import type { TaskTemplate, TemplateCadence } from "../types";

export function TemplatesView({
  templates,
  loading,
  onChanged,
  onError,
}: {
  templates: TaskTemplate[];
  loading: boolean;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [archived, setArchived] = useState(false);
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [deleting, setDeleting] = useState<TaskTemplate | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return templates
      .filter((template) => template.archived === archived)
      .filter((template) => !normalized || `${template.title} ${template.steps.map((step) => step.title).join(" ")}`.toLocaleLowerCase().includes(normalized));
  }, [archived, query, templates]);

  async function setTemplateArchived(template: TaskTemplate, value: boolean) {
    setBusy(template.id);
    try {
      await api.archiveTemplates([template.id], value);
      await onChanged(value ? "Template archived" : "Template restored");
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(null);
    }
  }

  const columns = useMemo<ColumnDef<TaskTemplate>[]>(() => [
    {
      id: "template",
      header: "Template",
      cell: ({ row }) => (
        <button type="button" className="table-link primary-cell" onClick={() => setEditing(row.original.id)}>
          <strong>{row.original.title}</strong>
          <span>{row.original.steps.map((step) => step.title).join(" · ")}</span>
        </button>
      ),
    },
    { id: "cadence", header: "Cadence", cell: ({ row }) => <Badge tone={row.original.cadence === "DAILY" ? "accent" : "neutral"}>{cadenceLabel(row.original.cadence)}</Badge> },
    { id: "steps", header: "Steps", cell: ({ row }) => <span className="time-cell">{row.original.steps.length}</span> },
    { id: "status", header: "Status", cell: ({ row }) => <Badge tone={row.original.archived ? "muted" : "accent"}>{row.original.archived ? "Archived" : "Active"}</Badge> },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <div className="row-actions">
          <button type="button" className="icon-button" aria-label={`Edit ${row.original.title}`} title="Edit template" disabled={busy !== null} onClick={() => setEditing(row.original.id)}><Edit3 size={16} /></button>
          <button type="button" className="icon-button" aria-label={`${row.original.archived ? "Restore" : "Archive"} ${row.original.title}`} title={row.original.archived ? "Restore template" : "Archive template"} disabled={busy !== null} onClick={() => void setTemplateArchived(row.original, !row.original.archived)}>{row.original.archived ? <ArchiveRestore size={16} /> : <Archive size={16} />}</button>
          {row.original.archived && <button type="button" className="icon-button danger-icon" aria-label={`Delete ${row.original.title}`} title="Delete template" disabled={busy !== null} onClick={() => setDeleting(row.original)}><Trash2 size={16} /></button>}
        </div>
      ),
    },
  ], [busy]);

  const table = useReactTable({ data: filtered, columns, getRowId: (row) => String(row.id), getCoreRowModel: getCoreRowModel() });
  const editingTemplate = typeof editing === "number" ? templates.find((template) => template.id === editing) : undefined;

  async function deleteTemplate() {
    if (!deleting) return;
    setBusy(deleting.id);
    try {
      await api.deleteTemplate(deleting.id);
      setDeleting(null);
      if (editing === deleting.id) setEditing(null);
      await onChanged("Template deleted");
    } catch (reason) {
      onError(message(reason));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="view-stack">
      <div className="filter-bar">
        <div className="segmented" role="group" aria-label="Template status">
          <button type="button" className={!archived ? "active" : undefined} onClick={() => setArchived(false)}>Active</button>
          <button type="button" className={archived ? "active" : undefined} onClick={() => setArchived(true)}>Archived</button>
        </div>
        <label className="search-field"><Search size={16} /><input aria-label="Search templates" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search templates and steps" /></label>
        <span className="result-count">{loading ? "Loading..." : `${filtered.length} templates`}</span>
        <div className="toolbar-spacer" />
        <button type="button" onClick={() => setEditing("new")}><Plus size={16} />New template</button>
      </div>

      <DataTable table={table} loading={loading} empty={loading ? "Loading templates..." : "No templates match this view."} />

      {editing === "new" && (
        <TemplateForm
          onClose={() => setEditing(null)}
          onSaved={async () => { setEditing(null); await onChanged("Template created"); }}
        />
      )}
      {editingTemplate && (
        <TemplateForm
          template={editingTemplate}
          onClose={() => setEditing(null)}
          onSaved={async () => { setEditing(null); await onChanged("Template saved"); }}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete template"
        message={`Delete "${deleting?.title ?? ""}"? Existing Today tasks keep their snapshots.`}
        confirmLabel="Delete template"
        danger
        busy={busy !== null}
        onCancel={() => setDeleting(null)}
        onConfirm={() => void deleteTemplate()}
      />
    </section>
  );
}

export function TemplateForm({
  template,
  onClose,
  onSaved,
}: {
  template?: TaskTemplate;
  onClose: () => void;
  onSaved: (template: TaskTemplate) => Promise<void>;
}) {
  const [title, setTitle] = useState(template?.title ?? "");
  const [cadence, setCadence] = useState<TemplateCadence>(template?.cadence ?? "MANUAL");
  const [steps, setSteps] = useState(template?.steps.map((step) => step.title) ?? []);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setTitle(template?.title ?? "");
    setCadence(template?.cadence ?? "MANUAL");
    setSteps(template?.steps.map((step) => step.title) ?? []);
  }, [template]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await api.saveTemplate({ title, cadence, steps: steps.map((step) => ({ title: step })) }, template?.id);
      await onSaved(result.template);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setBusy(false);
    }
  }

  function moveStep(index: number, offset: number) {
    const target = index + offset;
    if (target < 0 || target >= steps.length) return;
    setSteps((current) => {
      const reordered = [...current];
      [reordered[index], reordered[target]] = [reordered[target]!, reordered[index]!];
      return reordered;
    });
  }

  return (
    <Modal title={template ? "Edit template" : "New template"} onClose={onClose} size="lg">
      <form className="item-form" onSubmit={(event) => void submit(event)}>
        <div className="form-grid">
          <label>Title<input autoFocus required value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <CadenceField value={cadence} onChange={setCadence} disabled={busy} />
        </div>
        <section className="form-section">
          <div className="section-heading"><div><p className="eyebrow">Optional</p><h3>Task checklist</h3></div><button type="button" className="secondary compact" disabled={busy} onClick={() => setSteps((current) => [...current, ""])}><Plus size={15} />Add step</button></div>
          <div className="step-editor-list">
            {steps.length === 0 && <p className="empty-state">No checklist. The task can be completed directly.</p>}
            {steps.map((step, index) => (
              <div className="template-step-row" key={index}>
                <span className="step-index">{index + 1}</span>
                <input aria-label={`Step ${index + 1}`} required value={step} disabled={busy} onChange={(event) => setSteps((current) => current.map((value, itemIndex) => itemIndex === index ? event.target.value : value))} />
                <button type="button" className="icon-button" aria-label={`Move step ${index + 1} up`} title="Move up" disabled={busy || index === 0} onClick={() => moveStep(index, -1)}><ArrowUp size={16} /></button>
                <button type="button" className="icon-button" aria-label={`Move step ${index + 1} down`} title="Move down" disabled={busy || index === steps.length - 1} onClick={() => moveStep(index, 1)}><ArrowDown size={16} /></button>
                <button type="button" className="icon-button danger-icon" aria-label={`Remove step ${index + 1}`} title="Remove step" disabled={busy} onClick={() => setSteps((current) => current.filter((_, itemIndex) => itemIndex !== index))}><Trash2 size={16} /></button>
              </div>
            ))}
          </div>
        </section>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="modal-actions"><button type="button" className="secondary" onClick={onClose} disabled={busy}>Cancel</button><button disabled={busy || !title.trim() || steps.some((step) => !step.trim())}><Save size={16} />{busy ? "Saving..." : template ? "Save template" : "Create template"}</button></div>
      </form>
    </Modal>
  );
}

function CadenceField({ value, onChange, disabled }: { value: TemplateCadence; onChange: (value: TemplateCadence) => void; disabled: boolean }) {
  return (
    <fieldset className="field-group">
      <legend>Cadence</legend>
      <div className="segmented" role="group" aria-label="Cadence">
        <button type="button" disabled={disabled} className={value === "MANUAL" ? "active" : undefined} onClick={() => onChange("MANUAL")}>Manual</button>
        <button type="button" disabled={disabled} className={value === "DAILY" ? "active" : undefined} onClick={() => onChange("DAILY")}>Daily</button>
      </div>
    </fieldset>
  );
}

function cadenceLabel(value: TemplateCadence) {
  return value === "DAILY" ? "Daily" : "Manual";
}
