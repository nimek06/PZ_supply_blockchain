import { createBrowserRouter } from 'react-router'
import { OperatorPage } from '../features/operator/OperatorPage'
import { PassportPage } from '../features/passport/PassportPage'
import { QaPage } from '../features/qa/QaPage'
import { HomePage } from './HomePage'
import { Layout } from './Layout'
import { NotFoundPage, RouteErrorPage } from './RouteErrorPage'

// Paszport jest publiczny i nie wymaga portfela. /operator i /qa tylko ukrywają akcje,
// prawdziwą autoryzację robi backend.
export const router = createBrowserRouter([
  {
    element: <Layout />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'passport/:batchNumber', element: <PassportPage /> },
      { path: 'operator', element: <OperatorPage /> },
      { path: 'qa', element: <QaPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
