# Plan pierwszego tygodnia implementacji (bez frontendu)

**Status:** plan roboczy dla 4 osób, tydzień 1 z 12. Zakłada zmiany z
[changes-v2.md](changes-v2.md) i podział z [team-split.md](team-split.md).
**Frontend nie jest realizowany w tygodniu 1** (decyzja 2026-10-27): T005 i T032
(inicjalizacja, HTTPS, skaner QR) startują w poniedziałek tygodnia 2. Osoba 3
w tym tygodniu wspiera backend, schematy i infrastrukturę, dzięki czemu frontend
ruszy z gotowym OpenAPI, profilami i środowiskiem lokalnym.
Pozycje to zadania do wykonania, nie raport ukończenia.

## Cel tygodnia

1. Decyzje zamrożone, a `docs/` nie zmienia się już bez PR.
2. Trzy moduły (kontrakt, backend, symulator) budują się i uruchamiają „na pusto”;
   `docker compose up` podnosi PostgreSQL, Anvil i Kubo z health checkami.
3. **Wspólny wektor podpisu** przechodzi w Pythonie, Javie i Solidity.
4. Kontrakt przechodzi etapy 0 -> 3 lokalnie, w tym `StageRejected`.
5. Jeden **smoke deploy na Amoy** z potwierdzonym tipem gazu i `evm_version`.
6. `POST /api/v1/batches/{id}/stages` waliduje i zapisuje polecenie (`202`), bez relayera.
7. `shared/schemas/openapi.yaml` i profile pomiarów gotowe do użycia przez frontend w tygodniu 2.

## Przygotowanie przed poniedziałkiem (każdy, ok. 1 h)

| Osoba | Instalacja i konta |
|---|---|
| 1 | Foundry (`foundryup`; na tej maszynie brak `forge`/`anvil`), portfel testowy, środki testowe POL z faucetu Amoy, publiczny RPC Amoy |
| 2 | JDK 21 i Maven są; Docker. Konto Pinata (JWT poza repozytorium) |
| 3 | JDK 21, Maven, Docker (Node, MetaMask i konto WalletConnect dopiero w tygodniu 2); edytor z podglądem OpenAPI |
| 4 | Python 3.12 w venv (na maszynie jest 3.14, a projekt zakłada 3.11+; zweryfikuj zgodność `eth-account`), Docker Compose, repozytorium GitHub z Actions |

Wszyscy: sekrety tylko w `.env` poza Git, `.gitignore` sprawdzony przed pierwszym commitem.

## Poniedziałek - spotkanie startowe (wszyscy, 2 h)

Wejście: [changes-v2](changes-v2.md), [team-split](team-split.md), [decisions](decisions.md).
Wyjście (T001), zapisane w `docs/decisions.md` z datą i inicjałami:

1. Potwierdzenie zmian V2-01..V2-17 lub jawny sprzeciw z alternatywą.
2. **DEC-02:** wstępne profile pomiarów etapów 0..3 (klucze, jednostki całkowite,
   granice) - szkic przygotowują osoby 3 i 4 do środy, zespół akceptuje w piątek.
3. **DEC-11 (wartości robocze):** `maxDeadlineWindow` 3600 s, TTL tokenu 8 h /
   30 min bezczynności, liczba potwierdzeń lokalnie 1, na Amoy do ustalenia po smoke.
4. **DEC-12:** format numeru partii `B-RRRR-NNNN`.
5. Reguły pracy: PR z jednym recenzentem, `main` chroniony, wspólny kanał,
   15-minutowy stand-up codziennie, ogłoszenie każdej zmiany `shared/`.
6. Przesunięcie frontendu na tydzień 2 i podział zadań tygodnia 1 (poniżej).

## Osoba 1 - kontrakty i łańcuch

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | T003: `foundry.toml` (solc 0.8.24, **jawny `evm_version`**), przypięte OpenZeppelin i forge-std, `forge build`/`forge test` działa | zielony pusty test, wersje w repo |
| Wt | T012: szkielet `SupplyChain.sol` (OZ `AccessControl`, `EIP712`): role, rejestr stacji, `createBatch(string)`, typy `StageCompletion` i `StageApproval` | kompiluje się; typy zgodne z [protokołem](protocol.md) |
| Śr | Test wektora z `shared/fixtures/` (od osoby 4, wtorek wieczór): odzysk adresu stacji i operatora w Solidity | test wektora zielony |
| Czw | `completeStage`: maszyna stanów 0..3, deadline i `maxDeadlineWindow`, `StageRejected`, approval dla etapu 2 (różne adresy, rola QA), zdarzenia z indeksowanym `batchId` | testy: kolejność, deadline, replay, brak/zły approval, ten sam adres, wygasła stacja, `stageMask` |
| Pt | T013/T031: skrypt deploy na Anvil; **smoke deploy na Amoy** (rejestracja stacji, `createBatch`, jeden `completeStage`); eksport ABI przez `forge inspect` do `shared/abi/`; dane lokalnego wdrożenia; przegląd modułu krypto Javy (osoba 3) | `forge test` zielone, adres lokalny, notatka: tip gazu, limity RPC i `getLogs`, faucet |

