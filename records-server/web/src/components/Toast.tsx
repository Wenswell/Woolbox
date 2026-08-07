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
      <button type="button" className="icon-button toast-close" aria-label="Dismiss notification" title="Dismiss" onClick={onDismiss}><X size={16} /></button>
    </div>
  );
}
import { X } from "lucide-react";
