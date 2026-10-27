# ABI kontraktu

**Język:** JSON generowany przez kompilator Solidity; instrukcje Markdown.

Docelowy plik: `SupplyChain.abi.json`. Eksportuj po kompilacji, np. przez
`forge inspect SupplyChain abi --json`, i sprawdź format dla wybranej wersji Forge.
To polecenie wymaga wcześniej istniejącego kontraktu o nazwie `SupplyChain`.
Nie przepisuj ABI ręcznie i nie kopiuj całego `out/`.

Jeżeli generator Web3j wymaga bytecode, generuj go osobnym krokiem z tego
samego buildu do katalogu build backendu lub jawnie wersjonowanego artefaktu.
ABI i bytecode muszą odpowiadać jednej wersji kontraktu.

Zapisz wersję źródeł/kompilatora w danych wdrożenia. Po zmianie API/events
osoba 2 regeneruje wrapper Java, a osoba 3 aktualizuje typowane wywołania Viem.
Test integracyjny sprawdza zgodność ABI z rzeczywiście wdrożonym kodem.
