# Mikołaj — tydzień 3

**Cel:** bezpieczne trwałe przyjęcie polecenia i przygotowanie kolejki dla workera.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 3](../week3-plan.md), sekcja P3.2.
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).
Kontynuacja: [tydzień 4](../../week4/plans/mikolaj-plan.md).

## Zadania

- [ ] W3-04 [Plan:3.2] T010/T011/T015: wpiąć istniejącą weryfikację certyfikatu
  do przyjęcia etapu. Odrzucać nieznany, wygasły, odwołany i niezgodny certyfikat;
  brak RPC nie może oznaczać pozytywnej weryfikacji. Sprawdzić role, owner,
  logout oraz rozróżnienie `401`/`403`.
- [ ] W3-05 [Plan:3.2] T014/T015: domknąć idempotencję i regułę zastępowania.
  Zastąpienie po FAILED wymaga czasu potwierdzonego bloku i rozstrzygnięcia prób,
  nie tylko zegara JVM. Constraints i zmiany schematu dodawać kolejną migracją,
  bez przepisywania V1–V5.
- [ ] W3-06 [Plan:3.2] T014/T018: przygotować przejęcie/lease i trwałe aktualizacje
  statusów. Uzgodnić porty dla jednego workera: krótkie transakcje DB, I/O poza
  blokadą i odzysk wygasłego lease. Z Adamem uzgodnić porty checkpoint/projekcji.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W3-05: przegląd istniejącego przyjęcia, portów i constraints | lista konkretnych luk, bez przepisywania API |
| Wtorek | W3-04: certyfikat, auth, role i owner | weryfikacja przed trwałym przyjęciem |
| Środa | W3-05: zastępowanie i konkurencja; uzgodnienia z Adamem | bezpieczna idempotencja |
| Czwartek | W3-06: lease i porty workera | gotowe wejście dla tygodnia 4 |
| Piątek | odbiór trwałego `202` z Robertem | wyniki i jawne pozostałe blokady |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium:
`backend/src/main/java/pl/pz/supplyblockchain/application/SubmitStageService.java`,
`CreateBatchService.java`, `AuthService.java`, `RoleService.java`,
`SessionContextService.java`, `CommandQueryService.java`, porty w
`application/ports/`, adaptery w `infrastructure/persistence/`
i migracje w `backend/src/main/resources/db/migration/`.

Adam dostarcza adapter certyfikatu; Ty podpinasz go do przyjęcia.
Utrzymujesz OpenAPI i uzgadniasz zmiany DTO z Kubą i Robertem.

## Zależności i odbiór

W3-05 można zacząć od razu. W3-04 wymaga W3-02 Adama.
W3-06 wymaga W3-03 oraz W3-05. Przegląd: Adam dla łańcucha, Robert dla API.

Odbiór: `202` dopiero po trwałym zapisie; 10 identycznych ponowień daje ten sam
`commandId`, także po restarcie. Inna treść aktywnego etapu daje
`409 STAGE_ALREADY_PENDING`. Negatywny certyfikat nie tworzy nowego polecenia.
Wyniki lease i reguły zastępowania muszą respektować rzeczywisty stan prób.
Kontrole według [testing](../../../../guides/testing.md), z PostgreSQL dla integracji.
Wymagania: REQ-002/004/005/006/009/012/013; mapowanie w planie wspólnym.

**Poza zakresem:** pełny sender/worker, adapter Pinata i wdrożenie backendu na Amoy.
