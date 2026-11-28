import type {
  AuditEntry, ChairSummary, Claim, ConfirmedPage, Contribution, Me, MemberOverview, OverdueMember,
} from './types'

export class HttpError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    if (res.status === 401) onUnauthorized?.()
    let message =
      res.status === 401
        ? 'Your session expired. Please sign in again.'
        : `Something went wrong (error ${res.status})`
    try {
      const data = await res.json()
      if (data && typeof data.message === 'string') message = data.message
    } catch {
      /* response had no JSON body */
    }
    throw new HttpError(res.status, message)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function login(username: string, password: string): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    body: new URLSearchParams({ username, password }),
  })
  if (!res.ok) throw new HttpError(res.status, 'Wrong username or password')
}

export async function logout(): Promise<void> {
  await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' })
}

/** The claim actions shared by the treasurer and the chair countersign views. */
export interface ClaimApi {
  pending: () => Promise<Claim[]>
  confirmed: (page?: number, size?: number) => Promise<ConfirmedPage>
  confirm: (id: number) => Promise<void>
  reject: (id: number, reason: string) => Promise<void>
  reverse: (id: number, reason: string) => Promise<void>
}

export const api = {
  me: () => request<Me>('/api/me'),
  overview: () => request<MemberOverview>('/api/member/overview'),
  claim: (id: number, reference: string) =>
    request<Contribution>(`/api/member/contributions/${id}/claim`, 'POST', { reference }),
  withdraw: (id: number) =>
    request<Contribution>(`/api/member/contributions/${id}/withdraw`, 'POST'),
  pending: () => request<Claim[]>('/api/treasurer/pending'),
  confirmed: (page = 0, size = 20) =>
    request<ConfirmedPage>(`/api/treasurer/confirmed?page=${page}&size=${size}`),
  overdue: () => request<OverdueMember[]>('/api/treasurer/overdue'),
  confirm: (id: number) => request<void>(`/api/treasurer/contributions/${id}/confirm`, 'POST'),
  reject: (id: number, reason: string) =>
    request<void>(`/api/treasurer/contributions/${id}/reject`, 'POST', { reason }),
  reverse: (id: number, reason: string) =>
    request<void>(`/api/treasurer/contributions/${id}/reverse`, 'POST', { reason }),
  summary: () => request<ChairSummary>('/api/chair/summary'),
  audit: () => request<AuditEntry[]>('/api/chair/audit'),
}

/** Chair-only countersignature of the treasurer's own payments. */
export const chairClaims: ClaimApi = {
  pending: () => request<Claim[]>('/api/chair/treasurer/pending'),
  confirmed: (page = 0, size = 20) =>
    request<ConfirmedPage>(`/api/chair/treasurer/confirmed?page=${page}&size=${size}`),
  confirm: (id: number) => request<void>(`/api/chair/contributions/${id}/confirm`, 'POST'),
  reject: (id: number, reason: string) =>
    request<void>(`/api/chair/contributions/${id}/reject`, 'POST', { reason }),
  reverse: (id: number, reason: string) =>
    request<void>(`/api/chair/contributions/${id}/reverse`, 'POST', { reason }),
}
