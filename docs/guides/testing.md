# Testy i kryteria odbioru

To docelowa macierz odbioru, nie raport wykonanych testów. Istniejące zestawy
po tygodniu 2 zachowujemy i rozszerzamy. Testy muszą sprawdzać wymaganie, nie samo HTTP 200.
Szczegółowe przypadki pięciu obszarów są na końcu
[reguł produkcji](../rules/production-rules.md), [dwóch kluczy](../rules/quality-approval.md),
[certyfikatów](../rules/station-identity.md) i [uprawnień](../rules/access-control.md).
Testy wznowienia (nowy raport po wygaśnięciu starego) wynikają z zamkniętej DEC-09:
[reguła zastępowania](../rules/corrections.md). Zmiany v2: [changes-v2](../project/changes-v2.md).

## Wspólne dane i podpisy

W [fixtures](../../shared/fixtures/README.md) przygotuj poprawny raport,
jego kanoniczne bajty, hash, domenę i podpis z klucza wyłącznie testowego
generowanego lokalnie. Kod testu ma odtwarzać podpis bez prawdziwych sekretów.
Python, Java i TypeScript muszą otrzymać identyczny `reportHash` i signer;
Solidity musi zweryfikować ten sam podpis.

Przypadki negatywne: zmieniony pomiar, QA, etap, batchId, deadline, podpis
`StageApproval` użyty jako `StageCompletion` (i odwrotnie),
inna domena/chainId/kontrakt, wygasły podpis, nieznana stacja, niepoprawny adres.

## Macierz testów

| Moduł | Minimalny zakres |
|---|---|
| contracts | tworzenie partii, przejścia, role, rejestr stacji (`stageMask`, `expiresAt`, odwołanie), approval operatora, `StageRejected`, deadline i `maxDeadlineWindow`, eventy; fuzz kolejności etapów i podpisów |
| backend | schemat i spójność raportu, EIP-712, statusy HTTP, idempotencja, reguła zastępowania, mapping błędów, token Bearer i role |
| backend integracje | PostgreSQL/Flyway, restart workera, timeout magazynu/RPC, revert, okno ponownego skanu indexera (reorg przez Anvil `evm_snapshot`/`evm_revert`) |
| simulator | ścisłe modele, deterministyczne pomiary, wektor podpisu, retry tej samej treści, CLI |
| frontend | loading/empty/error/pending/confirmed, odczyt historii, QR, zła sieć i odrzucony podpis `StageApproval` w portfelu |
| całość | raport stacji -> CID -> transakcja -> event -> projekcja -> paszport i weryfikacja hash |

PostgreSQL testuj przez Testcontainers lub odizolowaną testową bazę; nie używaj
H2 jako dowodu zgodności SQL i blokad PostgreSQL. Testy nie mogą dotykać
produkcyjnej bazy ani używać prawdziwych tokenów. TestContainers wymaga Dockera.
Testy HTTP używają stubów magazynu raportów i RPC dla awarii; lokalna integracja
blockchain korzysta z Anvil, a IPFS z Kubo. CI nie wymaga dostępu do publicznej
sieci Amoy ani Pinaty. Cały odbiór aplikacji i próba prezentacji odbywają się
na Anvil/Kubo (DEC-17). Nie wymagamy testów backendu/frontendu/indexera na Amoy.

## Odbiór pełnej ścieżki

1. Wdróż kontrakt lokalnie, zarejestruj stacje, nadaj role i utwórz partię (numer `B-RRRR-NNNN`).
2. Wyślij raport poprawnego pierwszego etapu. `202` ma zawierać `commandId`,
   a nie status ukończonej produkcji.
3. Poczekaj na potwierdzenie; odczytaj event, status polecenia i historię API.
4. Pobierz raport z CID, porównaj kanoniczny hash z eventem i podpisem.
5. Wyślij identyczne żądanie ponownie: ten sam commandId, jeden event.
6. Wyślij inną treść dla etapu z aktywnym poleceniem: `409 STAGE_ALREADY_PENDING`,
   bez drugiego pinowania/transakcji.
