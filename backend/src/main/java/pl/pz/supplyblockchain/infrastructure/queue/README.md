# Trwała kolejka relayera

**Język:** Java 21; scheduler/executor i PostgreSQL.

Plan: `RelayerWorker`, mechanizm przejmowania polecenia i zapis prób/retry.
Na początek jeden worker wysyłający transakcje jednego konta. Kolejka DB
jest jedynym źródłem zadań (bez `BlockingQueue`/`@Async` jako kolejki).

Przejmij zadanie z blokadą/lease, zapisz stan, wykonaj etap poza transakcją DB,
zapisz wynik. Po restarcie wznawiaj od ostatniego trwałego kroku.
Retry z backoff i limitem prób dotyczy przejściowych błędów, nie błędnego podpisu,
konfliktu stanu czy trwałego revertu.

Nie uruchamiaj drugiego procesu wysyłającego przez samo zwiększenie liczby replik.
Synchronizacja musi obejmować nonce relayera globalnie, nie tylko dany wątek.
Po unknown RPC outcome wyślij ponownie identyczne zapisane bajty i uzgodnij hash (bez fee-bump).
Testuj równoczesne stacje, restart po pinowaniu/send, lease expiry i shutdown.

Przy etapie kontroli po pinowaniu zapisz WAITING_FOR_QA i zwolnij lease:
nie blokuj całego workera ani nie rezerwuj nonce relayera podczas oczekiwania
na operatora zmiany. Wznów po zapisaniu podpisu `StageApproval` (`qa_approvals`) dla tego samego raportu.
Podpis relayera nie zastępuje niezależnego klucza operatora.
