export function Toast({
  message,
  onDismiss,
}: {
  message: string | null;
  onDismiss: () => void;
}) {
  if (!message) {
    return null;
  }

  return (
    <div className="toast" role="status">
      <span>{message}</span>
      <button
        type="button"
        className="ghost"
        aria-label="Dismiss notification"
        onClick={onDismiss}
      >
        Dismiss
      </button>
    </div>
  );
}
