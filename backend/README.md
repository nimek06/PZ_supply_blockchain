# Backend - osoba 2

**Języki:** Java 21, SQL, YAML/XML konfiguracji.
**Narzędzia:** Spring Boot 3.3+, Maven Wrapper, Web3j, Spring Data JPA,
PostgreSQL, Flyway, Spring RestClient i Pinata.
Według [PDF](../docs/source-alignment.md) backend jest integratorem
programowego prototypu, nie nowej fizycznej linii.

## Co utworzyć

W korzeniu: `pom.xml`, `mvnw`, `mvnw.cmd`, pliki `.mvn/wrapper/`.
Klasa startowa: `src/main/java/pl/pz/supplyblockchain/SupplyBlockchainApplication.java`.
Nie ma jeszcze aplikacji ani manifestu; start opisuje
[development](../docs/development.md).

Podział pakietów i resources opisuje [src](src/README.md).
API nie powinno bezpośrednio korzystać z JPA, Pinaty ani Web3j.
Konfiguracja Spring łączy porty z adapterami.

## Kolejność implementacji

Przed DTO i migracjami przeczytaj [reguły etapów](../docs/production-rules.md),
[poprawki i retry](../docs/corrections.md), [politykę approval](../docs/quality-approval.md)
oraz [macierz ról i propozycję sesji](../docs/access-control.md).
Schemat auth/certyfikatu/profilu pomiarów musi być zatwierdzony przed
implementacją; szczegółowy opis nie oznacza jeszcze podjętej decyzji.

1. Uruchom pustą aplikację i test startu z profilem testowym.
2. Zdefiniuj records DTO i walidację zgodną ze wspólnym schematem.
3. Zaimplementuj kanonizację, hash, weryfikację EIP-712 oraz aktywnego
   certyfikatu stacji zgodnego z signerem.
4. Dodaj migracje partii/projekcji, poleceń, idempotencji i checkpointów.
5. API przyjmuje polecenie dopiero po trwałym zapisie i zwraca `202`.
6. Dodaj adapter Pinaty i zapisywanie CID przed kolejnym krokiem.
7. Wygeneruj wrapper Web3j z ABI, dodaj pojedynczy writer relayera.
8. Dodaj indexer logów, potwierdzenia, deduplikację i obsługę reorg.
9. Udostępnij projekcję, statusy i historię; podłącz symulator i frontend.

Etap kontroli po pinowaniu przechodzi w WAITING_FOR_QA. Chroniona lista
kandydatów umożliwia operatorowi zmiany obejrzenie i zatwierdzenie raportu
własnym portfelem. Dopiero drugi, niezależny dowód pozwala wysłać completeStage.
Szczegóły: [flows](../docs/flows.md) i [certyfikaty](../docs/station-identity.md).
W PostgreSQL przechowuj lokalny rejestr certyfikatów/odwołań; nie publikuj
go bez potrzeby w publicznym paszporcie.

## Wzorce pracy

Używaj constructor injection i `@ConfigurationProperties` z walidacją.
DTO nie są encjami JPA. Transakcje bazy są krótkie: nie trzymaj otwartego
`@Transactional` podczas HTTP lub oczekiwania na receipt.
Mapuj wyjątki na Problem Details przez wspólny `@RestControllerAdvice`.
Nie łap `Exception` tylko po to, żeby zwrócić `null` lub pustą listę.

PostgreSQL może obsługiwać prostą trwałą kolejkę; Redis/RabbitMQ nie są
obowiązkowe. Wyłącz równoległe przydzielanie nonce jednego relayera.
Sam `@Async` nie zapewnia trwałości ani kolejności transakcji.

## Konfiguracja i dostęp

Sekrety: klucz relayera, JWT Pinaty, hasło bazy - zmienne środowiskowe lub
magazyn sekretów. Konfiguracja publiczna: RPC URL, chainId, adres kontraktu,
gateway IPFS, dozwolone originy CORS. RPC URL również może zawierać sekretny token.
Nie loguj go w całości. Profile `local` i `test` muszą być jawne.
Nie używaj `ddl-auto=update`; schemat bazy zmieniają migracje.

Publiczny odczyt paszportu nie oznacza publicznego endpointu statusu poleceń,
tworzenia partii ani administracji. Wprowadź i testuj docelową autoryzację.

## Sprawdzenie

Po inicjalizacji: `./mvnw verify`. Testy JUnit, MockMvc, PostgreSQL przez
Testcontainers, stub Pinaty/RPC i Anvil do integracji kontraktu.
Kryterium: poprawny podpis, trwałe przyjęcie, bezpieczny restart, jeden event
dla ponowionego żądania i stan `CONFIRMED` dopiero po potwierdzeniu.
