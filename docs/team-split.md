# Nowy podział zadań (v2, 2026-10-27)

**Status:** zastępuje kolumny „Osoba” z dawnych tabel w [plan.md](plan.md).
Powód: osoba 2 miała 12 z 30 zadań (auth, certyfikaty, trwała kolejka, relayer,
indexer, reorg), a osoba 1 tylko 4. Zmiany techniczne:
[changes-v2.md](changes-v2.md). Plan pierwszego tygodnia: [week1-plan.md](week1-plan.md).

## Zasady podziału

1. Jedna osoba **odpowiada** za zadanie; druga je **przegląda** (kolumna „Przegląd”).
   Kod Javy pisany przez osobę 1 (indexer, weryfikator certyfikatów, historia)
   przegląda osoba 2 - zna konwencje backendu. Kod kontraktu przegląda osoba 2
   lub 4, kod UI - osoba 4.
2. Osoba 1 jest ekspertem od łańcucha, więc przejmuje wszystko, co czyta
   zdarzenia i rejestr stacji, także po stronie Javy.
3. Osoba 2 skupia się na jednym pionie: przyjęcie -> IPFS -> relayer -> QA.
   Osoba 4 nie pisze tych adapterów równolegle (zob. [plan](plan.md) sekcja 6),
   pomaga stubami i testami.
4. Każdy autor pisze testy swojego kodu; osoba 4 koordynuje testy systemowe.
5. Zadania nowe w v2: T031, T032, T033. **Tydzień 1 bez frontendu** (2026-10-27):
   T005 i T032 przeszły na tydzień 2, a osoba 3 w tygodniu 1 prowadzi OpenAPI, schematy,
   krypto w Javie i Compose ([week1-plan](week1-plan.md)).

## Role

| Osoba | Rola | Zakres główny |
|---|---|---|
| 1 | Kontrakty i łańcuch | Solidity, rejestr stacji, deploy (Anvil/Amoy), indexer, weryfikacja certyfikatów w Javie, historia API, CI |
| 2 | Backend: pion zgłoszenia | REST, auth Bearer, PostgreSQL, ReportStorage (Kubo/Pinata), relayer, worker, API kolejki QA |
| 3 | Frontend | operator, panel QA z podpisem approval, paszport, QR, lokalny HTTPS, portfel |
| 4 | Symulator, integracja, dokumentacja | Python, wektory podpisów, wystawianie certyfikatów, Compose, testy systemowe, instrukcje |

## Zadania

