# QR

**Język:** TypeScript/TSX. **Pakiety:** skaner wybrany po spike'u (`html5-qrcode` słabo utrzymywana; kandydaci `qr-scanner`, `@yudiel/react-qr-scanner`), qrcode.react.

Plan: `BatchQrCode.tsx`, `BatchQrScanner.tsx`.
Generator dostaje publiczny URL paszportu. Skaner zwraca zweryfikowany numer partii (`B-RRRR-NNNN`)
lub dozwolony URL - nie nawiguj bez walidacji do dowolnej zeskanowanej strony.

Kamera wymaga HTTPS (lokalny HTTPS/tunel także dla telefonu w sieci lokalnej) i zgody użytkownika. Obsłuż brak urządzenia,
odmowę, błędny kod i wielokrotne odczyty. Stop/cleanup przy unmount,
zwalnianie strumienia i jeden aktywny skaner.
QR nie zawiera klucza, podpisu ani tokenu. Testuj parser bez kamery,
kamerę sprawdzaj dodatkowo manualnie na telefonie/tablecie.
