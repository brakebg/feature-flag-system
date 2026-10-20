import { describe, expect, it } from 'vitest';
import { pieShare } from './pieShare';

// Spec 005 4.5 and AC-UI-4: floor((200 x enabled + total) / (2 x total)) followed by %, 0% with
// no flags. Examples from the criterion: 1 of 8 = 13%, 23 of 40 = 58%, 2 of 3 = 67%.
describe('group pie share (spec 005 4.5)', () => {
  it.each([
    [1, 8, '13%'],
    [23, 40, '58%'],
    [2, 3, '67%'],
    [0, 0, '0%'],
    [0, 5, '0%'],
    [5, 5, '100%'],
    [1, 2, '50%'],
    [1, 3, '33%'],
    [1, 200, '1%'],
    [1, 201, '0%'],
  ])('[AC-UI-4] %i of %i flags on gives %s', (enabled, total, share) => {
    expect(pieShare(enabled, total)).toBe(share);
  });
});
