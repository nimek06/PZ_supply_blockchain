# Testy aplikacji Java

**Język:** Java 21; JUnit, MockMvc, Testcontainers, stuby HTTP/RPC.

Plan: `SubmitStageUseCaseTest`, `BatchControllerTest`, `SignatureVectorTest`,
`RelayerWorkerIT`, `PersistenceIT`, `ChainEventIndexerIT`.
Gdy rośnie liczba testów, odtwórz podpakiety api/application/domain/infrastructure.

Sprawdź schemat i statusy, idempotencję, poprawny i zmieniony podpis,
restart między krokami, konkurencję nonce, revert i reorg.
Test domeny nie wymaga Spring; test integracji DB korzysta z PostgreSQL.
Test podpisu odczytuje wspólne fixture'y, nie inną ręcznie utworzoną strukturę.

Testy muszą jawnie wskazywać profil/testową sieć i chronić przed połączeniem
z prawdziwą bazą/RPC. `@SpringBootTest` nie jest domyślnym wyborem dla każdej klasy.
Po inicjalizacji: `./mvnw verify`; sprawdź, czy runner rzeczywiście odkrył IT.

Wymagania PDF dodają przypadki certyfikatu stacji (wystawca, adres, ważność,
odwołanie) i niezależności podpisów kontroli. Test API może sprawdzać
WAITING_FOR_QA, ale egzekucję dwóch kluczy potwierdź również integracją
z rzeczywistym kontraktem na Anvil.
