# Zasoby runtime

**Języki:** YAML/properties; SQL w [db](db/README.md).

Plan: `application.yml`, `application-local.yml`. Użyj odwołań do zmiennych
środowiskowych dla DB, RPC, chainId, kontraktu, JWT Pinaty i klucza relayera.
Sekrety mają być wymagane, nie zastępowane fikcyjną wartością domyślną.

Konfiguruj Flyway, JPA validate, timeouts i potwierdzenia.
Nie włączaj `ddl-auto=update` i nie zapisuj lokalnego konta administratora.
Profil testowy znajduje się w `src/test/resources/`.
Przykład konfiguracji środowiska musi wyraźnie oddzielać publiczne wartości
od sekretów. Nie pakuj kopii ABI bez określonego procesu jego aktualizacji.
