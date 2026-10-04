import { useEffect, type RefObject } from 'react';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Spec 8.7: modals trap focus and close on Escape. Focus moves into the dialog on open and back
 * to the element that had it on close.
 */
export function useFocusTrap(ref: RefObject<HTMLElement | null>, onEscape: () => void) {
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const previous = document.activeElement as HTMLElement | null;
    const items = () => Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE));
    const autofocus = node.querySelector<HTMLElement>('[data-autofocus]');
    (autofocus ?? items()[0] ?? node).focus();

    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onEscape();
        return;
      }
      if (event.key !== 'Tab') return;
      const list = items();
      if (list.length === 0) {
        event.preventDefault();
        return;
      }
      const first = list[0];
      const last = list[list.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    node.addEventListener('keydown', onKey);
    return () => {
      node.removeEventListener('keydown', onKey);
      previous?.focus?.();
    };
    // The trap is set up once per open dialog.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reason: onEscape changes every render; the trap must not restart
  }, [ref]);
}
