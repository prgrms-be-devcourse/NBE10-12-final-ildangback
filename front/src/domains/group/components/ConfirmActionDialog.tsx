import { useEffect, useRef, useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { groupErrorMessage } from "../errors";

export function ConfirmActionDialog({
  title,
  description,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const busy = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    const before = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = before;
      dialog?.close();
    };
  }, []);
  const confirm = async () => {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError(null);
    try {
      await onConfirm();
      onClose();
    } catch (err) {
      setError(groupErrorMessage(err));
    } finally {
      busy.current = false;
      setPending(false);
    }
  };
  return (
    <dialog
      ref={ref}
      aria-labelledby="group-confirm-title"
      onCancel={(event) => {
        if (pending) event.preventDefault();
        else onClose();
      }}
      onClick={(event) => {
        if (!pending && event.target === event.currentTarget) onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-[390px] rounded-2xl bg-white p-5 backdrop:bg-black/40"
    >
      <h2 id="group-confirm-title" className="text-lg font-bold">
        {title}
      </h2>
      <p className="my-4 text-sm leading-relaxed whitespace-pre-line">
        {description}
      </p>
      <FormAlert message={error} />
      <div className="mt-5 flex gap-2">
        <Button variant="secondary" disabled={pending} onClick={onClose}>
          취소
        </Button>
        <Button loading={pending} onClick={confirm}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
