import type { AuditResult, ReviewRequest } from './types'

/** Error carrying the server's ProblemDetail message and HTTP status. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.ok) return (await res.json()) as T
  let detail = `${res.status} ${res.statusText}`
  try {
    const body = (await res.json()) as { detail?: string }
    if (body.detail) detail = body.detail
  } catch {
    // non-JSON error body: keep the status line
  }
  throw new ApiError(res.status, detail)
}

/** Runs both stages synchronously; expect roughly 10 seconds for a 3-page contract. */
export async function audit(file: File): Promise<AuditResult> {
  const form = new FormData()
  form.append('file', file)
  return parse(await fetch('/api/audit', { method: 'POST', body: form }))
}

export async function getAudit(id: string): Promise<AuditResult> {
  return parse(await fetch(`/api/audit/${encodeURIComponent(id)}`))
}

/** Returns the full updated result; replace UI state from it. */
export async function review(id: string, req: ReviewRequest): Promise<AuditResult> {
  return parse(
    await fetch(`/api/audit/${encodeURIComponent(id)}/review`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    }),
  )
}
