import assert from "node:assert/strict";
import test from "node:test";
import { todayInDublin, dateInputOffset } from "../src/domain/dates.ts";

test("uses Dublin date at the summer midnight boundary, not browser timezone", () => {
  assert.equal(todayInDublin(new Date("2026-07-01T22:30:00Z")), "2026-07-01");
  assert.equal(todayInDublin(new Date("2026-07-01T23:30:00Z")), "2026-07-02");
});

test("uses standard time in winter", () => {
  assert.equal(todayInDublin(new Date("2026-11-14T23:30:00Z")), "2026-11-14");
});

test("cleared and invalid date inputs remain editable without a rendering exception", () => {
  for (const value of ["", "2026-", "2026-02-30", "not a date"])
    assert.equal(dateInputOffset(value, 1), "");
  assert.equal(dateInputOffset("2026-11-15", 1), "2026-11-16");
  assert.equal(dateInputOffset("9999-12-31", 1), "");
});
