# Założenia, decyzje i granice MVP

**Status:** po analizie 2026-10-27 wprowadzono zmiany [v2](changes-v2.md).
Pozycje oznaczone `USTALONA` zatwierdził użytkownik 2026-10-27; zespół
potwierdza je na spotkaniu startowym ([plan tygodnia 1](weekly_plans/week1/week1-plan.md)),
dopisując datę i inicjały. Osoby 1/2/3/4 odpowiadają modułom z głównego README.
Wymagania PDF mają pierwszeństwo: dwa klucze przy zamknięciu kontroli,
certyfikat każdej stacji, minimalny zapis on-chain i 12 tygodni nie są
opcjonalne. Szczegóły wykonawcze są poniżej.

## Jak używać

Przed rozpoczęciem zadania sprawdź wskazane decyzje. Zmiana wariantu wymaga
poprawy diagramów, protokołu, schematów, fixture'ów i planu w jednym PR.
Nie odgaduj decyzji w kodzie. Status `DO POTWIERDZENIA` oznacza wartość roboczą
do zatwierdzenia przez zespół w wyznaczonym terminie, nie zgodę na zmianę w kodzie.

## Rejestr

| ID / status | Decyzja | Właściciel | Co blokuje |
|---|---|---|---|
| DEC-01 / USTALONA | Ogólna marszruta przygotowanie -> montaż -> kontrola -> pakowanie, stageId 0..3, bramka dwóch kluczy na etapie 2. Bez konkretnego produktu i norm fabrycznych | cały zespół | [reguły produkcji](production-rules.md) |
| DEC-02 / DO POTWIERDZENIA (piątek tyg. 1) | Całkowite jednostki; jeden zamrożony zestaw profili demonstracyjnych v1 dla etapów 0..3. Szkic przygotowują osoby 2 + 4 do środy tyg. 1 | 2 + 4, konsultacja 1 | schema, walidacja, generatory |
| DEC-03 / USTALONA | JCS + Keccak + EIP-712 `StageCompletion` **bez nonce**; CID jest osobnym wskazaniem relayera. Ręczna implementacja EIP-712 w Javie, wspólny wektor w 4 językach (V2-01, V2-10) | 1 + 2 + 4 | wektory podpisów |
| DEC-04 / USTALONA | Dwa klucze: EIP-712 stacji + **podpis EIP-712 `StageApproval` operatora poza łańcuchem**, weryfikowane razem w `completeStage`. Adresy różne, aktualna rola QA. Brak `approveStage` on-chain (V2-03) | 1 + 2 + 3 | [dwa klucze](quality-approval.md) |
| DEC-05 / USTALONA | SIWE (EIP-4361) tylko do wydania opaque tokenu Bearer (w bazie skrót SHA-256). Brak cookie i CSRF. Wartości TTL robocze (DEC-11) (V2-06) | 2 + 3 + 4 | [logowanie](access-control.md), OpenAPI |
| DEC-06 / USTALONA | OPERATOR_ROLE dla create w API, RELAYER_ROLE dla wykonania on-chain, QA_INSPECTOR_ROLE dla operatora kontroli. **Brak STATION_ROLE** - zastępuje ją rejestr stacji (DEC-13). Domyślna odmowa | 1 + 2 + 3 | [macierz uprawnień](access-control.md) |
| DEC-07 / DO POTWIERDZENIA (po smoke Amoy) | Jedna aktywna operacja na `(batchId, stageId)`. Liczba potwierdzeń: lokalnie 1, na Amoy wartość po pomiarach w tyg. 1 i 5 | 1 + 2 + 4 | finalność, deadline |
| DEC-08 / USTALONA | PostgreSQL jako trwała kolejka (`FOR UPDATE SKIP LOCKED`), jedna instancja relayera i scheduler indexera. Bez Redis/RabbitMQ. **Bez fee-bump transakcji** (V2-07) | 2 | persistence, worker |
| DEC-09 / USTALONA (2026-10-27) | Wariant C uproszczony: nowy raport etapu dopiero, gdy stary nie może się już wykonać (deadline minął w potwierdzonym bloku). Bez nonce i bez dodatkowych funkcji kontraktu. `maxDeadlineWindow` egzekwuje kontrakt. Opis: [corrections](corrections.md) (V2-02) | 1 + 2 + 4 | T026 |
| DEC-10 / USTALONA | Symulator odczytuje z kontraktu przez publiczne RPC wyłącznie stan partii (`getBatch`, `nextStage`); nonce nie istnieje | 1 + 4 | konfiguracja symulatora |
| DEC-11 / DO POTWIERDZENIA (pon. tyg. 1) | Robocze: `maxDeadlineWindow` 3600 s, `approvalDeadline` domyślnie jak deadline stacji, token 8 h / 30 min bezczynności, challenge 5 min, limity API i retry/backoff ustalane przed odbiorem. Minimalny priority fee relayera konfigurowalny (Polygon wymaga wysokiego progu - zweryfikuj wartość) | 2 + 3 + 4 | odporność i odbiór |
| DEC-12 / USTALONA | Numer partii `B-RRRR-NNNN` nadawany przez backend; `batchId = keccak256(utf8(batchNumber))`, liczony też on-chain w `createBatch(string)`. QR i ręczne wpisanie używają numeru (V2-11). Wersje zależności przypinane przy inicjalizacji | 2, cały zespół | CreateBatchRequest |
| DEC-13 / USTALONA | Rejestr stacji on-chain (`registerStation`/`revokeStation`, `expiresAt`, `stageMask`) jest źródłem egzekucji. JSON certyfikatu: payload + EIP-712 wystawcy jako poświadczenie audytowe zgodne z rejestrem. Aktywacja po potwierdzonej rejestracji. Nie deklarujemy X.509 ani TPM (V2-04) | 2 + 4, 1 (kontrakt i Java) | [certyfikat](station-identity.md) |
| DEC-14 / USTALONA | Magazyn raportów: port `ReportStorage`, Kubo lokalnie i w CI, Pinata w demo; upload bajtów JCS jako plik; walidator CID przyjmuje CIDv0 i CIDv1 (V2-12) | 2 | IPFS, CI |
| DEC-15 / USTALONA | Odrzucenie kontroli zapisywane on-chain (`StageRejected`), bez przesunięcia partii; HTTP `202`, `outcome=REJECTED` (V2-05) | 1 + 2 | kontrakt, historia |
| DEC-16 / USTALONA | Środowiska: smoke deploy na Amoy w tyg. 1, integracja pionowa także na Amoy w tyg. 5, próba demo w tyg. 10; lokalny HTTPS dla frontendu; Spring Boot = aktualna wspierana wersja (V2-13..15) | cały zespół | plan |

