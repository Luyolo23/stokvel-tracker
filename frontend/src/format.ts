export function rand(cents: number): string {
  return 'R' + (cents / 100).toLocaleString('en-ZA', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function monthLabel(month: string): string {
  return new Date(`${month}-01T00:00:00`).toLocaleDateString('en-ZA', {
    month: 'long',
    year: 'numeric',
  })
}

export function dateTime(iso: string | null): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('en-ZA', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}
