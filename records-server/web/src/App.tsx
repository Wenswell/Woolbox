import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import { Toast } from "./components/Toast";
import { localToday, message } from "./lib/format";
import type { RecordAuditLog, RecordEntry, RecordItem } from "./types";
import { AuditLogView } from "./views/AuditLogView";
import { EntriesView, type EntryFilters } from "./views/EntriesView";
import { ItemsView, type ItemStatusFilter } from "./views/ItemsView";

type View = "entries" | "items" | "logs";

const VIEWS: Array<{ id: View; label: string }> = [
  { id: "entries", label: "Entries" },
  { id: "items", label: "Record items" },
  { id: "logs", label: "Logs" },
];

export function App() {
  const [view, setView] = useState<View>("entries");
  const [items, setItems] = useState<RecordItem[]>([]);
  const [entryItems, setEntryItems] = useState<RecordItem[]>([]);
  const [entries, setEntries] = useState<RecordEntry[]>([]);
  const [logs, setLogs] = useState<RecordAuditLog[]>([]);
  const [nextLogId, setNextLogId] = useState<number | null>(null);
  const [filters, setFilters] = useState<EntryFilters>({
    startDate: localToday(),
    endDate: localToday(),
    itemId: "",
  });
  const [itemStatus, setItemStatus] = useState<ItemStatusFilter>("active");
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadEntryItems = useCallback(async () => {
    setEntryItems(await api.items(false));
  }, []);

  const loadItems = useCallback(async () => {
    if (itemStatus === "all") {
      const [active, archived] = await Promise.all([api.items(false), api.items(true)]);
      setItems(
        [...active, ...archived].sort((left, right) => left.priority - right.priority || left.name.localeCompare(right.name)),
      );
      return;
    }
    setItems(await api.items(itemStatus === "archived"));
  }, [itemStatus]);

  const loadEntries = useCallback(async () => {
    setEntries(await api.entries(filters.startDate, filters.endDate, filters.itemId));
  }, [filters]);

  const loadLogs = useCallback(async (beforeId?: number) => {
    const page = await api.auditLogs(beforeId);
    setLogs((current) => (beforeId === undefined ? page.logs : [...current, ...page.logs]));
    setNextLogId(page.nextBeforeId);
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      await Promise.all([loadEntryItems(), loadItems(), loadEntries(), loadLogs()]);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }, [loadEntries, loadEntryItems, loadItems, loadLogs]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  async function handleDeleteEntries(ids: number[]) {
    try {
      await api.deleteEntries(ids);
      setToast(`Deleted ${ids.length} entries`);
      await refresh();
    } catch (reason) {
      setError(message(reason));
      throw reason;
    }
  }

  async function handleArchiveItems(ids: number[], archived: boolean) {
    try {
      await api.archive(ids, archived);
      setToast(archived ? `Archived ${ids.length} items` : `Restored ${ids.length} items`);
      await refresh();
    } catch (reason) {
      setError(message(reason));
      throw reason;
    }
  }

  async function handleItemSaved() {
    setToast("Record item saved");
    await refresh();
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">Records administration</p>
          <h1>Records</h1>
        </div>
        <button type="button" className="secondary" onClick={() => void refresh()}>
          Refresh
        </button>
      </header>

      <nav className="app-tabs" aria-label="Views">
        <div className="segmented tabs">
          {VIEWS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={view === item.id ? "active" : undefined}
              onClick={() => setView(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </nav>

      {error && (
        <div className="error" role="alert">
          <span>{error}</span>
          <button type="button" className="secondary" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      <main className="app-main">
        {view === "entries" && (
          <EntriesView
            entries={entries}
            items={entryItems}
            filters={filters}
            loading={loading}
            onFilters={setFilters}
            onDelete={handleDeleteEntries}
          />
        )}
        {view === "items" && (
          <ItemsView
            items={items}
            loading={loading}
            status={itemStatus}
            onStatusChange={setItemStatus}
            onArchive={handleArchiveItems}
            onSaved={handleItemSaved}
          />
        )}
        {view === "logs" && (
          <AuditLogView
            logs={logs}
            loading={loading}
            nextBeforeId={nextLogId}
            onLoadMore={() => {
              if (nextLogId !== null) void loadLogs(nextLogId);
            }}
          />
        )}
      </main>

      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}
