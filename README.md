# PZ_supply_blockchain

System śledzenia produkcji: stacje IoT podpisują raporty, backend przechowuje je
w IPFS i przekazuje transakcje do kontraktu, a panel pokazuje historię partii.

Źródłem nadrzędnym jest [opis projektu](Opis_Projectu%20%283%29.pdf).
MVP dotyczy modelowej linii montażowej, bez przebudowy fizycznej infrastruktury.
Zamknięcie etapu kontroli wymaga pozytywnego QA oraz autoryzacji **stacji
i operatora zmiany**. Każda symulowana stacja ma własny klucz i certyfikat.
Projekt planujemy na 12 tygodni; [mapa zgodności](docs/source-alignment.md)
odróżnia wymagania PDF od decyzji technicznych.

## Status i sposób korzystania z instrukcji

Repozytorium zawiera **strukturę oraz instrukcje implementacji**, nie gotową
aplikację. Pliki źródłowe, manifesty zależności, wrappery, konfiguracje usług
i testy opisane w instrukcjach należy dopiero utworzyć. Nie ma jeszcze
działających endpointów, zabezpieczeń ani integracji. Komendy w przewodnikach
dotyczą etapu po inicjalizacji odpowiedniego modułu.

W każdym katalogu znajduje się `README.md`: opis odpowiedzialności, języków,
planowanych plików oraz zasad pracy. Nie kopiuj całych instrukcji do kodu.
Zacznij od [przewodnika pracy](docs/development.md), potem przeczytaj README
swojego modułu i [protokół integracji](docs/protocol.md).

## Mapa projektu

```text
PZ_supply_blockchain/
|-- contracts/                         Solidity, Foundry, Anvil
|   |-- src/                           kontrakt produkcyjny
|   |-- test/                          testy Solidity
|   `-- script/                        wdrożenie i konfiguracja ról
|-- backend/                           Java 21, Spring Boot, Maven
|   `-- src/
|       |-- main/
|       |   |-- java/pl/pz/supplyblockchain/
|       |   |   |-- api/dto/            REST i modele transportowe
|       |   |   |-- application/ports/  przypadki użycia i granice integracji
|       |   |   |-- config/            konfiguracja Spring
|       |   |   |-- domain/            model i reguły biznesowe
|       |   |   `-- infrastructure/
|       |   |       |-- blockchain/    Web3j i indeksowanie zdarzeń
|       |   |       |-- ipfs/          klient Pinata
|       |   |       |-- persistence/   JPA i repozytoria
|       |   |       `-- queue/         trwała kolejka relayera
|       |   `-- resources/db/migration/ konfiguracja i migracje SQL
|       `-- test/
|           |-- java/pl/pz/supplyblockchain/
|           `-- resources/             konfiguracja i dane testowe
|-- simulator/                         Python 3.11+
|   |-- src/supply_chain_simulator/    modele, podpisy, HTTP i CLI
|   |-- scenarios/                    deklaratywne scenariusze
|   `-- tests/                        testy Python
|-- frontend/                          React, TypeScript, Vite
|   |-- public/                       pliki bez przetwarzania przez Vite
|   |-- src/
|   |   |-- app/                      start, router i dostawcy kontekstu
|   |   |-- assets/                   zasoby importowane w kodzie
|   |   |-- components/
|   |   |   |-- ui/                    bazowe elementy interfejsu
|   |   |   |-- timeline/              oś czasu
|   |   |   `-- qr/                    skanowanie i generowanie QR
|   |   |-- features/
|   |   |   |-- batches/               współdzielone widoki danych partii
|   |   |   |-- operator/              panel operatora
|   |   |   |-- qa/                    panel inspektora jakości
|   |   |   `-- passport/              publiczny paszport produktu
|   |   `-- lib/
|   |       |-- api/                   klient HTTP
|   |       `-- blockchain/            portfele i wywołania kontraktu
|   `-- tests/                        testy modułu frontendowego
|-- shared/                            wspólne artefakty, nie wspólny kod
|   |-- schemas/                      OpenAPI, JSON Schema, EIP-712
|   |-- fixtures/                     przykłady i wektory testowe
|   |-- abi/                          eksport interfejsu kontraktu
|   `-- deployments/                  publiczne adresy wdrożeń
|-- infrastructure/
|   |-- local/                        lokalne usługi i Compose
|   `-- ci/                           opis automatycznej weryfikacji
`-- docs/                              architektura, protokół i proces pracy
```

Pośrednie katalogi, takie jak `src/` i `java/`, również mają krótkie instrukcje
z odnośnikami do właściwych pakietów. Nie są osobnymi modułami.

## Podział zespołu i technologie

