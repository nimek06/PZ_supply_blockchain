# Adam — wejście do T019 (tydzień 3)

**Status:** propozycja do uzgodnienia z Mikołajem przed migracją i implementacją.
Zakres lokalny według [DEC-17](../../../../decisions/local-blockchain-scope.md);
rozwinięcie historycznego spike'u:
[notatka tygodnia 2](../../week2/notes/adam-indexer-notes.md).

## Zakres wdrożenia

Konfiguracja wdrożenia pochodzi z `shared/deployments/local.json`:
`chainId`, znormalizowany adres kontraktu, `deploymentBlock` i
`deploymentTxHash`. Przed skanem indexer sprawdza `eth_chainId`, kod kontraktu
i zgodność adresu z konfiguracją backendu. Polecenia i rekordy łańcucha są
ograniczone do `(chainId, contractAddress)`; numer bloku sam nie identyfikuje
gałęzi po restarcie Anvila.

## Odczyt i checkpoint

- Tylko lokalny Anvil: głowa `latest`, jedno potwierdzenie oznacza, że blok
  zdarzenia jest wliczony. Nie używać `safe`, `finalized` ani parametrów Amoy.
- Odczytywać zakresami do 10 000 bloków, kolejno po
  `(blockNumber, transactionIndex, logIndex)`. Zapis checkpointu także dla
  zakresu bez logów, aby zachować hash bloku kotwiczącego.
- Polling co 2 s; zachować 64 ostatnie kotwice `(blockNumber, blockHash)` i przy
  każdym cyklu porównać je z RPC. Niezgodność cofa skan do najnowszej wspólnej
  kotwicy; brak wspólnej kotwicy oznacza skan od `deploymentBlock` i jawny alarm.
- Weryfikować `blockHash` każdego logu i hash końca zakresu przed zatwierdzeniem
  odpowiedzi RPC. Błąd lub zmiana gałęzi porzuca cykl bez częściowego postępu.

## Zdarzenia, projekcje i polecenia

Do projekcji przyjmować `BatchCreated`, `StageCompleted` i `StageRejected`.
Zachowywać również `StationRegistered` i `StationRevoked` jako zdarzenia
rejestru stacji. `RoleGranted`, `RoleRevoked`, `RoleAdminChanged`,
`MaxDeadlineWindowUpdated` i `EIP712DomainChanged` nie zmieniają projekcji
partii; ich trwałe przechowywanie do audytu pozostaje decyzją do uzgodnienia.

Identyfikator obserwacji to
`(chainId, contractAddress, blockHash, logIndex)`. `txHash` służy wyłącznie do
połączenia logu z `transaction_attempts.tx_hash`; dla dopasowanego polecenia
sprawdza się typ zdarzenia i jego dane (`batchId`, numer partii, etap,
`reportHash`, wynik). Brak pasującej próby nie blokuje projekcji kanonicznego
zdarzenia, ale nie pozwala przypisać go do polecenia. Nie dopasowywać wyłącznie
po ostatniej próbie polecenia.

`CONFIRMED` nadaje tylko indexer po kanonicznym zdarzeniu objętym polityką
potwierdzeń — nigdy worker na podstawie samego receipt. `StageCompleted`
kończy polecenie z `outcome=ACCEPTED`; `StageRejected` z
`outcome=REJECTED` bez przesunięcia etapu. Reorg unieważnia niekanoniczne logi
i odbudowuje dotknięte projekcje oraz statusy.

Migracja ma objąć `chain_events` (payload, pozycja, block hash, `canonical`),
`indexer_checkpoints` oraz projekcje partii/etapów. Zapis zdarzeń, projekcji,
statusów skorelowanych poleceń i checkpointu, w tym odznaczenie starej gałęzi,
ma być atomowy. Nie modyfikować V1–V6; numer nowej migracji i współwłaściciela
zapisu statusu uzgodnić z Mikołajem.

## Punkty do potwierdzenia przed T019

1. Czy Mikołaj akceptuje zakres tabel i atomową granicę zapisu powyżej, oraz
   który moduł/port jest właścicielem aktualizacji statusu polecenia?
2. Czy 64 kotwice i 2-sekundowy polling są odpowiednie dla lokalnego Anvila?
3. Czy wymienione zdarzenia administracyjne mają być trwale przechowywane jako
   surowy audyt, czy mogą być pomijane?

Do czasu potwierdzenia nie dodawać migracji ani nie deklarować interfejsu
indexera jako uzgodnionego.

## Postęp

- **W3-01:** wdrożenie przetestowane na świeżym Anvilu 31337. Role admin (#0),
  relayer (#3), station (#1) i QA (#2) są rozdzielone; station ma `stageMask=15`.
  Ponowne uruchomienie nie wdrożyło kolejnego kontraktu ani nie wysłało
  transakcji dla już zgodnej rejestracji. Sprawdzono także helper Compose na
  istniejącym wdrożeniu oraz odmowę uruchomienia go z chainId 80002.
- **W3-02:** `StationCertificateConfig`, port i adapter rejestru były już
  podłączone do `SubmitStageService`. `Web3jStationRegistryIT` przeszedł na
  właśnie wdrożonym Anvilu (4/4), w tym odczyt stacji oraz weryfikację fixture'u
  certyfikatu. Testy jednostkowe weryfikatora, rejestru i przyjęcia zgłoszenia
  również przeszły.
- **W3-03:** przygotowano powyższą propozycję; uzgodnienie z Mikołajem pozostaje
  otwarte.
