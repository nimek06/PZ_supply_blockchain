import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../../tests/render'
import { BatchLookup } from './BatchLookup'
import { CommandLookup } from './CommandLookup'
import { DEMO_BATCH_NUMBERS } from './demoData'

describe('BatchLookup na danych demo', () => {
  it('bez numeru prosi o numer', () => {
    renderWithProviders(<BatchLookup source="demo" batchNumber={null} />)
    expect(screen.getByText(/Wpisz numer partii/)).toBeInTheDocument()
  })

  it('najpierw ładowanie, potem partia oznaczona jako demo', async () => {
    renderWithProviders(<BatchLookup source="demo" batchNumber={DEMO_BATCH_NUMBERS.inProgress} />)
    expect(screen.getByRole('status')).toHaveTextContent(/Wczytuję/)
    expect(await screen.findByText(`Partia ${DEMO_BATCH_NUMBERS.inProgress}`)).toBeInTheDocument()
    expect(screen.getByText('dane demo')).toBeInTheDocument()
    expect(screen.getByText('w produkcji')).toBeInTheDocument()
    expect(screen.getAllByText('potwierdzony')).toHaveLength(2)
    expect(screen.getByText('następny')).toBeInTheDocument()
  })

  it('nieaktualna projekcja ma ostrzeżenie', async () => {
    renderWithProviders(<BatchLookup source="demo" batchNumber={DEMO_BATCH_NUMBERS.stale} />)
    expect(await screen.findByText(/Dane mogą być nieaktualne/)).toBeInTheDocument()
  })

  it('404 to brak partii, nie pusty wynik', async () => {
    renderWithProviders(<BatchLookup source="demo" batchNumber={DEMO_BATCH_NUMBERS.notFound} />)
    expect(await screen.findByText(/Nie znaleziono/)).toBeInTheDocument()
  })

  it('brak sieci to widoczny błąd', async () => {
    renderWithProviders(<BatchLookup source="demo" batchNumber={DEMO_BATCH_NUMBERS.networkError} />, {})
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(/Brak połączenia/)
  })
})

describe('CommandLookup na danych demo', () => {
  it('202/ACCEPTED nie wygląda jak ukończone', async () => {
    renderWithProviders(<CommandLookup source="demo" commandId="00000000-0000-4000-8000-000000000001" />)
    expect(await screen.findByText('Przyjęte')).toBeInTheDocument()
    expect(screen.getByText(/jeszcze nic nie trafiło na blockchain/)).toBeInTheDocument()
    expect(screen.queryByText('Potwierdzone')).not.toBeInTheDocument()
  })

  it('StageRejected pokazuje odrzucenie', async () => {
    renderWithProviders(<CommandLookup source="demo" commandId="00000000-0000-4000-8000-000000000005" />)
    expect(await screen.findByText('Odrzucone')).toBeInTheDocument()
  })
})
