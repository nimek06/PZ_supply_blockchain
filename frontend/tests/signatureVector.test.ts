// @vitest-environment node
import { readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { getAddress, hashTypedData, recoverTypedDataAddress, type Address, type Hex } from 'viem'
import { describe, expect, it } from 'vitest'
import { canonicalReport, reportHash } from '../src/lib/blockchain/reportHash'
import { batchIdOf, stageApprovalTypes, stageCompletionTypes, supplyChainDomain } from '../src/lib/blockchain/typedData'

// Wspólny wektor T008 i poprawne fixture'y: TypeScript musi liczyć dokładnie to samo co Python, Java i Solidity.

const FIXTURES = fileURLToPath(new URL('../../shared/fixtures/', import.meta.url))

interface Signed {
  typeString: string
  message: Record<string, unknown>
  digest: Hex
  signature: Hex
  signer?: Address
}

interface Vector {
  domain: { name: string; version: string; chainId: number; verifyingContract: Address }
  batchNumber: string
  report: Record<string, unknown> & { batchId: Hex; stationAddress: Address }
  canonicalReportJcs: string
  reportHash: Hex
  stageCompletion: Signed
  stageApproval?: Signed
}

function load(name: string): Vector {
  return JSON.parse(readFileSync(FIXTURES + name, 'utf8')) as Vector
}

const files = ['signature-vector.json', ...readdirSync(FIXTURES).filter((f) => /^report-valid-stage\d\.json$/.test(f)).sort()]

function domainOf(vector: Vector) {
  const domain = supplyChainDomain(vector.domain.chainId, vector.domain.verifyingContract)
  expect(domain).toEqual(vector.domain)
  return domain
}

function completionMessage(signed: Signed) {
  const m = signed.message
  return {
    batchId: m.batchId as Hex,
    stageId: Number(m.stageId),
    reportHash: m.reportHash as Hex,
    qaPassed: m.qaPassed as boolean,
    deadline: BigInt(m.deadline as number),
  }
}

function approvalMessage(signed: Signed) {
  const m = signed.message
  return {
    batchId: m.batchId as Hex,
    stageId: Number(m.stageId),
    reportHash: m.reportHash as Hex,
    deadline: BigInt(m.deadline as number),
  }
}

function typeString(primary: string, fields: readonly { name: string; type: string }[]): string {
  return `${primary}(${fields.map((f) => `${f.type} ${f.name}`).join(',')})`
}

describe('typy EIP-712 zgadzają się z wektorem', () => {
  const vector = load('signature-vector.json')
  it('StageCompletion', () => {
    expect(typeString('StageCompletion', stageCompletionTypes.StageCompletion)).toBe(vector.stageCompletion.typeString)
  })
  it('StageApproval', () => {
    expect(typeString('StageApproval', stageApprovalTypes.StageApproval)).toBe(vector.stageApproval!.typeString)
  })
})

describe.each(files)('%s', (file) => {
  const vector = load(file)

  it('batchId = keccak256(numer partii)', () => {
    expect(batchIdOf(vector.batchNumber)).toBe(vector.report.batchId)
  })

  it('JCS i reportHash raportu', () => {
    expect(canonicalReport(vector.report)).toBe(vector.canonicalReportJcs)
    expect(reportHash(vector.report)).toBe(vector.reportHash)
  })

  it('podpis stacji StageCompletion', async () => {
    const domain = domainOf(vector)
    const message = completionMessage(vector.stageCompletion)
    expect(message.reportHash).toBe(vector.reportHash)
    const args = { domain, types: stageCompletionTypes, primaryType: 'StageCompletion', message } as const
    expect(hashTypedData(args)).toBe(vector.stageCompletion.digest)
    const signer = await recoverTypedDataAddress({ ...args, signature: vector.stageCompletion.signature })
    expect(signer).toBe(getAddress(vector.report.stationAddress))
  })

  const approval = vector.stageApproval
  if (approval) it('podpis operatora StageApproval, inny klucz niż stacja', async () => {
    const domain = domainOf(vector)
    const message = approvalMessage(approval!)
    const args = { domain, types: stageApprovalTypes, primaryType: 'StageApproval', message } as const
    expect(hashTypedData(args)).toBe(approval!.digest)
    const signer = await recoverTypedDataAddress({ ...args, signature: approval!.signature })
    if (approval!.signer) expect(signer).toBe(getAddress(approval!.signer))
    expect(signer).not.toBe(getAddress(vector.report.stationAddress))
  })

  it('zmiana jednego pola raportu zmienia hash', () => {
    const tampered = { ...vector.report, qaPassed: !vector.report.qaPassed }
    expect(reportHash(tampered)).not.toBe(vector.reportHash)
  })
})

it('sprawdzamy wektor i wszystkie poprawne fixture’y', () => {
  expect(files.length).toBeGreaterThanOrEqual(5)
})
