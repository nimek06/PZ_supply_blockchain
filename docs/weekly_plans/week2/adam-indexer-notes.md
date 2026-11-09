# Tydzień 2 - notatka Adama: spike indexera

**Data:** 2026-11-09.
**Sieci:**
- Polygon Amoy, chainId `80002`: tylko odczyt przez publiczne RPC (publicnode, tenderly);
- Anvil z Foundry 1.8.6.

**Dla kogo:**
- T019 `ChainEventIndexer` (tydzień 4-5, przegląd: osoba 2);
- DEC-07 (liczba potwierdzeń).

**Projekt wyjściowy:** [data-model §5](../../data-model.md), [flows §6 i §8](../../flows.md).

## Wnioski

1. **Okna `eth_getLogs`:** logi czytamy oknami po **10 000 bloków** (`toBlock - fromBlock <= 9 999`).
   Rozmiar okna trzymamy w konfiguracji, bo darmowe RPC mają różne limity. W
   [tygodniu 1](../week1/adam-amoy-notes.md) drpc odrzucał już 1 000 bloków.
2. **Propozycja do DEC-07:**
   - Amoy: indexer czyta do bloku `finalized`. Ten blok jest tylko **1-2 bloki (1-2 s)** za
     `latest`, a poniżej niego reorg nie powinien wystąpić.
   - Lokalnie: `latest` i 1 potwierdzenie. Tag `finalized` na Anvilu wynosi `latest - 64`, więc
     przy automine (jeden blok na transakcję) indexer stałby w miejscu.
3. **Wykrywanie reorgu:** wystarcza hash bloku z checkpointu.
   - Zgodny hash oznacza, że wszystkie wcześniejsze bloki też są te same.
   - Przy niezgodności punkt rozwidlenia wskazuje najnowsza zgodna kotwica (poprzednie
     checkpointy).
   - Sprawdzone w 8 scenariuszach na Anvilu. Po każdym projekcja z logów była równa `getBatch`.
4. **Tożsamość logu:** obserwację logu identyfikuje `(blockHash, logIndex)`, a `txHash` służy
   tylko do łączenia z poleceniem. Ten sam `txHash` wystąpił:
   - w dwóch blokach (ta sama tx po reorgu);
   - w dwóch łańcuchach (Anvil po resecie).
5. **Zniknięcie transakcji:** reorg może usunąć transakcję bez śladu. Nie ma wtedy tx ani
   receipt, mempool jest pusty, a nonce znów wolny. Relayer wysyła ponownie te same bajty (ten sam
   hash), zgodnie z [flows §6](../../flows.md).

## Pomiary Amoy (tylko odczyt)

| Pytanie | Wynik |
|---|---|
| Czas bloku | średnio **1,000 s** (ostatnie 10 000 bloków) |
| `finalized` względem `latest` | **1-2 bloki** (8 próbek, publicnode i tenderly) |
| Tag `safe` | błąd „safe block not found” na obu RPC; nie używać |
| Limit `eth_getLogs`, publicnode | `toBlock - fromBlock = 10 000` przechodzi; `10 001` zwraca `-32701 exceed maximum block range: 10000` |
| Limit `eth_getLogs`, tenderly | 1 000 000 bloków przeszło |
| Czas zapytania | ok. **85 ms** na okno 10 000 bloków z filtrem adresu (5 kolejnych okien) |
| Nadrobienie doby przestoju | 86 400 bloków = 9 okien, ok. 0,8 s zapytań |
| `eth_getLogs` z `blockHash` (EIP-234) | działa na obu RPC; nieznany hash zwraca `-32000 unknown block` |

## Zdarzenia kontraktu

Dane z `forge inspect SupplyChain events`. Tematy to pola `indexed` (topic1, topic2).

| Zdarzenie | topic0 | Tematy | Dane |
|---|---|---|---|
| `BatchCreated` | `0x9cf341ce3b7a62269dbbf9911b29e64450e8a230c782c5218b8c7cef48da6d22` | `batchId` | `batchNumber` |
| `StageCompleted` | `0x26a1be5f542831cf062627364dce48cfe928943d21a2093a6606629ff5e7b55b` | `batchId`, `station` | `stageId`, `qaOperator`, `reportHash`, `ipfsCid` |
| `StageRejected` | `0x812bd5bce50e6eae831e55c6cd70e525efa4b107a4b7986c6c3b1a4e530b3417` | `batchId`, `station` | `stageId`, `reportHash`, `ipfsCid` |
| `StationRegistered` | `0x855f88f1044b4d1d98ad4e342d7e331ab46dfea21ee2663854d360c2e73e1082` | `station` | `stationId`, `expiresAt`, `stageMask` |
| `StationRevoked` | `0x1b8b5e18956b6ab414f60c0534cfaeadecfb2baaff599b9cb37b5038061938ca` | `station` | - |

