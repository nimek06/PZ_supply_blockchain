# Frontend - osoba 3

**Języki:** TypeScript (`.ts`, `.tsx`), CSS, HTML.
**Narzędzia:** React 19, Vite, npm, TanStack Query, Axios, Wagmi v3,
Viem, Tailwind CSS, shadcn/ui (dodawany w miarę potrzeby),
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

## Uruchomienie

Wymagany Node 22.12+ (CI używa wersji z `.nvmrc`).

```bash
cd frontend
npm ci
npm run dev          # http://localhost:5173, tylko ten komputer
npm run dev:https    # HTTPS w sieci lokalnej, dla telefonu i kamery
npm run lint
npm run test -- --run
npm run build        # najpierw tsc -b, potem bundle
npm run gen:api      # odświeża src/lib/api/schema.gen.ts z OpenAPI
```

Serwer Vite przekazuje trzy ścieżki dalej, więc przeglądarka (także na telefonie) zna tylko adres
aplikacji, a Anvil, Kubo i backend dalej słuchają wyłącznie na komputerze:

| Ścieżka w aplikacji | Dokąd idzie | Zmienna (`.env.example`) |
|---|---|---|
| `/api` | backend, `http://localhost:8080` | `BACKEND_URL` |
| `/rpc` | Anvil, `http://127.0.0.1:8545` | `ANVIL_URL` |
| `/ipfs` | bramka Kubo, `http://127.0.0.1:8081` | `IPFS_GATEWAY_URL` |

- Nie potrzeba CORS ani wystawiania RPC Anvila czy API Kubo w sieci.
- Bez backendu widoki działają na danych demo: przełącznik na stronie operatora albo `?demo=1`
  w adresie. Dane demo mają zawsze etykietę „dane demo” i nie da się na nich tworzyć partii.
- `schema.gen.ts` jest generowany, nie poprawiaj go ręcznie. CI sprawdza, czy jest aktualny.

## Konta testowe

Tylko publiczne konta deweloperskie Anvila, tylko na lokalnym łańcuchu. Nigdy na Amoy.

| Konto | Adres | Rola (deploy z `infrastructure/local`) |
|---|---|---|
| Anvil #0 | `0xf39F…2266` | admin, relayer |
| Anvil #1 | `0x7099…79C8` | stacja z fixture'ów |
| Anvil #2 | `0x3C44…93BC` | operator QA |
| Anvil #3 | `0x90F7…b906` | operator produkcji, tworzy partie |

W MetaMasku: dodaj sieć (RPC `http://localhost:5173/rpc` albo `http://127.0.0.1:8545`, chain ID
`31337`, waluta `ETH`) i zaimportuj klucz konta #3 z dokumentacji Anvila. Portfel tylko podpisuje
(logowanie, w tygodniu 6 approval QA), transakcje wysyła relayer w backendzie.

## Telefon, HTTPS i QR

- `npm run dev:https` używa certyfikatu deweloperskiego (`@vitejs/plugin-basic-ssl`).
  Przeglądarka pokaże ostrzeżenie o certyfikacie, trzeba je raz zaakceptować.
- Vite wypisze adres w sieci lokalnej, np. `https://192.168.0.9:5173`. Telefon musi być w tej
  samej sieci Wi-Fi. `localhost` na telefonie oznacza sam telefon.
- Kod QR partii zawiera tylko publiczny adres paszportu, np. `https://192.168.0.9:5173/passport/B-2026-0001`.
  Skaner przyjmuje sam numer partii albo adres paszportu tej samej aplikacji, inne adresy odrzuca.
- Paszport i skaner działają bez portfela. Logowanie z telefonu wymaga portfela z przeglądarką
  (np. MetaMask Mobile) i sieci Anvil z RPC `https://<adres-komputera>:5173/rpc`. Czy MetaMask
  Mobile przyjmie certyfikat deweloperski, trzeba jeszcze sprawdzić.
- Wiadomość SIWE buduje backend z `AUTH_DOMAIN` i `AUTH_URI` (domyślnie `localhost`
  i `http://localhost:5173`). Przy HTTPS albo wejściu z telefonu ustaw je na adres, pod którym
  otwierasz aplikację, inaczej portfel może ostrzec, że domena się nie zgadza.
- To jest lokalna demonstracja. Aplikacja nie jest wdrożona na Amoy, a pełny paszport
  (historia, raport z IPFS, sprawdzenie hasha) to tydzień 7.

Stan sprawdzenia (W3-07, W4-09):

- skaner w Chrome na komputerze z kamerą testową: kod QR z aplikacji otwiera paszport, plik `.wasm`
  idzie z naszego serwera, żadnych zapytań poza `localhost`
- odmowa kamery, brak kamery i brak HTTPS mają osobne komunikaty (testy z podstawioną kamerą)
- telefon: jeszcze niesprawdzone

## Wybór bibliotek

- Skaner QR: `@yudiel/react-qr-scanner` (utrzymywany, sam zwalnia kamerę). Silnik `zxing-wasm`
  domyślnie pobiera plik `.wasm` z CDN, więc podpinamy kopię z builda (`src/components/qr/zxing.ts`).
  Skaner ładuje się dopiero po kliknięciu. Ostateczny wybór po teście na telefonie.
- Wagmi v3 bez RainbowKit. RainbowKit wymaga Wagmi v2, a Wagmi v2 ciągnie cały stos
  WalletConnect (ok. 660 MB w `node_modules` i znane podatności w zależnościach). Do lokalnego
  Anvila wystarczy portfel w przeglądarce (konektor `injected`, np. MetaMask), bez `projectId`.
- TypeScript 5.9, bo `openapi-typescript` 7 nie wspiera jeszcze 6.x.
- Wersje są przypięte dokładnie (`--save-exact`) i mają co najmniej 2 tygodnie od wydania.
- `npm audit` zgłasza tylko `minimatch` w narzędziach do lintowania (moderate, dev).
  Kod aplikacji (`npm audit --omit=dev`) nie ma zgłoszeń.
