export function localToday(): string {
  return new Date(Date.now() - new Date().getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10);
}

export function shiftLocalDate(base: string, days: number): string {
  const date = new Date(`${base}T00:00:00`);
  date.setDate(date.getDate() + days);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10);
}

export function minute(value: number): string {
  const hours = String(Math.floor(value / 60)).padStart(2, "0");
  const minutes = String(value % 60).padStart(2, "0");
  return `${hours}:${minutes}`;
}

export function numberOrNull(value: string | number): number | null {
  return value === "" ? null : Number(value);
}

export function parseWindows(value: string): Array<{ startMinute: number; endMinute: number }> {
  return value
    .split(/\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((window) => {
      const [start, end] = window.split("-");
      return {
        startMinute: timeMinute(start),
        endMinute: timeMinute(end),
      };
    });
}

export function timeMinute(value: string): number {
  const [hour, minuteValue] = value.split(":").map(Number);
  return hour * 60 + minuteValue;
}

export function message(reason: unknown): string {
  return reason instanceof Error ? reason.message : "Request failed.";
}

export function formatDateTime(epoch: number): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(epoch);
}
