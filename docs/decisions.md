# Założenia, decyzje i granice MVP

**Status:** propozycje planistyczne, nie zatwierdzona konfiguracja produkcji.
Data bazowa: 2026-10-26. Osoby 1/2/3/4 odpowiadają modułom z głównego README.
Wymagania PDF mają pierwszeństwo: dwa klucze przy zamknięciu kontroli,
certyfikat każdej stacji, minimalny zapis on-chain i 12 tygodni nie są
opcjonalnymi propozycjami. Otwarte są ich szczegóły wykonawcze.

## Jak używać

Przed rozpoczęciem zadania zamknij wskazane decyzje: dopisz datę, osoby
zatwierdzające, wybrany wariant i uzasadnienie. Zmień status na `ZATWIERDZONA`
dopiero po rzeczywistym uzgodnieniu. Jeśli wariant się zmienia, popraw równocześnie
diagramy, protokół, schematy, fixture'y i plan. Nie odgaduj decyzji w kodzie.

## Rejestr

Aktualizacja po odpowiedzi użytkownika 2026-10-27:
ustalono **ogólną** linię przygotowanie -> montaż -> kontrola -> pakowanie,
bez wskazania konkretnego produktu. Polecono pozostawić DEC-09 otwartą,
opisując alternatywy. Pozostałe poniższe szczegóły są projektami do
zatwierdzenia, nie decyzjami automatycznie podjętymi przez autora dokumentacji.

| ID / status | Propozycja robocza i uzasadnienie | Właściciel | Co blokuje |
|---|---|---|---|
| DEC-01 / ZAKRES USTALONY Z UŻYTKOWNIKIEM | Ogólne przygotowanie -> montaż -> kontrola -> pakowanie. Dokument produkcji przypisuje stageId 0..3 i bramkę dwóch kluczy etapowi 2; szczegóły storage i DTO są propozycją wykonawczą. Bez konkretnego produktu ani fabrycznych norm. | cały zespół | [reguły produkcji](production-rules.md); spójne enumy przed kodem |
| DEC-02 / DO ZATWIERDZENIA | Całkowite jednostki i jeden zamrożony zestaw profili demonstracyjnych na wdrożenie v1. Bez produktu nie wybieramy fizycznych progów. Trzeba uzupełnić tabelę kluczy, jednostek, kompletności, granic i testów dla każdego etapu. | 2 + 4, konsultacja 1 | [profile QA](production-rules.md), schema, walidacja i generatory |
| DEC-03 / DO ZATWIERDZENIA | JCS + Keccak + StageCompletion EIP-712 z protokołu; CID jest osobnym wskazaniem relayera. Brak podwójnego podpisu po IPFS upraszcza ścieżkę. | 1 + 2 + 4 | schemat i wektory podpisów |
| DEC-04 / WYMÓG PDF, WARIANT DO ZATWIERDZENIA | EIP-712 stacji + podpisana tx operatora approveStage; adresy różne, aktualne role. Approval dotyczy raportu, nie nonce/deadline jego obwiedni. Zaproponowano idempotencję i zakaz nadpisywania aktywnego operatora przez innego; brak trwałego revoke approval w wariancie bazowym. | 1 + 2 + 3 | [protokół dwóch kluczy](quality-approval.md), funkcje QA |
| DEC-05 / DO ZATWIERDZENIA | Propozycja: EOA + SIWE, jednorazowy challenge, serwerowa cookie session, CSRF, ten sam origin przez proxy; Python używa cookie jar. Nowy POST stages może korzystać z podpisu raportu, prywatny GET wymaga sesji owner/Admin. Wartości TTL są robocze. | 2 + 3 + 4 | [login i sesje](access-control.md), OpenAPI i bezpieczeństwo API |
| DEC-06 / DO ZATWIERDZENIA | OPERATOR_ROLE dla create w API, RELAYER_ROLE dla wykonania on-chain; QA_INSPECTOR_ROLE dla operatora kontroli. Domyślna odmowa, role nie dziedziczą wszystkich praw Admin. Własne statusy dla owner; QA czyta kandydatów, nie wszystkie commands. | 1 + 2 + 3 | [macierz uprawnień](access-control.md), role i endpointy |
| DEC-07 / DO ZATWIERDZENIA | Domyślnie jedna aktywna operacja na stację do potwierdzenia. Historia wymaga konfigurowalnych potwierdzeń; lokalnie roboczo 1, dla Amoy wartość trzeba ustalić i przetestować. | 1 + 2 + 4 | finalność, kolejność i deadline |
| DEC-08 / DO ZATWIERDZENIA | PostgreSQL jako trwała kolejka, jedna instancja relayera i scheduler indexera w backendzie. Bez Redis/RabbitMQ w MVP. | 2 | persistence i worker |
| DEC-09 / OTWARTA ZGODNIE Z WYBOREM UŻYTKOWNIKA | Nie wybrano mechanizmu. Porównano unieważnienie nonce on-chain, wersjonowane autoryzacje, ponowienie po expiry oraz ograniczenie do diagnozy. Żadna alternatywa nie pozwala usuwać idempotencji ani podmieniać podpisu w miejscu. | 1 + 2 + 4 | [alternatywy poprawek](corrections.md), T026 i pełny odbiór recovery |
| DEC-10 / DO ZATWIERDZENIA | Symulator odczytuje oczekiwany nonce bezpośrednio z kontraktu przez publiczne RPC, bez sekretów. Nie dodajemy kolejnego endpointu tylko do tego odczytu. | 1 + 4 | konfiguracja symulatora |
| DEC-11 / DO ZATWIERDZENIA | Limity API, timeouty, retry/backoff, polling, retention danych i logów ustalamy przed odbiorem. QA ma skończony czas: deadline musi obejmować pinowanie, inspekcję i inclusion. | 2 + 3 + 4 | odporność i odbiór |
| DEC-12 / DO ZATWIERDZENIA | batchId losowany jako bytes32 przez backend, zatwierdzany jako wynik polecenia; etykieta produktu jest osobnym publicznym polem lub poza MVP. Dokładne wersje zależności przypinamy podczas inicjalizacji. | 2, cały zespół | CreateBatchRequest i konfiguracja |
| DEC-13 / WYMÓG PDF, FORMAT DO ZATWIERDZENIA | Propozycja: payload JSON z serialem, stationId, adresem, datami, wdrożeniem i zakresem; JCS/Keccak + osobny typ EIP-712 wystawcy, allowlista i rejestr odwołań. Aktywacja dopiero z aktualną STATION_ROLE. Nie deklarujemy X.509 ani sprzętowego TPM. | 2 + 4, konsultacja 1 | [pełny projekt certyfikatu](station-identity.md), wektor i schema |

