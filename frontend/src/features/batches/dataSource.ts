import { useSearchParams } from 'react-router'

// Źródło danych widoków: prawdziwe API albo jawnie oznaczone dane demo (`?demo=1`).
export type DataSource = 'api' | 'demo'

export function useDataSource(): [DataSource, (next: DataSource) => void] {
  const [params, setParams] = useSearchParams()
  const source: DataSource = params.get('demo') === '1' ? 'demo' : 'api'
  const setSource = (next: DataSource) =>
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        if (next === 'demo') updated.set('demo', '1')
        else updated.delete('demo')
        return updated
      },
      { replace: true },
    )
  return [source, setSource]
}
