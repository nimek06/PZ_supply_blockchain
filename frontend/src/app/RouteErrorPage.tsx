import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Alert } from '../components/ui/Alert'

export function NotFoundPage() {
  return (
    <Alert tone="neutral" title="Nie ma takiej strony">
      <Link to="/" className="underline">
        Wróć na start
      </Link>
    </Alert>
  )
}

export function RouteErrorPage() {
  const error = useRouteError()
  const message = isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : error instanceof Error ? error.message : 'Nieznany błąd'
  return (
    <main className="mx-auto max-w-xl p-6">
      <Alert tone="danger" title="Coś poszło nie tak">
        <p>{message}</p>
        <Link to="/" className="underline">
          Wróć na start
        </Link>
      </Alert>
    </main>
  )
}
