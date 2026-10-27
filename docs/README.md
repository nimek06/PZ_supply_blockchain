# Dokumentacja projektu

**Język:** Markdown po polsku; diagramy Mermaid; przykłady kodu w języku modułu.

Tu opisujemy ustalenia wspólne, nie implementujemy aplikacji.

## Pięć obszarów przed implementacją

Zakres wybrany przez użytkownika 2026-10-27: **pierwszych pięć obszarów
z tabeli**, nie pięć późniejszych rezultatów (schema, makiety, demo).
Zachowujemy ogólną linię bez konkretnego produktu, a recovery porównujemy
bez wyboru mechanizmu.

| Obszar | Dokument | Co jest rozpisane |
|---|---|---|
| 1. Przebieg produkcji | [production-rules.md](production-rules.md) | etapy 0..3, wejście/wyjście, pozytywne QA, zakres walidacji i profil pomiarów do uzupełnienia |
| 2. Błędy i poprawki | [corrections.md](corrections.md) | timeout vs nowy zamiar, odmowa/expiry, alternatywy recovery i ich koszty |
| 3. Dwa klucze | [quality-approval.md](quality-approval.md) | co podpisuje stacja/operator, wiązanie hash, niezależność, role, wyścigi |
| 4. Certyfikaty | [station-identity.md](station-identity.md) | proponowany payload/podpis, rejestr, aktywacja, odwołanie, rotacja i kompromitacja |
| 5. Uprawnienia i login | [access-control.md](access-control.md) | macierz ról, owner, propozycja SIWE/sesji/CSRF i logowanie stacji |

Parametry fizyczne produktu nie są wymyślone. Profile pomiarów, wariant
approval, format certyfikatu i transport sesji wymagają zatwierdzenia
w [decisions](decisions.md). Dokumenty nie zastępują istniejącego
[Stack_Tech.md](../Stack_Tech.md): uszczegóławiają reguły wykonania w jego
technologiach. Poglądowe skróty z opisu stosu nie definiują ABI ani finalności.

## Zalecana kolejność czytania

1. [source-alignment.md](source-alignment.md): nadrzędny PDF, zgodność i korekty.
2. [spec.md](spec.md): zakres MVP, aktorzy i 14 wymagań.
3. [diagrams.md](diagrams.md): mapa połączeń, importów i artefaktów.
4. [flows.md](flows.md): przebieg API, relayera, dwóch kluczy QA i paszportu.
5. [data-model.md](data-model.md): relacje, źródła prawdy i statusy.
6. [station-identity.md](station-identity.md): klucze i certyfikaty stacji.
7. [decisions.md](decisions.md): co wynika z PDF, a co wymaga ustalenia.
8. [plan.md](plan.md): 12 tygodni, właściciele, zadania i ich prerequisites.

## Instrukcje wykonawcze

- [architecture.md](architecture.md): granice odpowiedzialności i źródła prawdy.
- [protocol.md](protocol.md): planowane API, podpisy, raporty i idempotencja.
- [development.md](development.md): inicjalizacja, kolejność pracy, zasady zmian.
- [testing.md](testing.md): wymagane testy i odbiór całego przepływu.

Zmiana formatu danych wymaga aktualizacji protokołu, schematów i testów wszystkich
klientów. Gdy pojawi się istotna decyzja, dopisz datę, uzasadnienie i konsekwencje
do właściwego dokumentu. Nie twórz osobnych dokumentów dla każdego małego helpera.
Instrukcje lokalne są w README konkretnych katalogów.
