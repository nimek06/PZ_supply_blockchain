# Podsumowanie prac — tydzień 3

Stan na 2026-11-23. Podsumowanie obejmuje zmiany z PR-ów tygodnia 3 zmergowanych do
`main` oraz pracę Osoby 4 nad scenariuszami i bramką integracyjną (W3-10..W3-12).
Podział ról: [team-split](../../team-split.md); plan: [week3-plan](../week3-plan.md).

## Co weszło do `main` (fakty z historii)

| PR / gałąź | Zakres |
|---|---|
| #12 `mikolaj/week3+4` | trwałe przyjęcie, sender relayera, worker i odzysk (bez indexera) |
| #14 `adam-week3` (+ `week4`) | **indexer** `ChainEventIndexer`, porty `ChainEventStore`/`BatchProjection`/`IndexerCheckpoint`, `JdbcChainEventStore`, migracja `V7`, `BatchQueryService` |
| #13 `kuba/week3` | **frontend** Vite/React (router, providery, sesja SIWE, widoki partii/operatora, QR) + `schema.gen.ts` |
| harness E2E | `backend/scripts/local-e2e/*` + `running-local-e2e.md`: stos → kontrakty → backend → stacja → symulator |

Uwaga: indexer i frontend powstały wcześniej niż zakładał plan tygodnia 3 (Adam/Kuba
dowieźli je razem z tygodniem 4). Nie zmieniamy z tego powodu zakresu W3.

## Osoba 4 — W3-10, W3-11, W3-12

**W3-10 (dopasowanie symulatora do realnego OpenAPI).** `client.py` używa pola
`failureCode` (tak w `openapi.yaml` i w `CommandStatusResponse`), modele
`CommandAccepted`/`CommandStatus` niosą `type`; docstringi odnoszą się do istniejącego
`shared/schemas/openapi.yaml`. `runner.py` raportuje `failureCode`. README modułu,
testów i skryptów wskazują kanoniczne uruchomienie (`backend/scripts/running-local-e2e.md`).
Bez nowego runnera i bez zmiany zachowania (accept-only, deterministyczny seed).
Testy: `python -m pytest` w `simulator/` → **381 passed, 1 skipped** (było 379; +2 nowe
testy pól odpowiedzi).

**W3-11 (przyjęcie przez lokalne API).** Uruchomiony zespołowy harness
`./backend/scripts/local-e2e/run-e2e.sh` — sukces 5/5, `202` na etapy 0 i 1 partii
`0x79709e74…582f1`. Zgłoszenia przez API (backend z E2E):

| Sprawdzenie | Wynik |
|---|---|
| poprawne zgłoszenie (świeży certyfikat) ×10 | `202`, jeden `commandId` (`8ed4d701…`) |
| te same bajty po restarcie backendu | `202`, ten sam `commandId` (`a122f9b0…`) |
| naruszenia profilu etapów 0..3 | `422 PROFILE_VIOLATION` |
| zmutowany podpis | `403 INVALID_SIGNATURE` |
| nieznany / wygasły / niezaufany certyfikat | `403 CERTIFICATE_INVALID` |

W bazie `supplychain_e2e` polecenia leżą trwale jako `ACCEPTED` (brak `CONFIRMED` —
worker/relayer wyłączony w harnessie, indexer nie ma zdarzenia do potwierdzenia).
Szczegóły i tabela: `simulator/scenarios/README.md` (sekcja „Wynik W3-11”).

**W3-12 (odbiór i dokumentacja).** Ten plik oraz instrukcja uruchomienia i resetu w
`docs/guides/development.md` (sekcja „Lokalny przebieg E2E”).

## Blokady i otwarte punkty (jawnie)

1. **Brak `CONFIRMED`** w tygodniu 3: `start-backend.sh` nie ustawia
   `RELAYER_WORKER_ENABLED`/`RELAYER_PRIVATE_KEY`, więc nie ma transakcji on-chain.
   Pełny pion i `outcome` to tydzień 4 (W4-04..W4-06).
2. **Statyczne fixture'y raportów** mają `issuedAt = 1800000000` (przyszłość) i stały
   `deadline`; weryfikator odrzuca `now < issuedAt` (`NOT_YET_VALID`). Pozytywną ścieżkę
   pokrywa certyfikat wystawiany na bieżąco przez `prepare-station.sh`; fixture'y służą
   do negatywów i walidacji schematu. Do rozważenia: regeneracja `issuedAt` w fixture'ach
   albo jawna adnotacja (zmiana w `shared/` wymaga zgody osób 1-3).
3. **Odczyt `GET /api/v1/commands/{id}`** wymaga tokenu Bearer (SIWE), którego symulator
   nie tworzy; prywatny status czyta frontend. Scenariusz pozostaje accept-only.
4. **Partia w scenariuszu** nie jest tworzona (harness nie woła `POST /batches`);
   backend przyjmuje zgłoszenie (brak sprawdzenia istnienia partii on-chain), a
   `getBatch` zwraca `nextStage=0`. Tworzenie partii z UI to W4-08.

## Przekazanie do tygodnia 4

- Działający harness E2E i kanoniczny adres kontraktu `0x5FbDB2315678afecb367f032d93F642f64180aa3`.
- Symulator zgodny z OpenAPI, gotowy do rozszerzenia o oczekiwanie na potwierdzenie (W4-10).
- Włączony indexer (`CHAIN_INDEXER_ENABLED=true`); brakuje włączenia workera i projekcji
  potwierdzonego etapu — zadania Adama i Mikołaja (W4-01..W4-06).
- Amoy nie jest warunkiem żadnego przekazania (DEC-17).
