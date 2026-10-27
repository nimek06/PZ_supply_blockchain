# Migracje Flyway

**Język:** SQL zgodny z PostgreSQL.

Plan: `V1__initial_schema.sql`, potem kolejne numerowane migracje.
Twórz tabele projekcji partii, historii, poleceń, prób transakcji i checkpointów
z constraints/idempotencją potrzebnymi workerowi.
Znaczniki czasu zapisuj z jasną strefą, np. `timestamptz`; domenowe Unix seconds
mapuj jawnie. Zdefiniuj indeksy wyszukiwania zadań i historii.

Nie modyfikuj migracji już zastosowanej w dzielonym środowisku.
Nowa zmiana to nowy plik. Sprawdź pustą bazę i upgrade ze wcześniejszej wersji
na PostgreSQL. Migracje nie robią HTTP, nie wdrażają kontraktów i nie zawierają
produkcyjnych danych/sekretów. JPA ma walidować, nie samodzielnie zmieniać schemat.
