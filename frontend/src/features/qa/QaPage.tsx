import { Card } from '../../components/ui/Card'

// Panel QA z podpisem StageApproval powstaje w tygodniu 6 (T023).
export function QaPage() {
  return (
    <div className="grid gap-6">
      <h1 className="text-xl font-semibold tracking-tight">Kontrola QA</h1>
      <Card>
        <p className="text-sm text-stone-600">
          Tu operator zmiany podpisze StageApproval dla raportu z kontroli jakości. Panel jest w planie na tydzień 6.
        </p>
      </Card>
    </div>
  )
}
