# ABI kontraktu

**Język:** JSON generowany przez kompilator Solidity; instrukcje Markdown.

Docelowy plik: `SupplyChain.abi.json`. Eksportuj po kompilacji, np. przez
`forge inspect SupplyChain abi --json`, i sprawdź format dla wybranej wersji Forge.
Wiążący kształt powierzchni (v2): `createBatch(string)`, `completeStage(...)` z `stationSignature`,
`approvalSignature`, `registerStation`/`revokeStation`, `getStation`, zdarzenia `StageRejected`,
`StationRegistered`, `StationRevoked`; brak `approveStage` i `STATION_ROLE`. ABI generuje kompilator.
To polecenie wymaga wcześniej istniejącego kontraktu o nazwie `SupplyChain`.
Nie przepisuj ABI ręcznie i nie kopiuj całego `out/`.

Bytecode (np. do deployu z backendu/testów) eksportuj `forge inspect SupplyChain bytecode`
z tego samego buildu do katalogu build backendu lub jawnie wersjonowanego artefaktu.
ABI i bytecode muszą odpowiadać jednej wersji kontraktu.

Zapisz wersję źródeł/kompilatora i `evm_version` z `foundry.toml` w danych wdrożenia. Po zmianie API/events
osoba 2 aktualizuje kodowanie wywołań w Javie (Web3j tylko do kodowania/podpisu), a osoba 3 aktualizuje typowane wywołania Viem.
Test integracyjny sprawdza zgodność ABI z rzeczywiście wdrożonym kodem.
