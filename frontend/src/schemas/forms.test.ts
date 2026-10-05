import { describe, expect, it } from 'vitest';
import {
  flagCreateSchema,
  groupCreateSchema,
  groupEditSchema,
  KEY_MESSAGE,
  slugify,
} from './forms';

describe('form rules = backend rules (spec 4.2)', () => {
  it.each(['Orders', '1abc', 'a', 'has space', '-ab', 'a'.repeat(51)])(
    '[AC-GRP-3] key %s is invalid',
    (key) => {
      const r = groupCreateSchema.safeParse({ key, name: 'N', description: '' });
      expect(r.success).toBe(false);
      expect(r.error?.issues[0]?.message).toBe(KEY_MESSAGE);
      expect(flagCreateSchema.safeParse({ key, description: '', enabled: false }).success).toBe(
        false,
      );
    },
  );

  it('valid keys, trimmed names and code-point lengths', () => {
    expect(groupCreateSchema.safeParse({ key: 'ab', name: 'N', description: '' }).success).toBe(
      true,
    );
    expect(
      groupCreateSchema.safeParse({ key: 'a' + 'b'.repeat(49), name: 'N', description: '' })
        .success,
    ).toBe(true);
    expect(
      groupEditSchema.safeParse({ name: '   ', description: '' }).error?.issues[0]?.message,
    ).toBe('Name is required');
    expect(groupEditSchema.safeParse({ name: '🚀'.repeat(100), description: '' }).success).toBe(
      true,
    );
    expect(
      groupEditSchema.safeParse({ name: '🚀'.repeat(101), description: '' }).error?.issues[0]
        ?.message,
    ).toBe('Name must be at most 100 characters');
    expect(
      groupEditSchema.safeParse({ name: `  ${'x'.repeat(100)}  `, description: '' }).success,
    ).toBe(true);
    expect(groupEditSchema.safeParse({ name: 'N', description: '🚀'.repeat(500) }).success).toBe(
      true,
    );
    expect(
      groupEditSchema.safeParse({ name: 'N', description: 'x'.repeat(501) }).error?.issues[0]
        ?.message,
    ).toBe('Description must be at most 500 characters');
  });

  it('slug rule (spec 8.4)', () => {
    expect(slugify('My New Group')).toBe('my-new-group');
    expect(slugify('  Ünïcode & More!! ')).toBe('n-code-more');
    expect(slugify('x'.repeat(60))).toHaveLength(50);
    expect(slugify('--a--')).toBe('a');
  });
});
