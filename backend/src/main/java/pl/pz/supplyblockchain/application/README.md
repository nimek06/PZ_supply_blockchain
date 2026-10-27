# Przypadki użycia

**Język:** Java 21; orkiestracja aplikacji.

Plan: `SubmitStageUseCase`, `CreateBatchUseCase`, `GetBatchUseCase`,
`GetCommandStatusUseCase`, `ProcessStageCommand` i `IndexChainEvents`.
Nazwy dobieraj do rzeczywistej odpowiedzialności; nie każda metoda potrzebuje klasy.
Granice integracji deklaruj w [ports](ports/README.md).

Przyjmowanie raportu: walidacja spójności -> canonical hash -> weryfikacja
podpisu/roli/certyfikatu -> lookup idempotencji -> dla nowej operacji
wstępne reguły -> transakcja bazy z idempotencją -> commandId.
Worker osobno wykonuje pinowanie, zapis CID i przesłanie transakcji.
Kontrakt powtarza krytyczne sprawdzenia; stan może się zmienić po walidacji API.

Przy kontroli worker czeka na podpis `StageApproval` operatora zmiany
dla tego samego raportu (WAITING_FOR_QA, tabela `qa_approvals`). Kolejka kandydatów jest
chroniona i dostępna zanim etap zostanie ukończony.
Identyczny digest zwraca istniejące `commandId`. Nowy raport tego samego etapu:
zasady zastępowania z `docs/changes-v2.md` (m.in. `409 STAGE_ALREADY_PENDING`;
po FAILED dopiero gdy stary `deadline` minął w potwierdzonym bloku).
`qaPassed=false` daje wynik `REJECTED` (kontrakt emituje `StageRejected`).

Nie trzymaj transakcji DB podczas zewnętrznego HTTP. Każdy krok workera ma
stan umożliwiający restart. Odczyty zwracają projekcję i uprawniony status.
Testuj przez proste fake porty, w tym timeout i częściowo zakończone zadanie.
Nie importuj kontrolerów ani konkretnych encji JPA do przypadków użycia.
