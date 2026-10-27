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

Przy kontroli worker czeka na niezależny podpis operatora zmiany
powiązany z tym samym raportem (WAITING_FOR_QA). Kolejka kandydatów jest
chroniona i dostępna zanim etap zostanie ukończony.
Identyczne ponowienie zakończonego polecenia zwraca dotychczasowe ID:
nie odrzucaj go tylko dlatego, że właśnie to polecenie zużyło nonce.

Nie trzymaj transakcji DB podczas zewnętrznego HTTP. Każdy krok workera ma
stan umożliwiający restart. Odczyty zwracają projekcję i uprawniony status.
Testuj przez proste fake porty, w tym timeout i częściowo zakończone zadanie.
Nie importuj kontrolerów ani konkretnych encji JPA do przypadków użycia.
