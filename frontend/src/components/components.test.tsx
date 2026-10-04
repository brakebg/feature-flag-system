import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { ConfirmDialog } from './ConfirmDialog';
import { EmptyState } from './EmptyState';
import { Modal } from './Modal';
import { TextField } from './TextField';
import { ToastProvider } from './Toast';
import { TOAST_MS, useToast } from './toastContext';
import { Toggle } from './Toggle';

describe('Modal (spec 8.4, 8.7)', () => {
  it('is a dialog named by its title, traps focus, closes on Escape and on Close', async () => {
    const onClose = vi.fn();
    render(
      <>
        <button type="button">outside</button>
        <Modal title="New group" onClose={onClose} footer={<Button>Cancel</Button>}>
          <TextField label="Key" />
        </Modal>
      </>,
    );
    const dialog = screen.getByRole('dialog', { name: 'New group' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();

    const user = userEvent.setup();
    await user.tab();
    expect(screen.getByLabelText('Key')).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('Enter in a field submits the form', async () => {
    const onSubmit = vi.fn();
    render(
      <Modal
        title="T"
        onClose={() => {}}
        onSubmit={onSubmit}
        footer={<button type="submit">Save changes</button>}
      >
        <TextField label="Name" />
      </Modal>,
    );
    await userEvent.setup().type(screen.getByLabelText('Name'), 'x{Enter}');
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});

describe('ConfirmDialog (spec 8.5)', () => {
  it('is an alertdialog named by its title with Cancel and the confirm button', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <ConfirmDialog
        title="Delete flag orders.a?"
        text="Services reading it will get 404."
        confirmLabel="Delete"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );
    const dialog = screen.getByRole('alertdialog', { name: 'Delete flag orders.a?' });
    expect(dialog).toHaveAccessibleDescription('Services reading it will get 404.');
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    await user.keyboard('{Escape}');
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('the confirm button can be disabled', () => {
    render(
      <ConfirmDialog
        title="T"
        text="x"
        confirmLabel="Delete"
        confirmDisabled
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled();
  });
});

describe('Toggle (spec 8.7)', () => {
  function Harness() {
    const [on, setOn] = useState(false);
    return <Toggle checked={on} onChange={setOn} label="Toggle orders.a" />;
  }

  it('is a switch with aria-checked that flips on click', async () => {
    render(<Harness />);
    const sw = screen.getByRole('switch', { name: 'Toggle orders.a' });
    expect(sw.tagName).toBe('BUTTON');
    expect(sw).toHaveAttribute('aria-checked', 'false');
    await userEvent.setup().click(sw);
    expect(sw).toHaveAttribute('aria-checked', 'true');
  });

  it('a disabled switch does not change', async () => {
    const onChange = vi.fn();
    render(<Toggle checked={false} onChange={onChange} label="x" disabled />);
    await userEvent.setup().click(screen.getByRole('switch', { name: 'x' }));
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe('Toast (spec 8.5)', () => {
  function Trigger() {
    const toast = useToast();
    return (
      <>
        <button type="button" onClick={() => toast.success('Group orders created')}>
          ok
        </button>
        <button type="button" onClick={() => toast.error('Could not update flag orders.a')}>
          fail
        </button>
      </>
    );
  }

  it('success is a status, error is an alert, both disappear after 3 s', async () => {
    vi.useFakeTimers();
    try {
      render(
        <ToastProvider>
          <Trigger />
        </ToastProvider>,
      );
      act(() => screen.getByRole('button', { name: 'ok' }).click());
      act(() => screen.getByRole('button', { name: 'fail' }).click());
      expect(screen.getByRole('status')).toHaveTextContent('Group orders created');
      expect(screen.getByRole('alert')).toHaveTextContent('Could not update flag orders.a');
      expect(TOAST_MS).toBe(3000);
      act(() => vi.advanceTimersByTime(2999));
      expect(screen.getByRole('status')).toBeInTheDocument();
      act(() => vi.advanceTimersByTime(1));
      expect(screen.queryByRole('status')).toBeNull();
      expect(screen.queryByRole('alert')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('useToast needs the provider', () => {
    function Bare() {
      useToast();
      return null;
    }
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Bare />)).toThrow('useToast outside ToastProvider');
  });
});

describe('TextField (spec 8.5)', () => {
  it('links the error with aria-describedby and sets aria-invalid', () => {
    render(
      <TextField
        label="Key"
        error="Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter"
      />,
    );
    const input = screen.getByLabelText('Key');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription(
      'Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter',
    );
  });

  it('without an error the field is not invalid', () => {
    render(<TextField label="Name" />);
    expect(screen.getByLabelText('Name')).not.toHaveAttribute('aria-invalid');
  });

  it('shows a prefix and a read-only value', () => {
    render(<TextField label="Key" prefix="orders." value="new-checkout" readOnly />);
    expect(screen.getByText('orders.')).toBeInTheDocument();
    expect(screen.getByLabelText('Key')).toHaveAttribute('readonly');
  });
});

describe('EmptyState', () => {
  it('shows the text and the action', () => {
    render(<EmptyState text="No groups yet" action={<Button>Create your first group</Button>} />);
    expect(screen.getByText('No groups yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create your first group' })).toBeInTheDocument();
  });
});
