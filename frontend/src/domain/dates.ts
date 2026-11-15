/** Calendar date in the Irish household timezone, matching the backend validation clock. */
export function todayInDublin(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Dublin",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const find = (type: string) =>
    parts.find((part) => part.type === type)?.value;
  return [find("year"), find("month"), find("day")].join("-");
}

/** Date-input display hint; incomplete/invalid edits must never throw during render. */
export function dateInputOffset(value: string, days: number): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isSafeInteger(days))
    return "";
  const date = new Date(value + "T12:00:00Z");
  if (
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== value
  )
    return "";
  date.setUTCDate(date.getUTCDate() + days);
  if (!Number.isFinite(date.getTime())) return "";
  const result = date.toISOString().slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(result) ? result : "";
}
