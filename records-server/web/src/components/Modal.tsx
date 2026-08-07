import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

export function Modal({
  title,
  onClose,
  children,
  size = "md",
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  size?: "md" | "lg";
}) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const dialogs = document.querySelectorAll<HTMLElement>("[role='dialog']");
      if (event.key === "Escape" && dialogs.item(dialogs.length - 1) === dialogRef.current) {
        onClose();
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        ref={dialogRef}
        className={`modal modal-${size}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-title">
          <div><p className="eyebrow">Records</p><h2>{title}</h2></div>
          <button type="button" className="icon-button" aria-label="Close" title="Close" onClick={onClose}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}