Kryterium: kontrakt pozwala wykonać 0 -> 3 lokalnie; na Amoy wykonano co najmniej jedno wywołanie.
Ryzyko: `evm_version` nieobsługiwany w sieci -> wynik smoke w piątek, poprawka w tyg. 2.

## Osoba 2 - backend, pion zgłoszenia

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | T004: Spring Initializr (aktualna wspierana wersja, Java 21, Maven), Web MVC, Validation, JPA, PostgreSQL, Flyway, Actuator; Web3j osobno; sprawdzić zgodność Web3j z Jackson | aplikacja startuje, `/actuator/health` |
| Wt | Przegląd OpenAPI osoby 3; szkic migracji `commands`, `qa_approvals`, `auth_*` według [data-model](data-model.md) | uwagi do OpenAPI, szkic SQL |
| Śr | T033 (część Web3j): kodowanie wywołania `completeStage`, podpis transakcji i opłaty EIP-1559 na Anvil, **bez `.send()`** i bez `DefaultGasProvider` | działający podpis tx na Anvil, notatka o bibliotece i opłatach |
| Czw | Pierwsza migracja `commands` i `POST .../stages`: walidacja schematu, hash i podpis (moduł krypto osoby 3), idempotencja po digest, reguła zastępowania, `202` dopiero po commit | test integracyjny z PostgreSQL (Testcontainers lub Compose) |
| Pt | T011 (start): `POST /auth/challenges` i `POST /auth/sessions` (SIWE, token Bearer ze skrótem SHA-256 w bazie), bez ról | testy: challenge jednorazowy, wygasły, zły podpis |

Kryterium: `POST /stages` poprawnie podpisanego raportu zwraca `202` z `commandId`,
zły podpis `403`, zmieniony pomiar `400`/`403` zgodnie z OpenAPI.
Ryzyko: niezgodność hashy Java/Python -> zatrzymać integrację do poprawy wektora.

## Osoba 3 - schematy, krypto w Javie, infrastruktura (zamiast frontendu)

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | T002: szkic `shared/schemas/openapi.yaml` (batches, batch-numbers, stages, commands, history, qa, auth, Problem Details, statusy z [data-model](data-model.md)); w tygodniu 2 z niego powstaną mocki i klient frontendu | OpenAPI do przeglądu osoby 2 |
| Wt | `telemetry.schema.json` i `eip712.json` (typy `StageCompletion`, `StageApproval`); szkic **profili pomiarów** etapów 0..3 (DEC-02, z osobą 4) | schematy zgodne z [protokołem](protocol.md), szkic profili |
| Śr | T033 (część krypto): moduł w `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/` - JCS (RFC 8785), Keccak-256 i **ręczny** odzysk adresu z EIP-712 (`StageCompletion`, `StageApproval`) na wektorze osoby 4 | test wektora w Javie zielony (para z osobą 2) |
| Czw | rano: testy mutacji każdego pola (hash i podpis muszą się zmienić), ten sam wynik dla adresu małymi/dużymi literami; po południu T007: `infrastructure/local/compose.yaml` (PostgreSQL, Anvil, Kubo) i `.env.example` | moduł krypto gotowy dla osoby 2; compose startuje |
| Pt | Health checki i opis świadomego resetu w README; spike magazynu raportów: upload **bajtów JCS jako plik** do Kubo i Pinaty, wersja CID, pobranie przez bramkę, limity planu; szkic portu `ReportStorage` | notatka o Pinacie i Kubo, port bez implementacji |

Kryterium: wektor przechodzi w Javie, `docker compose up` jest zdrowe, OpenAPI i schematy
zatwierdzone w piątek.
Ryzyko: osoba 3 pisze tydzień w Javie - w razie blokady para z osobą 2 (środa) i przegląd
osoby 1 (piątek); zadanie T033 jest podzielone właśnie po to, by błąd nie zatrzymał relayera.

