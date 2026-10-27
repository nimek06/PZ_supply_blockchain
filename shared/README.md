# Wspólne artefakty integracyjne

**Języki:** JSON, YAML, Markdown. Bez współdzielonej biblioteki kodu runtime.

- [schemas](schemas/README.md): definicje API, raportu i EIP-712.
- [fixtures](fixtures/README.md): przykłady i wektory zgodności.
- [abi](abi/README.md): interfejs wygenerowany z kontraktu.
- [deployments](deployments/README.md): publiczne adresy i parametry sieci.

Najpierw zespół zatwierdza [protokół](../docs/protocol.md), potem tworzy pliki.
Zmiana podpisywanego pola to zmiana integracyjna, nie lokalny refactor.
Obowiązują zmiany [changes-v2](../docs/changes-v2.md).
W jednym PR zmieniaj specyfikację, schematy i związane testy.

ABI należy do osoby 1; OpenAPI koordynuje osoba 2; schemat telemetrii i wektory
osoby 1, 2 i 4 wspólnie (wektor EIP-712 sprawdzany w Python, Java, TS/viem i Solidity); frontend weryfikuje zgodność odczytu i hashy.
Wskazuj wersję protokołu oraz kontraktu. Nie zapisuj sekretów ani build cache.
