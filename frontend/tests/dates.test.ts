import assert from 'node:assert/strict';
import test from 'node:test';
import { todayInDublin } from '../src/domain/dates.ts';

test('uses Dublin date at the summer midnight boundary, not browser timezone', () => {
  assert.equal(todayInDublin(new Date('2026-07-01T22:30:00Z')), '2026-07-01');
  assert.equal(todayInDublin(new Date('2026-07-01T23:30:00Z')), '2026-07-02');
});

test('uses standard time in winter', () => {
  assert.equal(todayInDublin(new Date('2026-11-14T23:30:00Z')), '2026-11-14');
});
