# Adaptery infrastruktury

**Język:** Java 21, biblioteki integracyjne.

- [blockchain](blockchain/README.md): Web3j, transakcje, weryfikacja podpisu, logi.
- [ipfs](ipfs/README.md): Pinata i pobieranie raportów.
- [persistence](persistence/README.md): encje, mapping i repozytoria PostgreSQL.
- [queue](queue/README.md): pobieranie zadań i kontrolowany worker relayera.

Adaptery implementują porty aplikacji. Nie wywołują kontrolerów.
Mapuj błędy zewnętrznych bibliotek na jawne błędy aplikacji,
z zachowaniem przyczyny w logu bez sekretów.
Timeout i retry muszą wynikać z rodzaju operacji, nie z globalnego catch.