| Moduł | Osoba | Język i narzędzia | Odpowiedzialność |
|---|---|---|---|
| [contracts](contracts/README.md) | 1 | Solidity 0.8.24+, Foundry, OpenZeppelin | kolejność etapów, EIP-712, role, QA, zdarzenia |
| [backend](backend/README.md) | 2 | Java 21, Spring Boot 3.3+, Maven, Web3j, JPA | REST, IPFS, relayer, PostgreSQL, indeksowanie |
| [frontend](frontend/README.md) | 3 | TypeScript, React 18/19, Vite | operator, QA, publiczna historia, QR, portfel |
| [simulator](simulator/README.md) | 4 | Python 3.11+, eth-account, Pydantic, httpx | pomiary, podpisy, scenariusze pozytywne i negatywne |

Osoba 4 odpowiada także za testy integracyjne całego systemu i dokumentację.
Panel QA obsługuje operatora zmiany uprawnionego do współzatwierdzania kontroli;
nie wymaga dodatkowego, piątego członka zespołu.

Przewodniki przyjmują Foundry, Maven i npm jako jeden spójny wariant startowy.
Nie utrzymuj równolegle Hardhat i Foundry ani Maven i Gradle bez konkretnej
potrzeby. Wybierz dokładne, kompatybilne wersje zależności podczas inicjalizacji
i zapisz je w manifestach oraz dostępnych lockfile'ach.

Sieć lokalna: Anvil. Sieć docelowa: Polygon Amoy (`chainId = 80002`).
Pinata przechowuje raporty; PostgreSQL przechowuje projekcję stanu i trwałe
zadania. Blockchain pozostaje źródłem prawdy o potwierdzonych etapach.

## Przepływ zdarzenia

1. Stacja tworzy raport według wspólnego schematu i podpisuje dane EIP-712
   zawierające jego skrót, identyfikator etapu, nonce oraz termin ważności.
2. Backend waliduje żądanie i podpis, trwale zapisuje polecenie i zwraca `202`.
3. Worker pinuje raport w IPFS, zapisuje CID, a pojedynczy relayer przesyła
   transakcję do kontraktu. Przy etapie kontroli najpierw czeka na podpisane
   zatwierdzenie tego samego raportu przez operatora zmiany.
4. Kontrakt sprawdza podpis, role, nonce, kolejność etapów i bramkę QA.
5. Indexer aktualizuje projekcję po uzgodnionej liczbie potwierdzeń.
6. Frontend pokazuje osobno stan oczekiwania, sukces i błąd. Publiczny paszport
   umożliwia odczyt raportu z IPFS i porównanie jego skrótu z blockchainem.

Szczegóły i decyzje wymagające wspólnego zatwierdzenia opisuje
[protokół](docs/protocol.md). Przykładowe sygnatury funkcji są projektem
interfejsu, nie deklaracją istniejącego ABI.

## Kolejność realizacji

1. Uzgodnijcie etapy produkcji, zasady QA i format podpisywanych danych.
2. Utwórzcie schematy i wspólne wektory testowe.
3. Zaimplementujcie kontrakt i testy, następnie eksportujcie ABI.
4. Zbudujcie backend i symulator na tych samych danych testowych.
5. Zbudujcie frontend z kontrolowanymi odpowiedziami API, potem podłączcie API.
6. Uruchomcie całą ścieżkę lokalnie; dopiero potem wdrażajcie na Amoy.

## Pozostałe instrukcje

- **[Pięć obszarów przed kodowaniem](docs/README.md)**:
  [przebieg produkcji](docs/production-rules.md),
  [błędy i poprawki](docs/corrections.md),
  [dwa klucze kontroli](docs/quality-approval.md),
  [certyfikaty stacji](docs/station-identity.md),
  [uprawnienia i logowanie](docs/access-control.md).
- [Zgodność z opisem PDF](docs/source-alignment.md)
- [Diagramy i zależności](docs/diagrams.md)
- [Szczegółowe przepływy](docs/flows.md)
- [Model danych i statusy](docs/data-model.md)
- [Plan 12 tygodni i zadania zespołu](docs/plan.md)
- [Wymagania MVP](docs/spec.md) i [otwarte decyzje](docs/decisions.md)
- [Tożsamość i certyfikaty stacji](docs/station-identity.md)
- [Architektura i granice modułów](docs/architecture.md)
- [Protokół API, raportów i podpisów](docs/protocol.md)
- [Inicjalizacja i codzienna praca](docs/development.md)
- [Testowanie i kryteria odbioru](docs/testing.md)
- [Wspólne artefakty integracji](shared/README.md)
- [Infrastruktura i automatyzacja](infrastructure/README.md)

Nie zapisuj kluczy prywatnych, JWT Pinaty ani haseł w repozytorium.
W kodzie i nazwach plików używaj angielskiego; dokumentacja zespołu jest
po polsku. Nie twórz dodatkowego katalogu `utils/` zamiast przypisania
odpowiedzialności do istniejącego modułu.