## Dlaczego QA potrzebuje osobnej kolejki

Na łańcuchu raport nie jest ukończonym etapem przed `completeStage`.
Inspektor szukający tylko potwierdzonych `StageCompleted` nigdy nie zobaczyłby
raportu do zatwierdzenia. Kandydat istnieje wcześniej w bazie, po pinowaniu,
i jest dostępny wyłącznie uprawnionej obsłudze/QA. Publiczna historia go nie zawiera.
Operator zatwierdza go podpisem `StageApproval`, który backend zapisuje i
przekazuje kontraktowi razem z podpisem stacji. Przebieg: [flows](flows.md).

## Cztery rodzaje podpisów

- **Login (SIWE, EIP-191):** wydanie tokenu Bearer; nie autoryzuje zdarzeń.
- **StageCompletion (EIP-712):** podpis stacji pod konkretnym raportem.
- **StageApproval (EIP-712):** podpis operatora zmiany pod tym samym `reportHash`
  (bez gazu operatora).
- **Transakcja relayera:** podpis serwera; nie zastępuje podpisu stacji ani operatora.

Każdy ma osobny typ lub schemat podpisu, więc jednego nie da się użyć jako drugiego.

## Co można robić przed potwierdzeniem

Inicjalizacja narzędzi, layout UI, wektory, szkielet kontraktu i API.
Profile DEC-02 i wartości DEC-07/11 muszą być potwierdzone przed zamrożeniem
schematów i ABI (piątek tyg. 1). Nie wykonuj pełnego odbioru na Amoy przed
zamknięciem DEC-07 i DEC-11. Wdrożenie fizycznej linii nie jest zadaniem MVP.
