# Testy i kryteria odbioru

To lista testów do napisania. Obecna struktura nie zawiera jeszcze runnerów
ani testów aplikacji. Testy muszą sprawdzać wymaganie, nie samo HTTP 200.
Szczegółowe przypadki pięciu obszarów są na końcu
[reguł produkcji](production-rules.md), [dwóch kluczy](quality-approval.md),
[certyfikatów](station-identity.md) i [uprawnień](access-control.md).
Testy nowego raportu po expiry/rewercie muszą poczekać na wybór DEC-09;
[alternatywy](corrections.md) nie są implementacją recovery.

## Wspólne dane i podpisy

W [fixtures](../shared/fixtures/README.md) przygotuj poprawny raport,
jego kanoniczne bajty, hash, domenę i podpis z klucza wyłącznie testowego
generowanego lokalnie. Kod testu ma odtwarzać podpis bez prawdziwych sekretów.
Python, Java i TypeScript muszą otrzymać identyczny `reportHash` i signer;
Solidity musi zweryfikować ten sam podpis.

Przypadki negatywne: zmieniony pomiar, QA, etap, batchId, nonce, deadline,
inna domena/chainId/kontrakt, wygasły podpis, nieznana stacja, niepoprawny adres.

## Macierz testów

| Moduł | Minimalny zakres |
|---|---|
| contracts | tworzenie partii, przejścia, role, ręczne QA, replay, deadline, eventy; fuzz nonce i kolejności |
| backend | schemat i spójność raportu, EIP-712, statusy HTTP, idempotencja, mapping błędów |
| backend integracje | PostgreSQL/Flyway, restart workera, timeout Pinaty/RPC, revert, odtwarzanie checkpointu |
| simulator | ścisłe modele, deterministyczne pomiary, wektor podpisu, retry tej samej treści, CLI |
| frontend | loading/empty/error/pending/confirmed, odczyt historii, QR, zła sieć i odrzucony podpis portfela |
| całość | raport stacji -> CID -> transakcja -> event -> projekcja -> paszport i weryfikacja hash |

PostgreSQL testuj przez Testcontainers lub odizolowaną testową bazę; nie używaj
H2 jako dowodu zgodności SQL i blokad PostgreSQL. Testy nie mogą dotykać
produkcyjnej bazy ani używać prawdziwych tokenów. TestContainers wymaga Dockera.
Testy HTTP używają stubów Pinaty i RPC dla awarii; lokalna integracja blockchain
korzysta z Anvil. CI nie wymaga dostępu do publicznej sieci Amoy.

## Odbiór pełnej ścieżki

1. Wdróż kontrakt lokalnie, nadaj właściwe role i utwórz partię.
2. Wyślij raport poprawnego pierwszego etapu. `202` ma zawierać `commandId`,
   a nie status ukończonej produkcji.
3. Poczekaj na potwierdzenie; odczytaj event, status polecenia i historię API.
4. Pobierz raport z CID, porównaj kanoniczny hash z eventem i podpisem.
5. Wyślij identyczne żądanie ponownie: ten sam commandId, jeden event.
6. Wyślij inną treść z tym samym nonce: `409`, bez drugiego pinowania/transakcji.
7. Wyślij kolejny etap bez poprzedniego lub z negatywnym QA: brak przejścia.
8. Sprawdź zamknięcie kontroli z podpisem stacji i niezależnym podpisem operatora
   zmiany dla tego samego hash. Brak jednego dowodu, inny hash lub ten sam adres
   dla obu stron nie może przejść. Cofnięcie roli operatora blokuje approval.
9. Zatrzymaj worker między pinowaniem a wysłaniem; restart nie gubi polecenia.
10. Zasymuluj timeout po wysłaniu: brak nowej operacji z nowym nonce; uzgodnij hash.
11. Wyślij dwie stacje równolegle: różne nonce transakcyjne relayera, brak kolizji.
12. Sprawdź reorg: projekcja nie pozostawia usuniętego zdarzenia jako potwierdzonego.
13. Podstaw niewłaściwy JSON pod odczyt raportu: UI pokazuje błąd integralności.
14. Sprawdź certyfikat: nieznany wystawca, wygaśnięcie, odwołanie i adres inny
    niż signer muszą blokować nowe zgłoszenie; sam certyfikat bez STATION_ROLE
    też nie daje uprawnienia.
15. Sprawdź panel stacji na telefonie/tablecie: ręczne ID, QR i rzeczywista
    zmiana statusu przez autoryzowaną operację, nigdy edycję bazy.

Osoba 4 koordynuje testy integracyjne i raport demonstracji; każdy autor
odpowiada za testy własnego modułu. Kryteria pochodzą z
[wymagań MVP](spec.md), a wymagania źródłowe z [mapy PDF](source-alignment.md).

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
