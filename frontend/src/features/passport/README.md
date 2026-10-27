# Publiczny paszport produktu

**Język:** TypeScript/TSX.

Plan: `PassportPage.tsx`, `ReportDetails.tsx`, `reportIntegrity.ts`.
Użytkownik wchodzi bez portfela przez batchId lub QR.
Pokaż podsumowanie, potwierdzoną historię, link IPFS i explorer danej sieci.

Przy odczycie JSON waliduj schemat, kanonizuj według wspólnego protokołu
i porównaj Keccak reportHash z potwierdzonym eventem/projekcją.
Oznaczenie pełnej weryfikacji on-chain wymaga odczytu właściwego kontraktu
lub jawnego opisania, że UI ufa projekcji backendu.

CID/raport nie są HTML do bezpośredniego renderowania.
Obsłuż 404 partii, timeout gateway, invalid JSON i niezgodny hash.
Nie pokazuj wewnętrznych poleceń, surowych podpisów czy danych administracyjnych.
Testy wykorzystują zarówno prawidłowy, jak i podmieniony raport.