## Osoba 4 - symulator, integracja, dokumentacja

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | T006: `pyproject.toml` (`>=3.11`), układ `src`, venv, eth-account, Pydantic v2 (`extra=forbid`), httpx, pytest, szkielet CLI | `python -m supply_chain_simulator --help` |
| Wt (**ścieżka krytyczna**) | T008: modele raportu v1 (jednostki całkowite), JCS, Keccak, podpis `StageCompletion` i `StageApproval`; **do końca dnia** `shared/fixtures/signature-vector.json` (klucz testowy, chainId 31337, stały adres kontraktu) z raportem, bajtami JCS, hashem, digestami i podpisami; ogłoszenie na kanale | wektor w repo, testy Pythona |
| Śr | Profile pomiarów etapów 0..3 (z osobą 3); szkic `station-certificate.schema.json` i przykładu certyfikatu | propozycja profili i certyfikatu |
| Czw | `identity.py` - generowanie klucza stacji bez wypisywania sekretu; CLI wystawiania certyfikatu (szkic); klient `POST stages` wobec OpenAPI | stacja ma klucz i certyfikat |
| Pt | Szkielet CI (jobs: contracts, backend, simulator) do przekazania osobie 1; pierwszy scenariusz YAML; **demo tygodnia** | CI uruchamia testy 3 modułów, scenariusz opisany |

Kryterium: wektor opublikowany we wtorek; CI uruchamia testy; symulator podpisuje poprawny raport.
Ryzyko: wektor spóźniony -> blokuje osoby 1, 2, 3; ma pierwszeństwo przed profilami i CI.

## Piątek - demo i odbiór tygodnia (wszyscy, 60 min)

Kolejność: wektor w Pythonie, Javie i Solidity -> `forge test` i smoke Amoy ->
`POST /stages` i login SIWE -> `docker compose up` -> CI -> OpenAPI i profile
(zatwierdzenie DEC-02). Następnie retrospektywa i plan tygodnia 2, w którym
startuje frontend (T005, T032) na podstawie OpenAPI.

## Definicja ukończenia tygodnia

- [ ] Decyzje v2 potwierdzone i wpisane z datą w `docs/decisions.md`.
- [ ] Wektor `signature-vector.json` przechodzi w Pythonie, Javie i Solidity.
- [ ] `forge test`, `./mvnw verify` i `python -m pytest` działają lokalnie.
- [ ] `docker compose up` podnosi PostgreSQL, Anvil i Kubo; health checki zielone.
- [ ] Kontrakt: 0 -> 3 oraz `StageRejected`; zdeployowany na Anvil i (smoke) na Amoy.
- [ ] `POST /stages` zapisuje polecenie i zwraca `202`; challenge SIWE wydaje token.
- [ ] `openapi.yaml`, `telemetry.schema.json`, `eip712.json` i profile pomiarów zatwierdzone.
- [ ] Notatki: limity Pinaty, tip gazu Amoy, limity RPC, wersje zależności.
- [ ] Żadnych sekretów w repozytorium.

## Zależności między osobami w tygodniu 1

```text
Pon: osoba 3 publikuje szkic OpenAPI -> Wt: osoba 2 recenzuje, osoba 4 czyta modele
Wt: osoba 4 publikuje wektor -> Śr: osoby 1, 2, 3 weryfikują w Solidity i Javie
Śr: osoba 3 oddaje moduł krypto Javy -> Czw: osoba 2 używa go w POST /stages
Czw: osoba 3 oddaje compose -> Pt: osoba 2 i 4 testują na PostgreSQL/Anvil/Kubo
Pt: osoba 1 publikuje ABI -> osoba 2 zaczyna wrapper/enkoder w tyg. 2-4
```

Jeśli którykolwiek punkt się opóźnia, zespół w stand-upie następnego dnia
wybiera: przesunięcie zadania lub pomoc zamiast równoległego przepisywania.

## Skutki dla tygodni 2 i dalej

- T005 (inicjalizacja frontendu) i T032 (HTTPS + QR) startują w poniedziałek tygodnia 2.
  Osoba 3 dopisuje wtedy TS: JCS + `keccak256` + `hashTypedData` na wektorze (część T008).
- Osoba 3 zachowuje zadania frontendowe T022, T023, T025 w dotychczasowych terminach
  (od tygodnia 5); opóźnienie startu o tydzień jest do nadrobienia mockami z OpenAPI.
- Moduł krypto Javy należy do osoby 3 do końca tygodnia 2, potem utrzymuje go osoba 2.
