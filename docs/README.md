# Dokumentacja projektu

**Język:** Markdown po polsku; diagramy Mermaid; przykłady kodu w języku modułu.

Tu opisujemy ustalenia wspólne, nie implementujemy aplikacji.

## Struktura folderów

| Folder | Zawartość |
|---|---|
| [project/](project/) | zakres MVP, scenariusz produktu, zgodność z PDF i zmiany v2 |
| [architecture/](architecture/) | architektura, diagramy, model danych, przepływy i protokół |
| [rules/](rules/) | pięć obszarów reguł: produkcja, poprawki, dwa klucze, certyfikaty, uprawnienia |
| [decisions/](decisions/) | rejestr decyzji i analizy zmian zakresu |
| [guides/](guides/) | przewodnik pracy i strategia testów |
| [planning/](planning/) | plan 12 tygodni, podział zadań i [plany tygodniowe](planning/weekly/README.md) |

**Wybrany zakres (2026-11-11):** [wariant 1 — lokalny system i jednorazowe demo
kontraktu Amoy](decisions/local-blockchain-scope.md). Cała aplikacja na Anvil/Kubo,
bez obowiązku wdrożenia jej na Amoy i bez Pinaty; DEC-17 ma pierwszeństwo
nad wcześniejszymi zapisami o środowiskach.

**Teraz realizujemy:** [tydzień 3](planning/weekly/week3/week3-plan.md)
i [tydzień 4](planning/weekly/week4/week4-plan.md).

## Zmiany v2 i plan startu

Po analizie z 2026-10-27 wprowadzono zmiany opisane w [changes-v2.md](project/changes-v2.md)
(wiążące, gdy starszy tekst mówi inaczej). Plan pierwszego tygodnia:
[week1-plan.md](planning/weekly/week1/week1-plan.md); nowy podział zadań: [team-split.md](planning/team-split.md).

## Pięć obszarów przed implementacją

Zakres wybrany przez użytkownika 2026-10-27: **pierwszych pięć obszarów
z tabeli**, nie pięć późniejszych rezultatów (schema, makiety, demo).
Architektura pozostaje ogólna (niezależna od produktu); scenariuszem referencyjnym
MVP jest montaż roweru elektrycznego: [product-scenario.md](project/product-scenario.md).
Recovery (DEC-09) jest zamknięte w wariancie C uproszczonym.

| Obszar | Dokument | Co jest rozpisane |
|---|---|---|
| 1. Przebieg produkcji | [production-rules.md](rules/production-rules.md) | etapy 0..3, wejście/wyjście, pozytywne QA, zakres walidacji i profil pomiarów do uzupełnienia |
| 2. Błędy i poprawki | [corrections.md](rules/corrections.md) | timeout vs nowy zamiar, odmowa/expiry, reguła zastępowania raportu |
| 3. Dwa klucze | [quality-approval.md](rules/quality-approval.md) | podpisy `StageCompletion` i `StageApproval`, wiązanie hash, niezależność, role, wyścigi |
| 4. Certyfikaty | [station-identity.md](rules/station-identity.md) | rejestr stacji on-chain, payload/podpis certyfikatu, aktywacja, odwołanie, rotacja i kompromitacja |
| 5. Uprawnienia i login | [access-control.md](rules/access-control.md) | macierz ról, owner, SIWE -> token Bearer i logowanie stacji |

Parametry fizyczne produktu nie są wymyślone. Profile pomiarów e-bike (DEC-02,
zatwierdzone 2026-11-02, wartości demonstracyjne) i wartości czasowe (DEC-11) są
potwierdzone w [decisions](decisions/decisions.md); DEC-07 zamknięta dla Anvila:
`latest` i 1 potwierdzenie, bez zależności od pomiarów Amoy.
Dokumenty nie zastępują istniejącego
[Stack_Tech.md](../Stack_Tech.md): uszczegóławiają reguły wykonania w jego
technologiach. Poglądowe skróty z opisu stosu nie definiują ABI ani finalności.

## Zalecana kolejność czytania

1. [changes-v2.md](project/changes-v2.md): zmiany po analizie (wiążące).
2. [source-alignment.md](project/source-alignment.md): nadrzędny PDF, zgodność i korekty.
3. [spec.md](project/spec.md): zakres MVP, aktorzy i 14 wymagań.
4. [diagrams.md](architecture/diagrams.md): mapa połączeń, importów i artefaktów.
5. [flows.md](architecture/flows.md): przebieg API, relayera, dwóch kluczy QA i paszportu.
6. [data-model.md](architecture/data-model.md): relacje, źródła prawdy i statusy.
7. [station-identity.md](rules/station-identity.md): klucze i certyfikaty stacji.
8. [decisions.md](decisions/decisions.md): rejestr decyzji.
9. [plan.md](planning/plan.md): 12 tygodni, [team-split.md](planning/team-split.md)
   i [plany tygodniowe](planning/weekly/README.md), w tym bieżące tygodnie 3 i 4.

## Instrukcje wykonawcze

- [architecture.md](architecture/architecture.md): granice odpowiedzialności i źródła prawdy.
- [protocol.md](architecture/protocol.md): planowane API, podpisy, raporty i idempotencja.
- [development.md](guides/development.md): inicjalizacja, kolejność pracy, zasady zmian.
- [testing.md](guides/testing.md): wymagane testy i odbiór całego przepływu.

Zmiana formatu danych wymaga aktualizacji protokołu, schematów i testów wszystkich
klientów. Gdy pojawi się istotna decyzja, dopisz datę, uzasadnienie i konsekwencje
do właściwego dokumentu. Nie twórz osobnych dokumentów dla każdego małego helpera.
Instrukcje lokalne są w README konkretnych katalogów.
