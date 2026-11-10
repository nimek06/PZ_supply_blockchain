# Tydzień 2 - raport gazu kontraktu `SupplyChain` (Adam)

**Data:** 2026-11-09. **Narzędzia:** Foundry 1.8.6 (solc 0.8.24, `via_ir`, `evm_version = cancun`), Anvil.
**Kontrakt:** [SupplyChain.sol](../../../contracts/src/SupplyChain.sol) bez zmian: runtime 6 196 B,
initcode 7 056 B (limit EIP-170 to 24 576 B).

## Jak odtworzyć

```bash
cd contracts
forge test --match-path test/SupplyChain.gas.t.sol --gas-report   # tabela dla funkcji
forge test --match-path test/SupplyChain.gas.t.sol -vvvv           # gaz każdego wywołania
```

Scenariusz [SupplyChain.gas.t.sol](../../../contracts/test/SupplyChain.gas.t.sol):
- pełna trasa partii (etap 2 z akceptacją QA);
- odrzucenie;
- odrzucenie z CID o maksymalnej długości (128 znaków);
- operacje administratora.

Dlaczego osobny scenariusz zamiast samego `forge test --gas-report`:
- testy w `SupplyChain.t.sol` wdrażają kontrakt przez `deployCodeTo` (`vm.etch`, stały adres
  wektora podpisu). Raport Forge liczy tylko kontrakty utworzone przez CREATE, więc tych testów
  nie widzi.
- Pełny przebieg miesza dane z kampanii inwariantów: ok. 6 000 wywołań `completeStage` z losowymi
  danymi z handlera (min 28 868, maks. 68 353). Te liczby nie nadają się do budżetu.

Przy `isolate = true` (domyślne w Forge 1.8) każde wywołanie zmieniające stan jest osobną
transakcją. Raport pokazuje więc pełny gaz transakcji (21 000 + calldata + wykonanie), czyli to
samo co `gasUsed` w receipt. `Deployment Cost` w raporcie wynosi 0, bo wdrożenie jest w `setUp`;
koszt wdrożenia zmierzono na Anvilu.

## Wyniki

Kontrola krzyżowa: świeży Anvil (hardfork Osaka/BPO1), `forge create` i `cast send` z publicznymi
kluczami testowymi Anvil, odczyt `gasUsed` z receipt. Stan po pomiarze był zgodny z oczekiwanym:
pierwsza partia zamknięta po etapie 3, druga po odrzuceniu i ponowieniu na etapie 1.

| Operacja | Forge | Anvil `gasUsed` | Calldata |
|---|---:|---:|---:|
| wdrożenie `SupplyChain` | - | 1 453 011 | 7 120 B |
| `grantRole` | 51 134 | 51 122-51 134 | 68 B |
| `registerStation` | 71 882-71 894 | 71 894 | 132 B |
| `createBatch` | 52 668 | 52 668 | 100 B |
| `completeStage` etap 0 | 46 940 | 46 916 | 548 B |
| `completeStage` etap 1 | 46 976 | 46 928 | 548 B |
| `completeStage` etap 2 (stacja + akceptacja QA) | 54 852 | 54 840 | 644 B |
| `completeStage` etap 3 (zamknięcie partii) | 47 187 | 47 163 | 548 B |
| odrzucenie (`qaPassed = false`) | 66 293 | 66 305 | 548 B |
| odrzucenie, CID 128 znaków (najgorszy przypadek) | 67 913 | - | 612 B |
| etap 0 po odrzuceniu | - | 46 904 | 548 B |
| `revokeStation` | 30 760 | 30 760 | 36 B |
| `revokeRole` | 34 443 | - | 68 B |
| `setMaxDeadlineWindow` | 30 269 | - | 36 B |

Różnice Forge-Anvil (maks. 48 gazu) wynikają z innych danych, czyli kluczy i hashy raportów:
bajt zerowy calldata kosztuje 4 gazy, a niezerowy 16. Hardfork Anvila nie zmienia tych kosztów.
Kontrakt jest kompilowany dla `cancun`, a podłoga calldata z EIP-7623 (Prague) jest niższa od
`gasUsed` każdej operacji nawet przy samych niezerowych bajtach.

## Na co idzie gaz