| Zadanie | Opis skrócony | Odpowiada | Przegląd | Tydzień |
|---|---|---|---|---|
| T001 | Potwierdzenie decyzji v2 i DEC | wszyscy | - | 1 |
| T002 | Schematy: OpenAPI, telemetria, EIP-712, certyfikat; przykłady | 3 (OpenAPI, telemetria, eip712) + 4 (certyfikat) | 2, 1 | 1-2 |
| T003 | `foundry.toml`, zależności, `evm_version` | 1 | 2 | 1 |
| T004 | Inicjalizacja backendu (Maven, Spring Boot, Flyway) | 2 | 1 | 1 |
| T005 | Inicjalizacja frontendu (HTTPS lokalny w T032) - **przesunięte na tydzień 2** | 3 | 4 | 2 |
| T006 | Inicjalizacja symulatora (venv, CLI, pytest) | 4 | 3 | 1 |
| T007 | Compose: PostgreSQL, Anvil, Kubo; `.env.example` | 3 | 2, 4 | 1 |
| T008 | Wektory: `StageCompletion`, `StageApproval`, certyfikat; testy w Py/Java/Sol (tyg. 1) i TS (tyg. 2) | 4 (generuje) | 1, 2, 3 (weryfikują) | 1-2 |
| T009 | Symulator: modele, hash, podpis, tożsamość | 4 | 2 | 2 |
| T010 | Weryfikator certyfikatu (Java) i zgodność z rejestrem on-chain | 1 | 2 | 3 |
| T011 | Auth: SIWE -> token Bearer, role, owner | 2 | 3, 4 | 2-3 |
| T012 | `SupplyChain.sol`: rejestr stacji, `completeStage`, approval-podpis, `StageRejected` | 1 | 2 | 1-3 |
| T013 | Skrypty deploy, rejestracja stacji i ról, ABI | 1 | 4 | 3 |
| T014 | Model danych i repozytoria (commands, attempts, qa_approvals, sessions) | 2 | 1 | 3 |
| T015 | Przyjęcie partii/etapu, idempotencja, reguła zastępowania, Problem Details | 2 | 4 | 3 |
| T016 | `ReportStorage`: Kubo i Pinata, walidacja CID | 2 | 4 | 4 |
| T017 | Nadawca transakcji: własny podpis, EIP-1559, trwała próba | 2 | 1 | 4 |
| T018 | Worker: pinowanie, WAITING_FOR_QA, preflight, wysyłka, odzysk | 2 | 1 | 4-5 |
| T019 | Indexer: potwierdzenia, okno ponownego skanu, projekcje | 1 | 2 | 4-5 |
| T020 | Symulator: klient, runner, scenariusze | 4 | 2 | 4 |
| T021 | API kolejki QA i `POST` approval | 2 | 3 | 5-6 |
| T022 | Frontend: klient API, operator, tworzenie/status partii | 3 | 4 | 5 |
| T023 | Frontend: panel QA, podpis `StageApproval`, sieć/odmowa portfela | 3 | 1 | 6 |
| T024 | API historii i projekcji (publiczne DTO, paginacja) | 1 | 2 | 6-7 |
| T025 | Paszport: oś czasu, QR, weryfikacja integralności | 3 | 4 | 7 |
| T026 | Wznowienie: reguła zastępowania, CLI ponowienia, testy wygaśnięcia | 2 | 1, 4 | 6-7 |
| T027 | Testy restartu, konkurencji, reorg, certyfikatów, dwóch podpisów | 4 | autorzy modułów | 7-9 |
| T028 | CI w `.github/workflows/` (szkielet od osoby 4 w tyg. 1) | 1 | 4 | 2-9 |
| T029 | Amoy: wdrożenie, Pinata/RPC, frontend | 1 + 2 + 3 | 4 | 5, 10 |
| T030 | Instrukcja demonstracji, wyniki, dokumentacja końcowa | 4 | wszyscy | 11-12 |
| T031 | **Nowe:** smoke deploy kontraktu na Amoy (faucet, RPC, tip, `evm_version`) | 1 | 2 | 1 |
| T032 | **Nowe:** spike HTTPS + skaner QR na telefonie, wybór biblioteki - **przesunięte na tydzień 2** | 3 | 4 | 2 |
| T033 | **Nowe:** spike Java: (a) JCS + Keccak + odzysk EIP-712 na wektorze - osoba 3; (b) kodowanie wywołania, podpis tx i opłaty EIP-1559 na Anvil - osoba 2 | 3 + 2 | 1 | 1 |

## Obciążenie po zmianie

| Osoba | Odpowiada za | Dawniej | Uwagi |
|---|---|---|---|
| 1 | T003, T010, T012, T013, T019, T024, T028, T031, część T029 | 4 | pełne wykorzystanie, w tyg. 1 priorytet T012/T031 |
| 2 | T004, T011, T014, T015, T016, T017, T018, T021, T026, część T033, część T002/T029 | ~12 | nadal najwięcej, ale bez indexera, certyfikatów, historii, CI, Compose i krypto Javy |
| 3 | T002 (OpenAPI), T007, część T033 (w tyg. 1); T005, T032, T022, T023, T025, część T029 (od tyg. 2) | 4 | tydzień 1 bez frontendu: schematy, krypto w Javie, Compose, spike IPFS |
| 4 | T006, T008, T009, T020, T027, T030, część T002 | 6 | dodatkowo wystawianie certyfikatów (CLI Python); Compose przeszedł na osobę 3 |

Odchylenie od wcześniejszej propozycji: wystawianie certyfikatów (narzędzie
administracyjne) zostaje w Pythonie u osoby 4, bo tam jest już podpis EIP-712.
Osoba 1 zyskuje rejestrację stacji on-chain oraz weryfikację certyfikatu w Javie.

## Granice odpowiedzialności

- **Osoba 1** nie zmienia ABI bez ogłoszenia: ABI jest kontraktem z osobą 2.
  Po każdej zmianie ABI: eksport do `shared/abi/` i powiadomienie w PR.
- **Osoba 2** utrzymuje OpenAPI; zmiana DTO wymaga aktualizacji klienta TS,
  modeli Pythona i fixture'ów w tym samym PR.
- **Osoba 3** nie wymyśla formatów: korzysta z OpenAPI i `shared/fixtures/`.
- **Osoba 4** jest właścicielem `shared/fixtures/`; zmiana wektora wymaga
  potwierdzenia od osób 1, 2 i 3.

## Ścieżka krytyczna

T008 (wektor) -> T012 (kontrakt) -> T013 (ABI) -> T017 (relayer) -> T018 (worker)
-> T019 (indexer) -> T022/T023 (UI) -> T027 -> T029/T030.
Opóźnienie T008 o dzień opóźnia weryfikację w Pythonie, Javie i Solidity (TypeScript dochodzi
w tygodniu 2), dlatego jest zaplanowane na wtorek tygodnia 1.