Pozostałe zdarzenia z ABI to `MaxDeadlineWindowUpdated`, `RoleGranted`, `RoleRevoked`,
`RoleAdminChanged` i `EIP712DomainChanged`. Do decyzji w T019: zapisywać je surowo do audytu
czy pomijać.

## Projekt do T019

### Strumień i konfiguracja

Jeden strumień na wdrożenie:
- `address` i `deploymentBlock` bierzemy z `shared/deployments/<sieć>.json`.
- `eth_getLogs` dostaje tylko filtr `address`, bez `topics`. Dzięki temu mamy wszystkie zdarzenia
  w kolejności `(blockNumber, logIndex)` i jeden checkpoint.
- Podział według topic0 robi Java.

Propozycja ustawień (prefiks taki jak `supplychain.relayer.*`):

```yaml
supplychain:
  indexer:
    head-tag: finalized    # lokalnie: latest
    confirmations: 1       # 1 = sam blok z logiem; głowa = blok(head-tag) - (confirmations - 1)
    max-block-range: 10000
    poll-interval: 2s
    anchors: 64            # ile ostatnich checkpointów (numer, hash) trzymać jako kotwice
```

### Cykl

```text
head = numer(head-tag) - (confirmations - 1)
kotwice od najnowszej (kotwica = numer i hash checkpointu z wcześniejszego cyklu):
    blok o tym numerze ma ten sam hash -> from = numer + 1, koniec szukania
    blok nie istnieje albo ma inny hash -> gałąź zmieniona, sprawdź starszą kotwicę
brak zgodnej kotwicy -> from = deploymentBlock (pełna odbudowa) + alarm
from <= checkpoint -> REORG: logi z block_number >= from dostają canonical = false,
                      kotwice >= from są usuwane
from > head -> koniec cyklu, czekamy na nowe bloki
dla okien [a, b] po max-block-range od from do head:
    h = hash(b); logi = eth_getLogs(address, a, b)
    każdy log: hash(log.blockNumber) == log.blockHash; na końcu znowu hash(b) == h
    niezgodność -> porzuć cykl bez zapisu (RPC pokazał dwie gałęzie)
    jedna transakcja DB: upsert chain_events po (deployment, block_hash, log_index)
    z canonical = true, przeliczenie projekcji i statusów dotkniętych partii, checkpoint (b, h)
```

Zasady dodatkowe:

- **Zapis po reorgu:** oznaczenie `canonical = false` zapisujemy w tej samej transakcji DB co
  pierwsze okno po rozwidleniu. Inaczej API pokaże stan pośredni, bez starych i bez nowych logów.
- **Przeliczanie projekcji:** projekcje dotkniętych partii liczymy od nowa z kanonicznych logów.
  Nie odejmujemy zdarzeń. To prostsze i zgodne z REQ-008.
- **Amoy (`head-tag: finalized`):**
  - Blok `finalized` niższy niż checkpoint oznacza opóźniony węzeł za load balancerem, a nie
    reorg. Pomijamy wtedy cykl.
  - Inny hash bloku poniżej `finalized` oznacza naruszenie finalności: alarm i ta sama ścieżka
    REORG.
- **Błąd `unknown block`:** odpowiedź `eth_getLogs` z `blockHash` z tym błędem nie dowodzi reorgu,
  bo węzeł może być opóźniony. Decyduje porównanie hashy nagłówków.
- **Statusy poleceń:**
  - Domenowy CONFIRMED nadaje tylko indexer ([flows §8](../../flows.md)).
  - Gdy reorg usuwa log, status wraca do ponownej weryfikacji. [protocol](../../protocol.md):
    „Reorganizacja może cofnąć pozorne potwierdzenie”.
  - Worker uzgadnia receipt i w razie potrzeby wysyła te same bajty.
  - Przy `finalized` na Amoy taka sytuacja nie powinna wystąpić.
- **Szacunek opóźnienia na Amoy:** finalizacja 1-2 s plus polling (średnio 1 s przy interwale
  2 s), razem ok. **2-4 s** od bloku z tx do projekcji. Koszt: ok. 4 zapytania na cykl, czyli
  ok. 2 zapytania/s.

## Wyniki spike'u na Anvilu

Prototyp był skryptem bash poza repozytorium. Indexer działał na pliku TSV z kotwicami i polem
`canonical`. Po każdym kroku projekcja z kanonicznych logów była porównywana z `getBatch`.

Dane testowe:
- partie `B-2026-0001..0005` (B1-B5);
- stacja ST-001;
- klucze deweloperskie Anvila.

