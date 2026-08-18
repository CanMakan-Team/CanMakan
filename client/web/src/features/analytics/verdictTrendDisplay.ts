/**
 * Largest-remainder percentages so Safe / Warning / Unsafe always sum to 100
 * (avoids whole-number rounding that can produce 29+43+29 = 101).
 */
export function sharePercents(counts: number[], total: number): number[] {
  if (total === 0) return counts.map(() => 0)
  const raw = counts.map((count) => (count / total) * 100)
  const floors = raw.map((value) => Math.floor(value))
  const remainder = 100 - floors.reduce((sum, value) => sum + value, 0)
  const byFraction = raw
    .map((value, index) => ({ index, fraction: value - floors[index] }))
    .sort((left, right) => right.fraction - left.fraction)
  const result = [...floors]
  for (let step = 0; step < remainder; step += 1) {
    result[byFraction[step].index] += 1
  }
  return result
}

export function formatPercentLabel(value: number): string {
  return `${value}%`
}
