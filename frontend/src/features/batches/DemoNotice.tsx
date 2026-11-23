import { Badge } from '../../components/ui/Badge'
import type { DataSource } from './dataSource'

export function DemoBadge({ source }: { source: DataSource }) {
  return source === 'demo' ? <Badge tone="warning">dane demo</Badge> : null
}

export function DataSourceToggle({ source, onChange }: { source: DataSource; onChange: (next: DataSource) => void }) {
  return (
    <label className="inline-flex items-center gap-2 text-xs text-stone-600">
      <input
        type="checkbox"
        className="size-4 accent-stone-900"
        checked={source === 'demo'}
        onChange={(event) => onChange(event.target.checked ? 'demo' : 'api')}
      />
      Dane demonstracyjne (nie z blockchaina)
    </label>
  )
}
