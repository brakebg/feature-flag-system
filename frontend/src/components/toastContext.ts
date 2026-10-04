import { createContext, useContext } from 'react';

export interface ToastApi {
  success: (text: string) => void;
  error: (text: string) => void;
}

/** Spec 8.5: toasts stay for 3 s. */
export const TOAST_MS = 3000;

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) throw new Error('useToast outside ToastProvider');
  return api;
}
