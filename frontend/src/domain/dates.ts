/** Calendar date in the Irish household timezone, matching the backend validation clock. */
export function todayInDublin(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Dublin', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const find = (type: string) => parts.find(part => part.type === type)?.value;
  return [find('year'), find('month'), find('day')].join('-');
}
