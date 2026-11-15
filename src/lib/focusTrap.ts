export interface FocusTrapOptions {
  onEscape?: () => void;
  initialFocus?: HTMLElement | null;
}

export interface FocusTrap {
  activate: () => void;
  deactivate: () => void;
}

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function getFocusable(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true'
  );
}

export function createFocusTrap(
  container: HTMLElement,
  options: FocusTrapOptions = {}
): FocusTrap {
  let previousFocus: HTMLElement | null = null;
  let active = false;

  const handleKeyDown = (event: KeyboardEvent) => {
    if (!active) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      options.onEscape?.();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = getFocusable(container);
    if (focusable.length === 0) {
      event.preventDefault();
      container.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const current = document.activeElement as HTMLElement | null;

    if (event.shiftKey && (current === first || !container.contains(current))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && current === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return {
    activate() {
      if (active) return;
      active = true;
      previousFocus = document.activeElement as HTMLElement | null;
      document.addEventListener('keydown', handleKeyDown, true);
      const focusInitial = () => {
        const target = options.initialFocus ?? getFocusable(container)[0] ?? container;
        target.focus();
      };
      focusInitial();
      window.setTimeout(focusInitial, 0);
    },
    deactivate() {
      if (!active) return;
      active = false;
      document.removeEventListener('keydown', handleKeyDown, true);
      previousFocus?.focus();
      previousFocus = null;
    },
  };
}