| # | Scenariusz | Wywołania | Wynik |
|---|---|---|---|
| 1 | Zwykłe indeksowanie: B1, etapy 0-1 | `createBatch`, `completeStage` | 3 logi kanoniczne, projekcja = kontrakt |
| 2 | Gałąź do porzucenia: B2, etap 0 B2, odrzucenie etapu 2 B1 | `evm_snapshot` | 3 logi kanoniczne (bloki 7-9) |
| 3 | Reorg na tej samej wysokości: B3 i 2 puste bloki | `evm_revert`, `evm_mine` | hash bloku 9 inny; kotwica 9 niezgodna, kotwica 6 zgodna; logi z bloków 7-9 niekanoniczne, B3 kanoniczne. `eth_getLogs` po starym `blockHash` zwraca `-32000 unknown block` |
| 4 | Ta sama tx w innym bloku | `cast mktx`, `cast publish` przed i po `evm_revert` | ten sam `txHash`; blok 10 niekanoniczny, blok 11 kanoniczny |
| 5 | Wymiana bloków | `anvil_reorg` z parametrami `[2, []]` | bloki 10-11 wymienione na puste, log B4 niekanoniczny. Tx zniknęła: brak tx i receipt, `txpool` pusty, nonce znów 7 |
| 6 | Tagi bloków | `anvil_mine 0x46` | przy `latest` 81: `safe` 49, `finalized` 17 (−32 i −64); przy `latest` 11 oba = 0 |
| 7 | Reorg do niższej wysokości: B5, cykl, cofnięcie | `evm_snapshot`, `evm_revert` | głowa 81 < checkpoint 82, kotwica 82 nie istnieje. Log B5 niekanoniczny, skan czeka; po nowym bloku wznawia od 82 |
| 8 | Nowy łańcuch pod tym samym adresem | `anvil_reset`, ponowne wdrożenie | ten sam adres i `deploymentBlock` 1; żadna kotwica nie pasuje, więc pełna odbudowa. `createBatch` B1 ma ten sam `txHash` w obu łańcuchach |

Format `anvil_reorg` z obiektem (`[{"depth": 2, ...}]`) Anvil odrzuca błędem
`-32602 invalid type: map, expected u64`.

Uwagi poboczne:
- **Restart Anvila:** daje identyczny `local.json` (ten sam adres i `deploymentBlock`). Nowy
  łańcuch rozpozna tylko porównanie hashy (scenariusz 8), więc indexer nie może ufać samemu
  numerowi checkpointu.
- **`anvil_reset`:**
  - Blok 0 dziedziczy base fee starego łańcucha (13 686 wei), a następny blok wymaga 1 gwei.
  - Przez to `forge create` bez `--gas-price` dostaje
    `-32003 max fee per gas less than block base fee`.
  - Do resetu środowiska lepiej zrestartować proces albo kontener Anvila.

## Jak odtworzyć

Ten sam zestaw wywołań posłuży testowi reorgu w T019:

```bash
anvil --port 18545                                               # osobny port, nie koliduje z Compose
RPC_URL=http://127.0.0.1:18545 contracts/script/deploy-local.sh   # kontrakt, role, stacja ST-001
cast rpc --rpc-url http://127.0.0.1:18545 evm_snapshot           # -> "0x0"
cast rpc --rpc-url http://127.0.0.1:18545 evm_revert 0x0         # -> true
cast rpc --rpc-url http://127.0.0.1:18545 anvil_reorg 2 '[]'     # wymienia 2 ostatnie bloki
cast rpc --rpc-url http://127.0.0.1:18545 anvil_mine 0x46        # 70 pustych bloków
cast block finalized --rpc-url http://127.0.0.1:18545 -f number  # latest - 64
```

Scenariusze 3, 4, 5, 7 i 8 to gotowe przypadki testowe dla T019.

## Otwarte pytania (T019, DEC-07)

1. **DEC-07:** zatwierdzić `finalized` na Amoy. Pomiar powtórzyć w tygodniu 5 na RPC wybranym do
   demo: czy obsługuje `finalized` i z jakim opóźnieniem.
2. **Limity RPC:** sprawdzić limity zapytań publicznego RPC przy pollingu co 2 s (ok. 170 tys.
   zapytań na dobę) albo wydłużyć interwał.
3. **Polling czy `eth_subscribe`:** flaga `removed` jest tylko w subskrypcjach i wymaga WebSocket.
   Propozycja: polling, bo działa na HTTP, a reorg wykrywa porównanie hashy.
4. **Zdarzenia administracyjne** (`RoleGranted`, `RoleRevoked`, `MaxDeadlineWindowUpdated`):
   projekcja czy tylko surowy zapis do audytu?
5. **Kotwice:** spike trzymał historię checkpointów. Wersja produkcyjna może użyć nieaktywnych
   wierszy `indexer_checkpoints` albo małej tabeli. Alternatywa bez historii: hashe bloków z
   kanonicznymi logami w `chain_events`. Zasada jest ta sama, tylko ponowny skan po reorgu jest
   dłuższy. Decyzja przy przeglądzie T019 z osobą 2.
