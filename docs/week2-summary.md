# Podsumowanie prac — tydzień 2

Stan na 10 listopada 2026 r. Podsumowanie obejmuje zmiany z PR-ów tygodnia 2
zmergowanych do `main`. Podział ról: [team-split.md](./team-split.md).

## Adam (@nimek06) — kontrakty i integracja z łańcuchem

PR [#8 — week2 v1](https://github.com/nimek06/PZ_supply_blockchain/pull/8):

- Dodał weryfikację certyfikatów stacji w Javie: weryfikację podpisu EIP-712,
  politykę certyfikatu oraz odczyt i sprawdzanie rejestru stacji.
- Rozszerzył testy kontraktu o zgodność calldata, testy inwariantów i pomiary
  gazu; dodał skrypty lokalnego wdrożenia, smoke testu Amoy i eksportu fixture'u calldata.
- Przygotował raport zużycia gazu i notatkę projektową dla przyszłego indexera.

## Mikołaj (@mikolaj-r) — backend zgłoszeń

PR [#6 — auth, batch API, walidacja i migracje](https://github.com/nimek06/PZ_supply_blockchain/pull/6):

- Rozwinął uwierzytelnianie i kontekst sesji oraz obsługę ról i uprawnień.
- Dodał API tworzenia i odczytu partii oraz walidację raportów względem profilu etapów.
- Rozszerzył model i trwałość danych o sekwencje partii, polecenia etapów,
  zatwierdzenia QA i próby transakcji; dodał migracje bazy danych i testy.

## Robert (@sm-tl) — symulator i scenariusze

PR-y [#5 — wektory negatywne](https://github.com/nimek06/PZ_supply_blockchain/pull/5)
i [#7 — generatory i scenariusze](https://github.com/nimek06/PZ_supply_blockchain/pull/7):

- Przygotował negatywne fixture'y naruszeń profilu etapów 0–3 oraz zgodny
  negatywny wynik QA, z oczekiwanymi kodami i statusami API.
- Dodał profile pomiarów i deterministyczne generatory telemetrii e-bike.
- Dodał negatywne warianty certyfikatów: wygasły oraz podpisany przez
  niezaufanego wystawcę.
- Zbudował obsługę scenariuszy YAML, walidację konfiguracji, odczyt danych
  z łańcucha, runner i tryb CLI; dodał preflight `--dry-run`, obsługę
  `accept-only` oraz skrypt i instrukcję lokalnego E2E.

## Kuba (@QbaRet) — storage, kontrakt API i fixture'y

PR [#9 — Week2/Kuba](https://github.com/nimek06/PZ_supply_blockchain/pull/9):

- Dodał adapter Kubo dla `ReportStorage`, walidator CID, konfigurację i testy
  jednostkowe oraz integracyjne.
- Rozszerzył testowanie zgodności backendu z OpenAPI oraz lokalne uruchamianie
  przez Compose i skrypt wdrożenia kontraktu.
- Dodał poprawne fixture'y raportów dla etapów 0–3 oraz generator i testy
  odtwarzalności.

## Integracja i status

Wszystkie wymienione PR-y są zmergowane do `main`. Przy integracji PR #9
rozwiązano konflikty z aktualnym `main` i dodano brakującą kopię
`signature-vector.json` wymaganą przez test backendu. Końcowe kontrole CI dla
PR #9 — backend, kontrakty i symulator — zakończyły się powodzeniem.
