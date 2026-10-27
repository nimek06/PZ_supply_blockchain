# Funkcje biznesowe

**Język:** TypeScript/TSX.

- [batches](batches/README.md): wspólne dane partii i query hooks.
- [operator](operator/README.md): wyszukiwanie i obsługa produkcji.
- [qa](qa/README.md): działania inspektora z portfelem.
- [passport](passport/README.md): publiczny odczyt i integralność raportów.

Zacznij w każdej funkcji od kilku płaskich plików: page, komponenty,
hooks i testy. Nie twórz osobnych folderów dla każdego typu/stałej.
Operator i passport korzystają z batches; batches nie importuje ich ekranów.
Ogólne integracje są w lib, prymitywy UI w components.
