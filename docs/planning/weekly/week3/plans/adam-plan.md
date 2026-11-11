# Adam — tydzień 3

**Cel:** odtwarzalne lokalne wdrożenie, certyfikaty gotowe do wpięcia w przyjęcie
i uzgodniony projekt lokalnego indexera.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 3](../week3-plan.md), sekcja P3.1.
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).
Kontynuacja: [tydzień 4](../../week4/plans/adam-plan.md).

## Zadania

- [ ] W3-01 [Plan:3.1] T013: sprawdzić istniejący lokalny deploy, ABI i dane
  wdrożenia. Domknąć role administratora, relayera, stacji i QA oraz rejestrację
  stacji etapów 0..3. Ponowne uruchomienie nie może niejawnie resetować danych.
- [ ] W3-02 [Plan:3.1] T010: domknąć konfigurację istniejącego weryfikatora
  certyfikatu, polityki i odczytu rejestru dla rzeczywistego lokalnego wdrożenia.
  Przekazać Mikołajowi port `StationCertificateVerifier` i sposób interpretacji
  wyniku `StationIdentityVerification`; nie pisać drugiego weryfikatora.
- [ ] W3-03 [Plan:3.1] T019: uzgodnić z Mikołajem checkpoint, identyfikator
  deploymentu, korelację event → command, projekcje i granicę `CONFIRMED`.
  Lokalnie `latest` + 1 potwierdzenie, okno ponownego skanu i porównanie hash bloku.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W3-01: deploy, role, stacje i publiczne adresy | działające lokalne wdrożenie |
| Wtorek | W3-02: konfiguracja i sprawdzenie certyfikatu względem rejestru | wejście dla Mikołaja do W3-04 |
| Środa | W3-03: wspólne ustalenia persistence/indexera | uzgodnione porty i zakres migracji |
| Czwartek | przegląd W3-04/W3-05, zgodność ABI i reguły zastępowania | zamknięte uwagi integracyjne |
| Piątek | odbiór deployu i przekazanie do T019 | gotowe wejście dla indexera w tygodniu 4 |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium: `contracts/script/deploy-local.sh`,
`DeploySupplyChain.s.sol`, `RegisterStation.s.sol`, `shared/abi/SupplyChain.abi.json`,
`shared/deployments/local.json` oraz
`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/`.
Ustalenia indexera dopisz jako aktualizację notatki tygodnia 2 lub kontynuację
w `docs/planning/weekly/week3/notes/`.

Nie edytuj równolegle z Mikołajem `SubmitStageService.java`: on podpina
weryfikację do przyjęcia, Ty dostarczasz adapter i uzgodniony interfejs.

## Zależności i odbiór

W3-01 i W3-03 można zacząć niezależnie. Odbiór W3-02 na rzeczywistym rejestrze
wymaga W3-01. Java: przegląd Mikołaja; uruchomienie: przegląd Roberta.

Gotowe oznacza: odtwarzalny deploy bez cichego resetu, zgodne role/stacje/ABI,
weryfikację certyfikatu na lokalnym wdrożeniu i uzgodnione wejścia T019.
Kontrole własnego modułu według [testing](../../../../guides/testing.md).
Wymagania: REQ-001/004/005/008/013/014; pełne mapowanie w planie wspólnym.

**Poza zakresem:** implementacja całego indexera, badanie finalności Amoy,
deploy aplikacji na Polygon i Pinata.
