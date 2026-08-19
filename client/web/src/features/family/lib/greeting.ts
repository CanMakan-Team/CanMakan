/**
 * Time-of-day greeting helpers for the personal home page header.
 *
 * @author Amelia
 */

export type GreetingPeriod = 'morning' | 'afternoon' | 'evening'

/**
 * Resolve which part of the day a given moment falls into, using the
 * viewer's local clock (hours 0-23):
 * - morning: 12:00 AM up to (not including) 12:00 PM
 * - afternoon: 12:00 PM up to (not including) 6:00 PM
 * - evening: 6:00 PM up to midnight
 */
export function getGreetingPeriod(date: Date = new Date()): GreetingPeriod {
  const hour = date.getHours()
  if (hour < 12) return 'morning'
  if (hour < 18) return 'afternoon'
  return 'evening'
}