- `completeStage` etapu 0 kosztuje 46 916 gazu:
  - ok. 25,6 tys. to koszt samej transakcji: 21 000 oraz ok. 4,6 tys. za 548 B calldata
    (wyliczone z [fixture calldata](../../../shared/fixtures/complete-stage-calldata.json));
  - ok. 21,3 tys. to wykonanie: sprawdzenie roli, odczyt partii, stacji i `maxDeadlineWindow`,
    digest EIP-712 i `ecrecover`, zapis etapu oraz event `StageCompleted` z CID.

  **Ponad połowa kosztu to sama transakcja, nie logika kontraktu.**
- Etap 2 kosztuje o 7,9 tys. więcej: drugi podpis (digest akceptacji i `ecrecover`), sprawdzenie
  `QA_INSPECTOR_ROLE` i 96 B więcej calldata.
- Odrzucenie kosztuje o 19,4 tys. więcej niż etap 0. Powodem jest nowy slot
  `rejectionRecorded[digest]` (SSTORE 0 → 1 = 20 000 gazu), który blokuje ponowny event
  `StageRejected` dla tego samego podpisu. Partia zostaje na etapie, więc odrzucenie z ponowieniem
  kosztuje ok. 66 tys. + 47 tys.
- CID o maksymalnej długości (128 znaków) dodaje ok. 1,6 tys. (calldata i dane eventu).
- `createBatch` zakłada jeden nowy slot (partia), a `registerStation` dwa (stacja).

## Koszt w POL (Amoy)

Koszt [POL] = `gasUsed` × (base fee + tip) / 10^18. Base fee na Amoy wynosi 64 wei, więc jest
pomijalny i o koszcie decyduje tip. `eth_maxPriorityFeePerGas` to sugestia węzła, a nie stała:
- 30 gwei 2026-10-28 ([notatka tyg. 1](../week1/adam-amoy-notes.md));
- 35,2 gwei 2026-11-09 (publicnode).

| Pozycja | Gaz | POL przy 30 gwei | POL przy 35,2 gwei |
|---|---:|---:|---:|
| wdrożenie kontraktu | 1 453 011 | 0,0436 | 0,0511 |
| smoke Amoy: wdrożenie, `grantRole` relayera, `registerStation`, partia, etap 0 | 1 675 623 | 0,0503 | 0,0590 |
| pełna trasa partii (`createBatch` + 4 × `completeStage`) | 248 515 | 0,0075 | 0,0087 |
| 100 partii | 24 851 500 | 0,75 | 0,87 |
| dodatkowe odrzucenie | 66 305 | 0,0020 | 0,0023 |
| liczba partii za 1 POL | - | ok. 134 | ok. 114 |

Limit gazu bloku Amoy (2026-11-09) wynosi 140 M. `completeStage` zajmuje ok. 0,03 % bloku,
a wdrożenie ok. 1 %, więc przepustowość łańcucha nie ogranicza MVP.

## Wnioski

1. Koszty są niskie i przewidywalne. Budżet testów przy 35 gwei: smoke na Amoy ok. 0,06 POL,
   symulator ze 100 partiami ok. 0,9 POL.
2. **Limit gazu relayera dla `completeStage`:** zmierzone maksimum to 67 913 (odrzucenie
   z maksymalnym CID), więc stały limit 100 000 daje ok. 47 % zapasu. Alternatywa to
   `eth_estimateGas` z zapasem; przy okazji wykrywa revert przed wysłaniem. Maksimum dotyczy
   formatu z fixture: podpisy 65 B i pusty approval poza zatwierdzaną kontrolą. Kontrakt sprawdza
   approval tylko na etapie 2 z `qaPassed = true`, więc dołączony gdzie indziej jedynie podnosi
   koszt calldata.
3. **Tip relayera:** wartość z DEC-11 (30 gwei) jest niższa od dzisiejszej sugestii węzła
   (35,2 gwei). Propozycja dla osoby 2: tip = `max(min-tip-gwei, eth_maxPriorityFeePerGas)`.
4. Nie proponuję optymalizacji kontraktu przed zamrożeniem ABI. Przykładowo zamiana
   `string ipfsCid` na `bytes32` oszczędziłaby szacunkowo ok. 1,5 tys. gazu (ok. 3 %) na etap,
   a wymagałaby zmiany ABI w backendzie, symulatorze i fixture'ach.
