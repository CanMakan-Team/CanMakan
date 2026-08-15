import type { ConsumerTrendsQuery } from "./consumerTrendsTypes";

function toSingaporeDate(date: Date): string {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Singapore",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = Object.fromEntries(
    formatter
      .formatToParts(date)
      .filter((part) => part.type === "year" || part.type === "month" || part.type === "day")
      .map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function buildPeriodQuery(
  days: number,
  category?: string,
  now = new Date(),
): ConsumerTrendsQuery {
  const to = toSingaporeDate(now);
  const [year, month, day] = to.split("-").map(Number);
  const from = new Date(Date.UTC(year, month - 1, day - (days - 1))).toISOString().slice(0, 10);

  return {
    from,
    to,
    category,
  };
}
