/** A rollover day is a whole day of the month, 1 to 31. */
export function isValidRolloverDay(day: number | null): day is number {
  return day !== null && Number.isInteger(day) && day >= 1 && day <= 31
}
