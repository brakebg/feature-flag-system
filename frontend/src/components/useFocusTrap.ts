import { useEffect, useRef, type RefObject } from 'react';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Spec 8.7: modals trap focus and close on Escape. Focus moves into the dialog on open and back
 * to the element that had it on close. The keys are handled on the document, so the trap also
 * holds when focus has fallen to the page body (for example after a click on plain text).
 */
export function useFocusTrap(ref: RefObject<HTMLElement | null>, onEscape: () => void) {
  const escape = useRef(onEscape);
  useEffect(() => {
    escape.current = onEscape;
  });

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const previous = document.activeElement as HTMLElement | null;
    const items = () => Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE));
    const autofocus = node.querySelector<HTMLElement>('[data-autofocus]');
    (autofocus ?? items()[0] ?? node).focus();

    function onKey(event: KeyboardEvent) {
      if (!node) return;
      if (event.key === 'Escape') {
        event.stopPropagation();
        escape.current();
        return;
      }
      if (event.key !== 'Tab') return;
      const list = items();
      if (list.length === 0) {
        event.preventDefault();
        node.focus();
        return;
      }
      const first = list[0];
      const last = list[list.length - 1];
      const active = document.activeElement;
      const inside = active instanceof HTMLElement && node.contains(active);
      if (!inside) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      } else if (event.shiftKey && active === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      if (previous && previous.isConnected) {
        previous.focus();
      }
    };
  }, [ref]);
}
