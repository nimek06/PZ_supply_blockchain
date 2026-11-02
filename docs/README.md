# Dokumentacja projektu

**Język:** Markdown po polsku; diagramy Mermaid; przykłady kodu w języku modułu.

Tu opisujemy ustalenia wspólne, nie implementujemy aplikacji.

## Zmiany v2 i plan startu

Po analizie z 2026-10-27 wprowadzono zmiany opisane w [changes-v2.md](changes-v2.md)
(wiążące, gdy starszy tekst mówi inaczej). Plan pierwszego tygodnia:
[week1-plan.md](weekly_plans/week1/week1-plan.md); nowy podział zadań: [team-split.md](team-split.md).

## Pięć obszarów przed implementacją

Zakres wybrany przez użytkownika 2026-10-27: **pierwszych pięć obszarów
z tabeli**, nie pięć późniejszych rezultatów (schema, makiety, demo).
Architektura pozostaje ogólna (niezależna od produktu); scenariuszem referencyjnym
MVP jest montaż roweru elektrycznego: [product-scenario.md](product-scenario.md).
Recovery (DEC-09) jest zamknięte w wariancie C uproszczonym.

| Obszar | Dokument | Co jest rozpisane |
|---|---|---|
| 1. Przebieg produkcji | [production-rules.md](production-rules.md) | etapy 0..3, wejście/wyjście, pozytywne QA, zakres walidacji i profil pomiarów do uzupełnienia |
| 2. Błędy i poprawki | [corrections.md](corrections.md) | timeout vs nowy zamiar, odmowa/expiry, reguła zastępowania raportu |
| 3. Dwa klucze | [quality-approval.md](quality-approval.md) | podpisy `StageCompletion` i `StageApproval`, wiązanie hash, niezależność, role, wyścigi |
| 4. Certyfikaty | [station-identity.md](station-identity.md) | rejestr stacji on-chain, payload/podpis certyfikatu, aktywacja, odwołanie, rotacja i kompromitacja |
| 5. Uprawnienia i login | [access-control.md](access-control.md) | macierz ról, owner, SIWE -> token Bearer i logowanie stacji |

Parametry fizyczne produktu nie są wymyślone. Profile pomiarów e-bike (DEC-02,
zatwierdzone 2026-11-02, wartości demonstracyjne) i wartości czasowe (DEC-11) są
potwierdzone w [decisions](decisions.md); DEC-07 (potwierdzenia) czeka na pomiary z Amoy.
Dokumenty nie zastępują istniejącego
[Stack_Tech.md](../Stack_Tech.md): uszczegóławiają reguły wykonania w jego
technologiach. Poglądowe skróty z opisu stosu nie definiują ABI ani finalności.

## Zalecana kolejność czytania

1. [changes-v2.md](changes-v2.md): zmiany po analizie (wiążące).
2. [source-alignment.md](source-alignment.md): nadrzędny PDF, zgodność i korekty.
3. [spec.md](spec.md): zakres MVP, aktorzy i 14 wymagań.
4. [diagrams.md](diagrams.md): mapa połączeń, importów i artefaktów.
5. [flows.md](flows.md): przebieg API, relayera, dwóch kluczy QA i paszportu.
6. [data-model.md](data-model.md): relacje, źródła prawdy i statusy.
7. [station-identity.md](station-identity.md): klucze i certyfikaty stacji.
8. [decisions.md](decisions.md): rejestr decyzji.
9. [plan.md](plan.md): 12 tygodni, [team-split.md](team-split.md), [week1-plan.md](weekly_plans/week1/week1-plan.md) i [week2-plan.md](weekly_plans/week2/week2-plan.md).

## Instrukcje wykonawcze

- [architecture.md](architecture.md): granice odpowiedzialności i źródła prawdy.
- [protocol.md](protocol.md): planowane API, podpisy, raporty i idempotencja.
- [development.md](development.md): inicjalizacja, kolejność pracy, zasady zmian.
- [testing.md](testing.md): wymagane testy i odbiór całego przepływu.

Zmiana formatu danych wymaga aktualizacji protokołu, schematów i testów wszystkich
klientów. Gdy pojawi się istotna decyzja, dopisz datę, uzasadnienie i konsekwencje
do właściwego dokumentu. Nie twórz osobnych dokumentów dla każdego małego helpera.
Instrukcje lokalne są w README konkretnych katalogów.
