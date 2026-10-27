# QR

**Język:** TypeScript/TSX. **Pakiety:** html5-qrcode, qrcode.react.

Plan: `BatchQrCode.tsx`, `BatchQrScanner.tsx`.
Generator dostaje publiczny URL paszportu. Skaner zwraca zweryfikowany batchId
lub dozwolony URL - nie nawiguj bez walidacji do dowolnej zeskanowanej strony.

Kamera wymaga HTTPS lub localhost i zgody użytkownika. Obsłuż brak urządzenia,
odmowę, błędny kod i wielokrotne odczyty. Stop/cleanup przy unmount,
zwalnianie strumienia i jeden aktywny skaner.
QR nie zawiera klucza, podpisu ani tokenu. Testuj parser bez kamery,
kamerę sprawdzaj dodatkowo manualnie na telefonie/tablecie.
