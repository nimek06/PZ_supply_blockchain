# Mikołaj — tydzień 5

**Cel:** poprawny sender i worker, dzięki którym tworzenie i etapy działają w pokazie GUI.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 5](../week5-plan.md), sekcja P5.2.
Wejście: [Twój tydzień 4](../../week4/plans/mikolaj-plan.md) i
[podsumowanie W4](../../week4/week4-summary.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W5-04 [Plan:4.1] T017: poprawić signer EIP-1559. Testować zdekodowany typ `0x02`, chainId, nonce, gas limit, opłaty, adres i calldata oraz rzeczywistą wysyłkę na Anvilu; zachować trwały zapis bajtów/hash przed send i odzysk tej samej próby.
- [ ] W5-05 [Plan:4.1] T018: najpierw domknąć konfigurację i działanie workera dla CREATE_BATCH i etapów 0–1, JCS/CID, preflight, retry i restart. Następnie przygotować etap 2: `qaPassed=true` ma czekać w `WAITING_FOR_QA` bez nonce i blokowania innych poleceń; `qaPassed=false` prowadzi do `StageRejected`. Potwierdza indexer, nie sam receipt.
- [ ] W5-06 [Plan:5.1] T021 (rozszerzenie po pokazie): dodać chronioną listę i szczegóły kandydatów zgodnie z OpenAPI, w tym paginację i testy uprawnień. Zwracać CID/hash/raport/deadline/domenę bez podpisów i sekretów. `POST approval` pozostaje na tydzień 6; W5-06 nie blokuje podstawowego pokazu etapów 0–1.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W5-04: poprawka i test signera | właściwy typ i pola transakcji |
| Wtorek | W5-04/W5-05: send na Anvilu | rzeczywiste transakcje workera |
| Środa | W5-05: etapy 0–1 i odzysk | pion dla pokazu GUI |
| Czwartek | stabilizacja pionu; W5-06 po jego gotowości | poprawki lub chroniony odczyt QA |
| Piątek | odbiór pokazu z Adamem, Kubą i Robertem | działające tworzenie i historia |

## Pliki i granice odpowiedzialności

`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/Eip1559TransactionSigner.java`,
`RelayerTransactionSender.java`, `infrastructure/queue/RelayerWorker.java`,
porty/persistence i `api/`; kontrakt w `shared/schemas/openapi.yaml`.
Korzystaj z istniejącego Kubo. Migracje i statusy uzgodnij z Adamem,
kontrakt API z Kubą. Nie wysyłaj nowej operacji po nieznanym wyniku RPC.

## Zależności i odbiór

W5-05 wymaga W5-04, W5-06 wymaga przygotowanego `WAITING_FOR_QA`;
pełny pion wymaga też ról W5-03. Przegląd: Adam dla transakcji i persistence,
Kuba dla API.

Odbiór podstawowy: tx `0x02`, partia utworzona w GUI i etapy 0–1 potwierdzone
przez indexer. Rozszerzenie: QA czeka bez nonce, negatywny etap nie przesuwa
partii, a API przestrzega ról i nie zwraca podpisów. Restart, retry oraz
awarie nie mogą dawać pozornego sukcesu.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-002/004/005/006/007/009/012/013/014.

**Poza zakresem:** `POST approval`, panel podpisu, fee-bump, Pinata i Amoy.
