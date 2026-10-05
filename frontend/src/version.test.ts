import { describe, expect, it } from 'vitest';
import pkg from '../package.json';
import { APP_VERSION } from './version';

describe('version (spec 9.6)', () => {
  it('is the repository version (package.json and VERSION are kept equal by gate 15)', () => {
    expect(APP_VERSION).toBe(pkg.version);
    expect(APP_VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });
});
