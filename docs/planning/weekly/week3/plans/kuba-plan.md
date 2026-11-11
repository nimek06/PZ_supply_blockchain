# Kuba — tydzień 3

**Cel:** uruchomić frontend lokalny, logowanie portfelem i pierwsze widoki.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 3](../week3-plan.md), sekcja P3.3.
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).
Kontynuacja: [tydzień 4](../../week4/plans/kuba-plan.md).

## Zadania

- [ ] W3-07 [Plan:3.3] T005/T032: zainicjalizować React/TypeScript/Vite
  w istniejącym katalogu, zachowując README. Dodać manifest, lockfile,
  providery/router, type-check/lint/build i lokalny HTTPS. Wybrać bibliotekę
  skanera QR po sprawdzeniu jej na telefonie.
- [ ] W3-08 [Plan:3.3] T008/T011: odtworzyć wspólny wektor podpisu w TypeScript;
  podłączyć portfel do Anvila i SIWE challenge/verify/logout. Bearer tylko
  w pamięci, czyszczenie sesji po zmianie konta/sieci, jawna odmowa podpisu.
- [ ] W3-09 [Plan:3.3] T022: przygotować widoki partii i statusu zgodne
  z OpenAPI, początkowo na fixture'ach. Pokazać loading/empty/pending/error,
  podłączyć dostępne odczyty lokalne. Dane demonstracyjne oznaczyć;
  `202` i `stale=true` nie oznaczają ukończenia produkcji.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W3-07: Vite/TS/router/providery | startujący frontend |
| Wtorek | W3-08: wektor podpisu i konfiguracja Anvila | zgodność kryptografii, właściwa sieć |
| Środa | W3-08: SIWE/Bearer/logout | rzeczywista sesja backendu |
| Czwartek | W3-07/W3-09: HTTPS/QR, widoki i stany | czytelny UI, wynik sprawdzenia telefonu |
| Piątek | demonstracja i przegląd z Robertem | gotowa podstawa pod rzeczywiste statusy |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium: `frontend/package.json`, lockfile,
`frontend/src/app/`, `frontend/src/lib/blockchain/`,
`frontend/src/lib/api/client.ts`, `frontend/src/features/batches/`,
`frontend/src/features/operator/`, `frontend/tests/` i `frontend/README.md`.
Formaty pochodzą z `shared/schemas/openapi.yaml`
i `shared/fixtures/signature-vector.json`, nie z nowych niezależnych modeli.

Mikołaj utrzymuje OpenAPI; uzgadniaj z nim rozbieżności zamiast obchodzić API.
Nie wracaj do pisania kolejnego adaptera IPFS — Kubo już istnieje.

## Zależności i odbiór

W3-07 można zacząć niezależnie. W3-08/W3-09 wymagają działającej aplikacji
z W3-07; test wektora można przygotować przed gotowym SIWE UI.
Przegląd: Robert dla UI/QR, Adam dla EIP-712.

Odbiór: build z type-checkiem, zgodny wektor, logowanie/wylogowanie, obsługa
odmowy i zmiany konta/sieci. Telefon otwiera osiągalny URL HTTPS i skanuje QR.
Brak urządzenia odnotuj jako niewykonaną weryfikację, nie zaliczenie.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-003/004/009/010/012/014; mapowanie w planie wspólnym.

**Poza zakresem:** pełny panel QA i paszport, przełącznik środowisk Amoy,
publikowanie aplikacji na Polygon i przechowywanie sekretów użytkownika.
