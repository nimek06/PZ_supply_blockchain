import { Alert } from '../../components/ui/Alert'
import { toApiError } from '../../lib/api/client'

// Wspólny komunikat błędu zapytania. Błąd nigdy nie jest pokazywany jako pusta lista.
export function QueryError({ error, what }: { error: unknown; what: string }) {
  const apiError = toApiError(error)
  if (apiError.isNetworkError) {
    return <Alert tone="danger" title="Brak połączenia z backendem">Sprawdź, czy backend działa, i spróbuj ponownie.</Alert>
  }
  if (apiError.status === 404) {
    return <Alert tone="neutral" title={`Nie znaleziono: ${what}`} />
  }
  if (apiError.status === 401) {
    return <Alert tone="warning" title="Wymagane logowanie">Zaloguj się portfelem (SIWE), żeby to zobaczyć.</Alert>
  }
  if (apiError.status === 403) {
    return <Alert tone="warning" title="Brak uprawnień">Twoje konto nie ma roli wymaganej do tej operacji.</Alert>
  }
  return (
    <Alert tone="danger" title={`Błąd: ${what}`}>
      {apiError.message}
      {apiError.correlationId && <span className="block text-xs opacity-75">ID zgłoszenia: {apiError.correlationId}</span>}
    </Alert>
  )
}
