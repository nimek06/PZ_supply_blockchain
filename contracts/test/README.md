# Testy kontraktu

**Język:** Solidity (`*.t.sol`), runner Forge, `forge-std/Test`.

Zacznij od `SupplyChain.t.sol`. Setup wdraża nowy kontrakt, tworzy testowe
adresy i jawnie nadaje role. Użyj `vm.prank`, `vm.expectRevert`, `vm.expectEmit`,
`vm.warp` i `vm.sign`; klucze są wyłącznie lokalnymi danymi testu.

Testuj: poprawny etap, zła kolejność, nieznana partia/stacja, brak roli relayera,
stacja wyrejestrowana/wygasła/poza `stageMask`, `qaPassed=false` (event `StageRejected`,
stan partii bez zmian), brak podpisu approval, wygasły `deadline`/`approvalDeadline`,
deadline powyżej okna maksymalnego, ponowne ukończenie tego samego `(batchId, stageId)`,
inny chainId/adres kontraktu i każdą zmianę podpisanego pola.
Po rewercie stan jest niezmieniony.
Porównuj argumenty eventów i stan, nie tylko brak revertu.

Dla kontroli przetestuj oba niezależne klucze: stacja i operator zmiany.
Sam podpis stacji, sam podpis approval operatora, approval dla innego hash
oraz ten sam adres po obu stronach nie mogą zamknąć kontroli.
Po odebraniu roli operatorowi stary podpis approval nie może dać przejścia.
Testuj też `createBatch(string)` (`batchId` = keccak256 numeru, duplikat numeru).

Dodaj fuzz dla identyfikatorów, deadline i kolejności; invariant: bez autoryzowanego
poprawnego podpisu nie można przesunąć produkcji. Wspólny wektor z
[fixtures](../../shared/fixtures/README.md) musi przechodzić także tutaj.
Po inicjalizacji: `forge test`, a dla pliku `forge test --match-path test/SupplyChain.t.sol`.

## Wspólny wektor podpisu (`SignatureVector.t.sol`)

`test_SharedVector` czyta `shared/fixtures/signature-vector.json` i jest pomijany (SKIP),
dopóki plik nie istnieje. Domena: `SupplyChain`/`1`, chainId `31337`, kontrakt pod stałym
adresem `0x5FbDB2315678afecb367f032d93F642f64180aa3` (test wdraża go tam przez `deployCodeTo`).
Oczekiwane ścieżki JSON (pozostałe pola pliku są dowolne):

```text
.domain.chainId, .domain.verifyingContract
.stageCompletion.message.{batchId, stageId, reportHash, qaPassed, deadline}
.stageCompletion.{digest, signature, signer}
.stageApproval.message.{batchId, stageId, reportHash, deadline}
.stageApproval.{digest, signature, signer}
```

Do czasu publikacji wektora działa wektor tymczasowy (`test_ProvisionalVector_*`): klucze
Anvil #1 (stacja) i #2 (operator), digesty i podpisy wygenerowane niezależnie przez `cast`.

## Zgodność ABI `completeStage` (`CompleteStageCalldata.t.sol`)

`shared/fixtures/complete-stage-calldata.json` zawiera wejścia `completeStage(...)` i oczekiwane
calldata zakodowane niezależnie przez `cast calldata`. Przypadki: etap 0 i 1 z pustym
`approvalSignature` (`approvalDeadline = 0`), odrzucenie kontroli (`qaPassed = false`), zatwierdzona
kontrola z wektora wspólnego i etap 3 z CID o długości `MAX_CID_LENGTH`. Test sprawdza
`abi.encodeCall` == fixture, zgodność przypadku kontroli z `signature-vector.json` oraz wykonuje
surowe bajty kolejno na jednej partii (eventy i stan). Ten sam plik czyta
`CompleteStageCallEncoderTest` w backendzie (kopia w `backend/src/test/resources/fixtures/`,
pilnowana przez `SharedFixtureCopiesTest`).

Fixture odtwarza `script/export-calldata-fixture.sh` (wymaga `cast`, `jq`); `--check` kończy się
błędem, gdy plik nie odpowiada wektorowi wspólnemu. Po zmianie `signature-vector.json` wygeneruj
fixture ponownie i skopiuj go do backendu.

## Inwariant: partia nie przesuwa się bez poprawnych podpisów (`SupplyChain.invariant.t.sol`)

`SupplyChainHandler` wdraża własny kontrakt i role, a następnie rejestruje stacje:
- zwykłą;
- „podwójną” (stacja + `QA_INSPECTOR_ROLE`);
- z maską `0x01`;
- wygasającą po dobie;
- wyrejestrowaną.

