# Współdzielone komponenty

**Języki:** TypeScript/TSX, CSS.

- [ui](ui/README.md): prymitywy interfejsu.
- [timeline](timeline/README.md): prezentacja historii etapów.
- [qr](qr/README.md): etykiety i skaner.

Komponent wspólny otrzymuje dane i callbacks przez props.
Nie powinien znać routingu operatora, kluczy API czy sposobu pinowania.
Logika biznesowa i query hooks należą do features. Nie przenoś komponentu
tu tylko dlatego, że jest mały - powinien mieć rzeczywiste zastosowanie wspólne.
