// Mirrors the records in dev.clausevault.clausevault.model.

export type Severity = 'HIGH' | 'MEDIUM' | 'LOW'
export type ReviewStatus = 'PENDING' | 'ACCEPTED' | 'DISMISSED'
export type ReviewAction = 'ACCEPT' | 'DISMISS' | 'EDIT'

export interface Clause {
  id: string
  type: string
  title: string
  text: string
}

export interface Risk {
  id: string
  clauseId: string
  category: string
  severity: Severity
  explanation: string
  quote: string
  status: ReviewStatus
  edited: boolean
}

export interface VerificationChecks {
  modelOk: boolean
  requestHashOk: boolean
  responseHashOk: boolean
  signerOk: boolean
  teeSigned: boolean
}

export interface StageReceipt {
  stage: string
  chatId: string
  requestHash: string
  responseHash: string
  signatureText: string
  signature: string
  signingAddress: string
  checks: VerificationChecks
  verified: boolean
  verifyUrl: string
}

export interface AuditResult {
  id: string
  filename: string
  clauses: Clause[]
  risks: Risk[]
  receipts: StageReceipt[]
}

export interface ReviewRequest {
  riskId: string
  action: ReviewAction
  severity?: Severity
  explanation?: string
}