## Dlaczego QA potrzebuje osobnej kolejki

Na łańcuchu raport nie jest ukończonym etapem przed `completeStage`.
Gdyby inspektor szukał tylko potwierdzonych StageCompleted, nigdy nie zobaczy
raportu do zatwierdzenia, a relayer będzie stale czekał.
Dlatego kandydat istnieje wcześniej w bazie, po pinowaniu, i jest dostępny
wyłącznie uprawnionej stacji/obsłudze/QA. Publiczna historia nadal go nie zawiera.
Dokładny przebieg: [flows](flows.md).

## Dwa podpisy, nie dwa warianty tego samego podpisu

- Login challenge: jednorazowe uwierzytelnienie sesji; domena/origin, adres,
  chainId, nonce, expiry i ochrona przed replay. Jeśli wybierzecie SIWE,
  ma własne zasady podpisu EIP-191; **nie** weryfikuj go jak StageCompletion.
- StageCompletion: podpis EIP-712 autoryzujący konkretny raport na kontrakcie.
- Transakcja QA: niezależny podpis portfela operatora zmiany pod zatwierdzeniem
  tego samego raportu; wraz z EIP-712 stacji spełnia współautoryzację kontroli.
- Transakcja relayera: podpis serwera; nie zastępuje podpisu stacji.

Mechanizm sesji musi określić wygaśnięcie, logout/revocation, CSRF przy cookies
i weryfikację aktualnych ról przy zapisach. To osobne wymagania auth, nie
właściwości zapewniane automatycznie przez RainbowKit.

## Co można robić przed zatwierdzeniem

Inicjalizacja narzędzi, layout UI, komponenty stanów i projekt stubów.
Zakres etapów DEC-01 jest ustalony; formaty i parametry DEC-02..06 nadal
wymagają zatwierdzenia przed zamrożeniem ABI/schematów.
Nie wykonuj pełnego odbioru testnet przed rozstrzygnięciem DEC-07..13.
Otwarta zgodnie z życzeniem DEC-09 nie blokuje samego projektowania i setupu,
ale blokuje implementację wybranego recovery i jego deklarację jako gotowego.
Wdrożenie realnej fizycznej linii
nie jest zadaniem tego MVP.
Plan nie uznaje żadnej otwartej decyzji za rozstrzygniętą.
