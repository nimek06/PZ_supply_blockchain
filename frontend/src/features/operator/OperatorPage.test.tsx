import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { renderWithProviders } from '../../../tests/render'
import { OperatorPage } from './OperatorPage'

it('zły numer partii nie idzie do backendu', async () => {
  renderWithProviders(<OperatorPage />, { route: '/operator?demo=1' })
  await userEvent.type(screen.getByLabelText('Numer partii'), 'B-26-1')
  await userEvent.click(screen.getByRole('button', { name: 'Pokaż partię' }))
  expect(screen.getByText('Numer partii ma format B-RRRR-NNNN.')).toBeInTheDocument()
  expect(screen.queryByText(/Wczytuję partię/)).not.toBeInTheDocument()
})

it('wpisany numer pokazuje partię z danych demo', async () => {
  renderWithProviders(<OperatorPage />, { route: '/operator?demo=1' })
  expect(screen.getByLabelText(/Dane demonstracyjne/)).toBeChecked()
  await userEvent.type(screen.getByLabelText('Numer partii'), 'b-2026-0008')
  await userEvent.click(screen.getByRole('button', { name: 'Pokaż partię' }))
  expect(await screen.findByText('Partia B-2026-0008')).toBeInTheDocument()
  expect(screen.getByText('ukończona')).toBeInTheDocument()
})
