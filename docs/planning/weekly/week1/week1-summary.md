# Tydzień 1 - podsumowanie stanu (aktualizacja 2026-11-02, po mergach)

Zebrane z `main` po scaleniu PR #1 (Adam), #2 (Mikołaj), #3 (Kuba) i #4 (Robert),
a następnie zmian z 2026-11-02 (DEC-02/11, profil e-bike, nowy wektor). Odniesienie:
[week1-plan.md](week1-plan.md) i jego "Definicja ukończenia tygodnia".

## Stan gałęzi

Wszystkie cztery gałęzie tygodnia 1 są w `main`: `adam` (PR #1), backend Mikołaja (PR #2),
`kuba/week1-schemas-compose` (PR #3), `robert/week1-simulator-vector` (PR #4). W `main` są już
OpenAPI, schematy, Compose, symulator, wektor w `shared/fixtures/` i szkielet CI.
CI (`.github/`) zostaje bez zmian i nie jest rozwijane (decyzja 2026-11-02: niepotrzebne).

## Definicja ukończenia tygodnia - punkt po punkcie

- [x] **Decyzje v2 potwierdzone i wpisane z datą w `docs/decisions.md`.**
  Wpis zespołu z 2026-11-02 w nagłówku [decisions.md](../../decisions.md); DEC-02 (profil e-bike)
  i DEC-11 (wartości czasu) zamknięte jako `USTALONA`. Otwarta pozostaje DEC-07 (liczba
  potwierdzeń na Amoy, po pomiarach).
- [x] **Wektor `signature-vector.json` przechodzi w Pythonie, Javie i Solidity.**
  Wektor jest w `shared/fixtures/` i w `backend/src/test/resources/fixtures/` (identyczne
  pliki). 2026-11-02 przegenerowany pod profil e-bike etapu 2 (`inspectedUnits`,
  `defectsFound`, `checklistItemsTotal`, `checklistItemsPassed`), nowy `reportHash`
  `0x388322c4...7ffe`. `test_SharedVector` (Solidity), testy Javy i pytest przechodzą.
- [x] **`forge test`, `./mvnw verify` i `python -m pytest` działają lokalnie.**
  `forge test`: 55 passed. Backend: 48 testów jednostkowych zielonych; 3 testy `*IT` wymagają
  `TEST_DB_URL` (PostgreSQL z Compose) i nie były uruchamiane po zmianie wektora. `pytest`:
  206 passed, 1 skipped (Python 3.14).
- [x] **`docker compose up` podnosi PostgreSQL, Anvil i Kubo; health checki zielone.**
  Potwierdzone przez Kubę 2026-11-02 (status `healthy` po ok. 10 s); `compose.yaml` jest w `main`.
- [x] **Kontrakt: 0 -> 3 oraz `StageRejected`; zdeployowany na Anvil i (smoke) na Amoy.**
  Lokalnie w pełni zrobione i przetestowane (`test_FullRouteZeroToThree`, 52+ testów
  negatywnych). **Smoke na Amoy NIE wykonany realnie** - tylko symulacja bez portfela
  (`evm_version=cancun` działa, tip ≈30 gwei, limity `eth_getLogs` zmierzone). Brakuje
  prawdziwej transakcji z faucetowym POL; `shared/deployments/amoy.json` nie istnieje
  (przechodzi na tydzień 2, poniedziałek).
- [x] **`POST /stages` zapisuje polecenie i zwraca `202`; challenge SIWE wydaje token.**
  `StageController` zwraca `202 Accepted` + `Location`; `AuthController` ma
  `/auth/challenges` i `/auth/sessions`. Reguła zastępowania (V2-02) zaimplementowana
  zgodnie z tabelą z `corrections.md`. Od 2026-11-02 backend wymaga też, by
  `stationCertificateId` było bytes32 (jak w OpenAPI i symulatorze).
- [x] **`openapi.yaml`, `telemetry.schema.json`, `eip712.json` i profile pomiarów zatwierdzone.**
  Profile e-bike v1 zatwierdzone w DEC-02 (2026-11-02), wektor dostosowany do profilu etapu 2
  (niezgodność wykryta wcześniej usunięta; test w `simulator/tests/test_signature_vector.py`
  pilnuje zgodności z schematem). OpenAPI nadal wymaga przeglądu szczegółów w tygodniu 2.
- [x] **Notatki: limity Pinaty, tip gazu Amoy, limity RPC, wersje zależności.**
  Tip i limity RPC Amoy: [adam-amoy-notes.md](adam-amoy-notes.md). Kubo: zbadane,
  notatka Kuby ([kuba-ipfs-notes.md](kuba-ipfs-notes.md)). **Pinata: niezbadana**
  (czeka na token Mikołaja, przechodzi na tydzień 2).
- [x] **Żadnych sekretów w repozytorium.** Nie znaleziono kluczy prywatnych ani
  tokenów w sprawdzonych commitach; `.env` wszędzie w `.gitignore`.

## Co jest zrobione (skrót po osobach)

**Adam - kontrakty i łańcuch**
`SupplyChain.sol` kompletny wg `changes-v2` (role, rejestr stacji, `completeStage`
z pełną kolejnością reguł, `StageRejected`, dwa klucze na etapie 2). 54 testy
(w tym fuzz) + test wspólnego wektora. Skrypty deployu/rejestracji/smoke na Anvil,
eksport ABI i danych wdrożenia. Smoke na Amoy - tylko przygotowanie i symulacja,
bez prawdziwej transakcji (brak portfela z POL).

**Mikołaj - backend**
Inicjalizacja Spring Boot 4.1.2 + Web3j 6.1.0. Ręczny moduł krypto EIP-712 w Javie
(bez gotowego kodera, zgodnie z V2-10), encoder `completeStage` z poprawną kolejnością
9 argumentów, podpisywanie transakcji EIP-1559 bez `.send()`/`DefaultGasProvider`.
`POST /stages` z pełną walidacją i regułą zastępowania. SIWE challenge/session (start).

**Kuba - schematy, krypto Javy (recenzja), infrastruktura**
`openapi.yaml`, `telemetry.schema.json`, `eip712.json`, `compose.yaml` (potwierdzone
jako działające), port `ReportStorage`, testy mutacji podpisu, badanie Kubo (IPFS).
Znalazł i zgłosił dwa realne błędy w backendzie Mikołaja (Flyway, cudzysłów adresu
kontraktu w YAML) - do przeglądu przez Mikołaja.

**Robert - symulator, integracja, dokumentacja**
Modele raportu, JCS, podpisy, **wspólny wektor podpisu** (zweryfikowany w 3 implementacjach,
od 2026-11-02 w wersji e-bike), klient HTTP symulatora, certyfikat stacji, szkielet CI
(joby contracts/simulator/backend; nie rozwijany dalej).

## Główne braki względem planu (po aktualizacji 2026-11-02)

1. Prawdziwy smoke deploy na Amoy nie wykonany (tylko symulacja offline; wymaga POL z faucetu
   i klucza testowego poza repozytorium) - tydzień 2, poniedziałek.
2. Badanie Pinaty nie wykonane (brak tokenu) - tydzień 2.
3. Testy `*IT` backendu wymagają `TEST_DB_URL`; po zmianie wektora i walidacji
   `stationCertificateId` nie były uruchamiane (Docker niedostępny w chwili zmiany).
4. Backend jeszcze nie waliduje profili pomiarów (`422`) - tydzień 2 (Mikołaj).

Rozstrzygnięte: merge gałęzi Kuby i Roberta, wpis decyzji (DEC-02 e-bike, DEC-11),
niezgodność wektora z profilem etapu 2, zakres wersji Pythona (`>=3.11`, testy przechodzą
na 3.12-3.14; wcześniejsza uwaga "eth-account nie działa na 3.14" nie potwierdziła się).
