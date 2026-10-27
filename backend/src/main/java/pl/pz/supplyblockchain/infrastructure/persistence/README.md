# Baza danych i projekcje

**Język:** Java 21; Spring Data JPA. Schemat: migracje SQL w resources.

Plan: encje partii/projekcji (z numerem `B-RRRR-NNNN`), stage history, commands,
transaction attempts (podpisana transakcja zapisana przed wysłaniem), `qa_approvals`
(podpis `StageApproval`; nie ma `qa_projections`) i indexer checkpoint; repozytoria oraz mappery do domeny.
Dodaj rejestr certyfikatów stacji (audyt, zgodny z rejestrem on-chain): wystawca, serial/ID,
adres, ważność, `stageMask`, podpisane poświadczenie i odwołanie. Polecenie wskazuje certyfikat
użyty przy przyjęciu, bez publikowania go jako części raportu IPFS.
Nie wystawiaj encji z kontrolerów. Jawnie zapisuj typy/statusy i indeksy.

Unikalność zgłoszenia: wdrożenie, `batchId`, `stageId` i digest (brak nonce stacji);
duplikat identycznej treści wskazuje to samo polecenie, inny `reportHash` podlega regule
zastępowania z `docs/changes-v2.md`.
Tokeny auth: tylko skrót SHA-256.
Zapisuj CID, signed reportHash, deadline, stan, txHash, retryAt i błąd
w zakresie potrzebnym do bezpiecznego wznowienia.
Wartości uint256 (np. `deadline`) rozważ jako `numeric`/tekst; wybierz właściwą reprezentację.

Użyj blokad/wersjonowania do aktualizacji poleceń. Worker nie może przejąć
tego samego zadania dwa razy; potrzebna jest lease/lock i odzyskiwanie po awarii.
Zapis projekcji i checkpointu powinien być spójny transakcyjnie.
Testuj na PostgreSQL: constraints, konkurencję, rollback, migracje i restart.
