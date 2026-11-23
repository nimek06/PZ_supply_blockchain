import { Badge } from '../../components/ui/Badge'
import { Card } from '../../components/ui/Card'
import { Field, Fields } from '../../components/ui/Field'
import { Mono } from '../../components/ui/Mono'
import type { CommandStatus } from '../../lib/api/types'
import { describeCommand } from './commandPresentation'
import type { DataSource } from './dataSource'
import { DemoBadge } from './DemoNotice'
import { stageName } from './stages'

export function CommandStatusCard({ command, source }: { command: CommandStatus; source: DataSource }) {
  const view = describeCommand(command)
  const what = command.type === 'CREATE_BATCH' ? 'Utworzenie partii' : `Etap ${command.stageId ?? '?'}: ${stageName(command.stageId ?? -1)}`
  return (
    <Card
      title={what}
      actions={
        <div className="flex gap-2">
          <DemoBadge source={source} />
          <Badge tone={view.tone}>{view.label}</Badge>
        </div>
      }
    >
      <p className="text-sm">{view.description}</p>
      {!view.terminal && <p className="mt-1 text-xs text-stone-500">Status odświeża się automatycznie.</p>}
      <div className="mt-4">
        <Fields>
          {command.batchNumber && <Field label="Partia">{command.batchNumber}</Field>}
          {command.cid && (
            <Field label="CID raportu">
              <Mono>{command.cid}</Mono>
            </Field>
          )}
          {command.txHash && (
            <Field label="Transakcja">
              <Mono>{command.txHash}</Mono>
            </Field>
          )}
          {command.failureCode && (
            <Field label="Kod błędu">
              <Mono>{command.failureCode}</Mono>
            </Field>
          )}
          <Field label="ID polecenia">
            <Mono>{command.commandId}</Mono>
          </Field>
        </Fields>
      </div>
    </Card>
  )
}