Operator QA A ma rolę na stałe, operatorowi B kampania ją nadaje i odbiera. Digesty EIP-712 handler
liczy sam, bez widoków kontraktu.

Akcje kampanii:
- tworzenie partii (maks. 8);
- autoryzowane ukończenie (dowolna uprawniona stacja i dla kontroli dowolny inny inspektor) oraz
  autoryzowane odrzucenie z powtórką;
- 22 rodzaje sfałszowanych wywołań;
- powtórka przyjętego wywołania;
- dowolne bajty podpisu i approval od fuzzera;
- zmiana roli operatora B;
- upływ czasu.

Każde sfałszowane wywołanie ma pozostałe pola poprawne, więc musi się wycofać dokładnie na
sprawdzeniu, w które celuje (oczekiwany selektor błędu).

Inwarianty:
- `nextStage` każdej partii = liczba autoryzowanych ukończeń (stan „ghost”), `≤ STAGE_COUNT`,
  `completed` ⇔ `nextStage == STAGE_COUNT`;
- żadne sfałszowane, powtórzone ani dowolne wywołanie nie przechodzi;
- każde sfałszowane wywołanie wycofuje się z oczekiwanym błędem;
- każde autoryzowane wywołanie przechodzi.

`afterInvariant` odrzuca pustą kampanię (bez ukończeń lub bez ataków), ale tylko dla sekwencji
≥ 64 wywołań. Dzięki temu skrócony kontrprzykład odtworzony po naprawie przechodzi.

Konfiguracja inline: `runs = 64`, `depth = 128`, `fail-on-revert = true` (każdy revert w samym
handlerze to błąd testu); czas ok. 3 s.

Przykładowa kampania:
- ok. 990 autoryzowanych ukończeń i 1000 odrzuceń;
- 900 ataków, wszystkie 22 rodzaje (najrzadszy 13 razy);
- 2000 powtórek i 1000 wywołań z dowolnymi bajtami;
- ok. 100 partii przeszło całą trasę.

Test mutacyjny: każda z 10 mutacji kontraktu łamie inwariant:
- brak `onlyRole(RELAYER_ROLE)`;
- pominięte approval kontroli;
- brak maski etapów;
- odrzucenie przesuwa partię;
- brak wygasania stacji;
- brak kontroli kolejności etapu;
- stacja = operator;
- powtórka odrzucenia;
- brak górnej granicy deadline;
- digest bez `batchId`.

```bash
forge test --match-path test/SupplyChain.invariant.t.sol -vv
```

Znaleziony kontrprzykład Forge zapisuje w `cache/invariant/failures/` (ignorowane przez git)
i odtwarza go przy kolejnym uruchomieniu. Usuń ten katalog, żeby zacząć od nowa.

## Scenariusz gazu (`SupplyChain.gas.t.sol`)

Stały scenariusz dla `--gas-report`:
- pełna trasa partii (etap 2 z akceptacją QA);
- odrzucenie;
- odrzucenie z CID o długości `MAX_CID_LENGTH` (najdroższy wariant `completeStage`);
- operacje administratora.

Kontrakt jest wdrażany przez `new`, bo raport Forge liczy tylko kontrakty utworzone przez CREATE,
a `deployCodeTo` z `SupplyChainBase` używa `vm.etch`. Samo `forge test --gas-report` miesza te
liczby z kampanią inwariantów, dlatego plik uruchamiaj osobno:

```bash
forge test --match-path test/SupplyChain.gas.t.sol --gas-report
forge test --match-path test/SupplyChain.gas.t.sol -vvvv   # gaz każdego wywołania
```

Przy `isolate = true` (domyślne) gaz wywołań zmieniających stan = `gasUsed` transakcji.
Wyniki i koszt w POL: [raport gazu](../../docs/weekly_plans/week2/adam-gas-report.md).

## Test skryptu Amoy

`test/amoy-smoke.test.sh` uruchamia osobny Anvil z chainId 80002 i kopię projektu w katalogu
tymczasowym. Sprawdza symulację, pełny smoke, ponowienie, opłaty i odmowę błędnych ustawień.
Używa wyłącznie publicznych kluczy Anvila; nie wysyła transakcji na Amoy i nie nadpisuje
lokalnego `broadcast/` ani `shared/deployments/amoy.json`.

```bash
bash test/amoy-smoke.test.sh
# Gdy port 18547 jest zajęty:
TEST_ANVIL_PORT=18548 bash test/amoy-smoke.test.sh
```
