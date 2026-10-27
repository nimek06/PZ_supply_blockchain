# Frontend - osoba 3

**Języki:** TypeScript (`.ts`, `.tsx`), CSS, HTML.
**Narzędzia:** React 18/19, Vite, npm, TanStack Query, Axios, Wagmi v2,
Viem, RainbowKit (wymaga `projectId` WalletConnect/Reown), Tailwind CSS, shadcn/ui,
biblioteka skanera QR wybrana po spike'u (`html5-qrcode` jest słabo utrzymywana;
kandydaci: `qr-scanner`, `@yudiel/react-qr-scanner`), qrcode.react.
Wybierz kompatybilne wersje, nie mieszaj instrukcji różnych wersji Tailwind.
Zmiany projektowe v2: [changes-v2](../docs/changes-v2.md) - wiążące.

Wymagany [zakres PDF](../docs/source-alignment.md): responsywny widok stacji,
ręczne wpisanie numeru partii (`B-RRRR-NNNN`) lub QR, zmiana statusu i publiczna oś czasu.
Widok QA obsługuje operatora zmiany: drugi niezależny klucz do zamknięcia
kontroli obok podpisu stacji, nie opcjonalnego trzeciego inspektora;
operator podpisuje `StageApproval` (EIP-712) portfelem, bez transakcji i gazu.

## Co utworzyć

W korzeniu: `package.json`, `package-lock.json`, `index.html`, konfiguracje Vite,
TypeScript i ESLint. Ustaw strict typing i skrypty `dev`, `build`, `lint`, `test`.
`build` ma obejmować także type-check, nie tylko bundlowanie.
Inicjalizacja jest w [development](../docs/development.md).
Kamera wymaga HTTPS: lokalny HTTPS dla serwera deweloperskiego (certyfikat
deweloperski lub tunel) od tygodnia 1.

- [src](src/README.md): kod aplikacji i funkcji.
- [public](public/README.md): zasoby pod stałymi URL.
- [tests](tests/README.md): setup i scenariusze testowe.

## Kolejność implementacji

1. Utwórz start aplikacji, router, QueryClient i wspólne style.
2. Użyj fixture'ów zgodnych z OpenAPI, pokazując wszystkie stany zapytania.
3. Dodaj paszport produktu: partia, historia, CID i link do explorera.
4. Dodaj logowanie portfelem (SIWE, EIP-4361) wydające Bearer token trzymany w pamięci
   (bez cookie/CSRF), panel operatora: wyszukanie/skanowanie partii i status polecenia.
5. Dodaj portfel operatora zmiany z rolą QA, sprawdzenie sieci i podpis `StageApproval`
   (EIP-712, `signTypedData`) dla tego samego raportu, wysyłany do backendu.
   Pobieraj kandydatów z chronionej kolejki WAITING_FOR_QA, nie z historii.
6. Podłącz prawdziwy backend, testuj błędy i odświeżanie po potwierdzeniu.
7. Dodaj sprawdzenie integralności raportu; PWA poza MVP.

## Zasady danych i UX

Widoczność akcji wynika z [macierzy uprawnień](../docs/access-control.md),
a przebieg kontroli z [quality-approval](../docs/quality-approval.md).
UI ma odróżniać approval operatora od ukończenia etapu, retry tego samego
żądania od nowego raportu oraz wynik `outcome=REJECTED` (`qaPassed=false`,
`StageRejected`) od `CONFIRMED` z przesunięciem partii. Zastępowanie raportu
(`409 STAGE_ALREADY_PENDING`) wg [changes-v2](../docs/changes-v2.md); nie dodawaj
przycisku zmieniającego automatycznie podpis/deadline po błędzie.

Stan serwera trzymaj w TanStack Query, nie w kilku niezależnych useEffect.
Modele API pochodzą ze schematu; `uint256` w JSON jest stringiem,
w wywołaniach Viem używaj `bigint`, nie JS `number`.
Renderuj osobno loading, brak danych, błąd, oczekiwanie i potwierdzenie.
`202` i hash transakcji nie oznaczają zakończenia etapu.

Publiczny paszport działa bez portfela. Połączenie portfela nie dowodzi
uprawnień; do API uwierzytelnia dopiero SIWE i wydany Bearer token (w pamięci, nie w localStorage).
Nie umieszczaj klucza relayera ani JWT Pinaty w `VITE_*`: wszystko trafia
do przeglądarki. Linki IPFS/explorera buduj z dozwolonej konfiguracji i CID/hash,
nie z dowolnego URL przekazanego w raporcie.

QR powinien zawierać publiczny URL paszportu i numer partii, nigdy podpis ani sekret.
Obsłuż brak kamery, odmowę uprawnień i zatrzymanie skanera po opuszczeniu widoku.
Operacje QA muszą pokazywać rejection portfela, revert i potwierdzenie.
Offline/PWA jest rozszerzeniem poza MVP; nie wolno udawać wykonanej operacji blockchainowej.

## Sprawdzenie

Po inicjalizacji: `npm ci`, `npm run lint`, `npm run test -- --run`,
`npm run build`. Planowany runner: Vitest i React Testing Library; E2E: Playwright.
Testuj widoki na wspólnych fixture'ach i pełny przepływ na lokalnym środowisku.
