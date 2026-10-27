# Źródła backendu

**Języki:** Java, YAML/properties i SQL.

- [main](main/README.md): aplikacja oraz zasoby runtime.
- [test](test/README.md): testy i ich zasoby.

Maven rozpoznaje te katalogi standardowo. Nie umieszczaj tu manifestu,
wrappera ani wyniku kompilacji. Manifest należy do korzenia backendu,
wynik do `target/`. Nie mieszaj testów z kodem produkcyjnym.