7. Wyślij kolejny etap bez poprzedniego: brak przejścia. Wyślij raport z `qaPassed=false`:
   `StageRejected`, partia bez zmiany, polecenie `outcome=REJECTED`.
8. Sprawdź zamknięcie kontroli z podpisem stacji i niezależnym podpisem `StageApproval`
   operatora zmiany dla tego samego hash. Brak jednego dowodu, inny hash, wygasły
   `approvalDeadline` lub ten sam adres dla obu stron nie może przejść.
   Odebranie roli QA operatorowi przed wykonaniem blokuje przejście.
9. Zatrzymaj worker między pinowaniem a wysłaniem; restart nie gubi polecenia.
10. Zasymuluj timeout po wysłaniu: brak nowej operacji; uzgodnij hash i wyślij te same bajty.
11. Wyślij dwie stacje równolegle: jednoznaczne wyniki, brak kolizji nonce relayera.
12. Sprawdź reorg: projekcja nie pozostawia usuniętego zdarzenia jako potwierdzonego.
13. Podstaw niewłaściwy JSON pod odczyt raportu: UI pokazuje błąd integralności.
14. Sprawdź certyfikat i rejestr: nieznany wystawca, wygaśnięcie, `revokeStation`, adres
    inny niż signer, `stageId` poza `stageMask` oraz certyfikat bez rejestracji
    on-chain muszą blokować nowe zgłoszenie.
15. Sprawdź wznowienie: raport po wygaśnięciu deadline starego polecenia (FAILED)
    zostaje przyjęty dopiero po warunku reguły zastępowania; stary podpis po
    deadline jest odrzucany przez kontrakt.
16. Sprawdź panel stacji na telefonie/tablecie przez HTTPS: ręczne ID, QR i rzeczywista
    zmiana statusu przez autoryzowaną operację, nigdy edycję bazy.

Osoba 4 koordynuje testy integracyjne i raport demonstracji; każdy autor
odpowiada za testy własnego modułu. Kryteria pochodzą z
[wymagań MVP](../project/spec.md), a wymagania źródłowe z [mapy PDF](../project/source-alignment.md).

## Dodatkowe jednorazowe demo kontraktu Amoy (T029, tydzień 10)

To osobny pokaz, nie warunek lokalnego odbioru powyżej ani test w CI.

1. Użyj osobnych kont testowych (nigdy publicznych kluczy Anvila), RPC Amoy
   i testowego POL z faucetu. Nie kupuj prawdziwych tokenów.
2. Skryptem wdróż kontrakt, nadaj role, zarejestruj stacje i utwórz osobną partię.
3. Wykonaj etapy 0..3, z niezależnym podpisem operatora QA na etapie 2.
   Podpisy muszą dotyczyć domeny Amoy i adresu tego wdrożenia.
4. Sprawdź receipts, eventy i końcowy `getBatch`. Zapisz datę, adres kontraktu,
   numery bloków, hashe transakcji i linki do eksploratora oraz jawne ograniczenia.
5. Jeśli używasz CID raportu z Kubo, nie deklaruj jego dostępności w publicznym
   IPFS bez sprawdzenia. Demo kontraktu nie wymaga Pinaty.

Istniejący smoke jednego etapu wymaga rozszerzenia do pełnej partii przed
oznaczeniem T029 jako wykonanego. Brak RPC/faucetu lub niewykonane etapy należy
odnotować; screenshot nie zastępuje udokumentowanego przebiegu.

## Gdzie umieszczać testy

- Solidity: `contracts/test/`.
- Java: `backend/src/test/java/pl/pz/supplyblockchain/`.
- Python: `simulator/tests/`.
- React: testy blisko komponentów; wspólny setup i testy E2E w `frontend/tests/`.
- Wspólne fixture'y: `shared/fixtures/`, nie cztery niezależne kopie.

## Gotowe do przeglądu

Budowanie/type-check, odpowiednie testy i formatowanie przechodzą dla zmienionych
modułów. Dokumentacja i schematy odpowiadają implementacji. Sekrety nie są
wersjonowane. Statusy oczekiwania i błędu są widoczne. Nie ma ręcznie zmienionych
plików wygenerowanych ani ukrytych fallbacków udających poprawne raporty.
