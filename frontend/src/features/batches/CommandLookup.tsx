import { Card } from '../../components/ui/Card'
import { useCommandStatus } from './batchQueries'
import { CommandStatusCard } from './CommandStatusCard'
import type { DataSource } from './dataSource'
import { QueryError } from './QueryError'

export function CommandLookup({ source, commandId }: { source: DataSource; commandId: string | null }) {
  const query = useCommandStatus(source, commandId)

  if (commandId === null) return null
  if (query.isPending) {
    return (
      <Card>
        <p className="text-sm text-slate-600" role="status">
          Wczytuję status polecenia…
        </p>
      </Card>
    )
  }
  if (query.isError) return <QueryError error={query.error} what="polecenie" />
  return <CommandStatusCard command={query.data} source={source} />
}
