import { createContext } from "react";

export interface ToastContextValue {
  showToast: (message: string, duration?: number) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);
