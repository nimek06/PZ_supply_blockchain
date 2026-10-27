# Frontend - osoba 3

**Języki:** TypeScript (`.ts`, `.tsx`), CSS, HTML.
**Narzędzia:** React 18/19, Vite, npm, TanStack Query, Axios, Wagmi v2,
Viem, RainbowKit, Tailwind CSS, shadcn/ui, html5-qrcode, qrcode.react.
Wybierz kompatybilne wersje, nie mieszaj instrukcji różnych wersji Tailwind.

Wymagany [zakres PDF](../docs/source-alignment.md): responsywny widok stacji,
ręczne wpisanie ID lub QR, zmiana statusu i publiczna oś czasu.
Widok QA obsługuje operatora zmiany: drugi niezależny klucz do zamknięcia
kontroli obok podpisu stacji, nie opcjonalnego trzeciego inspektora.

## Co utworzyć

W korzeniu: `package.json`, `package-lock.json`, `index.html`, konfiguracje Vite,
TypeScript i ESLint. Ustaw strict typing i skrypty `dev`, `build`, `lint`, `test`.
`build` ma obejmować także type-check, nie tylko bundlowanie.
Inicjalizacja jest w [development](../docs/development.md).

- [src](src/README.md): kod aplikacji i funkcji.
- [public](public/README.md): zasoby pod stałymi URL.
- [tests](tests/README.md): setup i scenariusze testowe.

## Kolejność implementacji

1. Utwórz start aplikacji, router, QueryClient i wspólne style.
2. Użyj fixture'ów zgodnych z OpenAPI, pokazując wszystkie stany zapytania.
3. Dodaj paszport produktu: partia, historia, CID i link do explorera.
4. Dodaj panel operatora: wyszukanie/skanowanie partii i status polecenia.
5. Dodaj portfel operatora zmiany z rolą QA, sprawdzenie sieci i podpisane
   zatwierdzenie tego samego raportu. Pobieraj kandydatów z chronionej
   kolejki WAITING_FOR_QA, nie z już ukończonej historii.
6. Podłącz prawdziwy backend, testuj błędy i odświeżanie po potwierdzeniu.
7. Dodaj sprawdzenie integralności raportu i ewentualnie funkcje PWA.

## Zasady danych i UX

Widoczność akcji wynika z [macierzy uprawnień](../docs/access-control.md),
a przebieg kontroli z [quality-approval](../docs/quality-approval.md).
UI ma odróżniać approval operatora od ukończenia etapu oraz retry tego samego
żądania od nowego raportu. Dopóki [DEC-09](../docs/corrections.md) jest otwarta,
nie dodawaj przycisku zmieniającego automatycznie podpis/deadline po błędzie.

Stan serwera trzymaj w TanStack Query, nie w kilku niezależnych useEffect.
Modele API pochodzą ze schematu; `uint256` w JSON jest stringiem,
w wywołaniach Viem używaj `bigint`, nie JS `number`.
Renderuj osobno loading, brak danych, błąd, oczekiwanie i potwierdzenie.
`202` i hash transakcji nie oznaczają zakończenia etapu.

Publiczny paszport działa bez portfela. Połączenie portfela nie dowodzi
uprawnień i nie uwierzytelnia automatycznie w API.
Nie umieszczaj klucza relayera ani JWT Pinaty w `VITE_*`: wszystko trafia
do przeglądarki. Linki IPFS/explorera buduj z dozwolonej konfiguracji i CID/hash,
nie z dowolnego URL przekazanego w raporcie.

QR powinien zawierać publiczny URL paszportu i batchId, nigdy podpis ani sekret.
Obsłuż brak kamery, odmowę uprawnień i zatrzymanie skanera po opuszczeniu widoku.
Operacje QA muszą pokazywać rejection portfela, revert i potwierdzenie.
Tryb offline PWA może pokazać cache z oznaczeniem nieaktualności; nie wolno
udawać wykonanej operacji blockchainowej.

## Sprawdzenie

Po inicjalizacji: `npm ci`, `npm run lint`, `npm run test -- --run`,
`npm run build`. Planowany runner: Vitest i React Testing Library; E2E: Playwright.
Testuj widoki na wspólnych fixture'ach i pełny przepływ na lokalnym środowisku.
