# Kuba — tydzień 4

**Cel:** frontend pokazuje rzeczywiste lokalne dane i postęp poleceń,
a nie tylko fixture'y.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 4](../week4-plan.md), sekcja P4.3.
Wejście: [Twój tydzień 3](../../week3/plans/kuba-plan.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W4-07 [Plan:4.3] T022: podłączyć GET partii/numeru partii oraz
  `GET /commands/{id}` zgodnie z OpenAPI. Polling kończyć na terminalnym
  wyniku; poprawnie prezentować freshness/stale, `401`, `403`, `404`
  i błąd sieci.
- [ ] W4-08 [Plan:4.3] T022: podłączyć formularz utworzenia partii
  z SIWE/Bearer i `Idempotency-Key`. Zachować klucz dla ponowienia tego samego
  zamiaru. Pokazywać pending/confirmed/failed, etap 0 i oczekiwanie QA;
  samo `202` nie oznacza gotowej partii.
- [ ] W4-09 [Plan:4.3] T032/T025: sprawdzić lokalny portfel i QR/URL
  z telefonu przez HTTPS. Opisać osiągalny adres aplikacji, RPC i gateway
  oraz użycie wyłącznie testowych kont Anvila. Nie sugerować, że pełny
  paszport lub wdrożenie aplikacji na Amoy są już gotowe.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W4-07: klient GET i polling | dane z lokalnego API |
| Wtorek | W4-07: freshness, statusy i błędy | rozróżnienie oczekiwania od potwierdzenia |
| Środa | W4-08: tworzenie partii i idempotencja | rzeczywiste polecenie z UI |
| Czwartek | W4-08/W4-09: pełne stany i telefon | wynik sprawdzenia HTTPS/QR |
| Piątek | demonstracja na prawdziwych danych z indexera | partia/etap 0 widoczne w UI |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium: `frontend/src/lib/api/client.ts`,
`frontend/src/features/batches/`, `frontend/src/features/operator/`
i `frontend/README.md`. Korzystaj z `shared/schemas/openapi.yaml`;
zmiany kontraktu API uzgadniaj z Mikołajem.

Status pochodzi z backendu/indexera, nie z receipt odczytanego przez UI.
Fixture'y mogą pomagać w pracy przed integracją, ale nie są dowodem odbioru.
Nie wystawiaj publicznie RPC Anvila ani administracyjnego API Kubo.

## Zależności i odbiór

W4-07 wymaga W3-08/W3-09; prawdziwa freshness czeka na W4-03 Adama.
W4-08 wymaga W4-07 i W3-05. W4-09 wymaga W3-07/W3-08.
Przegląd: Robert dla UX, Mikołaj dla API.

Odbiór: utworzenie partii z UI, odczyt prawdziwych statusów, rozróżnienie
`202`/pending/confirmed/failed i poprawna freshness. URL HTTPS/QR osiągalny
z telefonu; brak urządzenia lub integracji zapisany jawnie.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-002/009/010 (przygotowanie)/012; mapowanie w planie wspólnym.

**Poza zakresem:** pełny paszport, panel podpisu QA, przełącznik Amoy
i publiczne wdrożenie frontendu.
