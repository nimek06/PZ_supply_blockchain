# Baza danych i projekcje

**Język:** Java 21; Spring Data JPA. Schemat: migracje SQL w resources.

Plan: encje partii/projekcji, stage history, commands, transaction attempts
i indexer checkpoint; repozytoria oraz mappery do domeny.
Dodaj rejestr certyfikatów stacji: wystawca, serial/ID, adres,
ważność, podpisane poświadczenie i odwołanie. Polecenie wskazuje certyfikat
użyty przy przyjęciu, bez publikowania go jako części raportu IPFS.
Nie wystawiaj encji z kontrolerów. Jawnie zapisuj typy/statusy i indeksy.

Unikalność zgłoszenia oparta na sieci, kontrakcie, stacji i nonce;
duplikat identycznej treści wskazuje to samo polecenie.
Zapisuj CID, signed reportHash, deadline, stan, txHash, retryAt i błąd
w zakresie potrzebnym do bezpiecznego wznowienia.
Nonce uint256 nie mieści się w bigint PostgreSQL: wybierz właściwą reprezentację.

Użyj blokad/wersjonowania do aktualizacji poleceń. Worker nie może przejąć
tego samego zadania dwa razy; potrzebna jest lease/lock i odzyskiwanie po awarii.
Zapis projekcji i checkpointu powinien być spójny transakcyjnie.
Testuj na PostgreSQL: constraints, konkurencję, rollback, migracje i restart.
