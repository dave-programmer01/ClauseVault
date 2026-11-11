import { useState, type FormEvent } from 'react'
import { audit, ApiError } from './api'
import type { AuditResult } from './types'
import './App.css'

type Phase = { kind: 'idle' } | { kind: 'running'; filename: string } | { kind: 'error'; message: string } | { kind: 'done' }

export default function App() {
  const [file, setFile] = useState<File | null>(null)
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' })
  const [result, setResult] = useState<AuditResult | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!file) return
    setPhase({ kind: 'running', filename: file.name })
    try {
      setResult(await audit(file))
      setPhase({ kind: 'done' })
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not reach the ClauseVault server.'
      setPhase({ kind: 'error', message })
    }
  }

  return (
    <main className="app">
      <header className="app-header">
        <h1>ClauseVault</h1>
        <p className="tagline">Private contract risk audit, verified in a NEAR AI confidential TEE.</p>
      </header>

      <form className="upload" onSubmit={onSubmit}>
        <label className="file-label">
          <input
            type="file"
            accept=".pdf,.txt,application/pdf,text/plain"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            disabled={phase.kind === 'running'}
          />
          <span>{file ? file.name : 'Choose a PDF or .txt contract (up to 12,000 characters)'}</span>
        </label>
        <button type="submit" disabled={!file || phase.kind === 'running'}>
          {phase.kind === 'running' ? 'Auditing…' : 'Audit contract'}
        </button>
      </form>

      {phase.kind === 'running' && (
        <p className="status" role="status">
          Running two TEE stages on {phase.filename}: clause extraction, then risk flagging. About 10 seconds.
        </p>
      )}
      {phase.kind === 'error' && (
        <p className="status error" role="alert">
          {phase.message}
        </p>
      )}

      {result && (
        <section className="result">
          <h2>
            {result.filename}: {result.clauses.length} clauses, {result.risks.length} risks, verified{' '}
            {result.receipts.map((r) => (r.verified ? 'yes' : 'NO')).join(' / ')}
          </h2>
          {/* Checkpoint: raw JSON. Risk cards, badges and the proof panel replace this next. */}
          <pre className="raw-json">{JSON.stringify(result, null, 2)}</pre>
        </section>
      )}
    </main>
  )
}
