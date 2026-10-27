# Jak rozpocząć i rozwijać projekt

## Wymagania

- Git; JDK 21; Foundry (`forge`, `cast`, `anvil`).
- Wspierana wersja Node.js zgodna z wybraną wersją Vite, npm.
- Python 3.11+; Docker z Compose do PostgreSQL.
- Konto Pinaty i RPC Amoy dopiero do integracji zewnętrznej.

**Manifestów i aplikacji jeszcze nie ma.** Najpierw wykonaj inicjalizację poniżej.
Nie uruchamiaj generatora na całym repozytorium ani opcji nadpisującej README.
Generatory, które wymagają pustego folderu, uruchamiaj w tymczasowym katalogu;
przenieś wyłącznie potrzebne pliki po sprawdzeniu konfliktów i zachowaj instrukcje.

## Inicjalizacja modułów

### Kontrakty

W `contracts/` dodaj `foundry.toml`, przypnij kompilator i zależności
OpenZeppelin/forge-std. Foundry ma używać istniejących `src`, `test`, `script`.
Sposób instalacji bibliotek musi być odtwarzalny; zapisz dokładne wersje/revizje.
Nie kopiuj kodu OpenZeppelin ręcznie.

### Backend

Wygeneruj projekt Spring Initializr: Java 21, Maven, stabilna wersja Spring Boot
zgodna z wymaganiem 3.3+, pakiet `pl.pz.supplyblockchain`. Dodaj Spring Web,
Validation, Data JPA, PostgreSQL, Flyway i Actuator. Dobierz zgodne moduły Flyway
dla PostgreSQL, jeśli wymaga ich wybrana wersja. Dodaj Web3j osobno.
Przenieś `pom.xml`, Maven Wrapper oraz klasę startową do istniejącej struktury.
Nie włączaj WebFlux równolegle tylko po to, by wykonać wywołanie HTTP: wystarczy
Spring `RestClient` w wariancie MVC.

### Frontend

Wygeneruj szablon Vite `react-ts` z npm. Zachowaj `app/`, `features/`,
`components/` i `lib/`. Dodaj TanStack Query, Axios, router, Wagmi v2, Viem
i kompatybilny RainbowKit; Tailwind CSS i shadcn/ui konfiguruj według wersji
wybranych pakietów. QR: pakiet `html5-qrcode` (bez `@`) i `qrcode.react`.
Zapisz `package-lock.json`, ustaw TypeScript `strict`, skrypty lint/test/build.
PWA jest kolejnym etapem, nie warunkiem pierwszego działającego SPA.

### Symulator

Dodaj `pyproject.toml` z metadanymi, `requires-python = ">=3.11"`, układem `src`
oraz entry pointem CLI. Zależności: eth-account, Pydantic v2, httpx; generowanie
może początkowo używać standardowego `random`. Testy: pytest jako zależność dev.
Dodaj `__init__.py`, `__main__.py` i moduły opisane w README pakietu.
Wybierz i udokumentuj jeden sposób przypinania/odtwarzania zależności.

### Wspólne uruchomienie

Dodaj Compose dla PostgreSQL i lokalnego Anvil w `infrastructure/local/`.
API, frontend i symulator początkowo uruchamiaj w osobnych terminalach.
Dodaj `.env.example` z pustymi wartościami sekretów i opisem zmiennych.
Zewnętrzną Pinatę/RPC zastępuj kontrolowanym stubem w testach, nie
niewidocznym fallbackiem w kodzie produkcyjnym.

## Komendy po inicjalizacji

Uruchamiaj każdą grupę z katalogu jej modułu. Skrypty npm i entry point Python
trzeba najpierw zdefiniować zgodnie z powyższymi instrukcjami.

| Moduł | Sprawdzenie | Uruchomienie lokalne |
|---|---|---|
| contracts | `forge fmt --check`, `forge build`, `forge test` | `anvil` |
| backend | `./mvnw verify` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| frontend | `npm ci`, `npm run lint`, `npm run test -- --run`, `npm run build` | `npm run dev` |
| simulator | utwórz venv, `python -m pip install -e ".[dev]"`, `python -m pytest` | `python -m supply_chain_simulator --help` |

W Pythonie aktywuj venv zanim instalujesz zależności. Nie instaluj zależności
projektu globalnie. `npm ci` wymaga istniejącego i zgodnego lockfile.

## Kolejność pracy zespołu

Przed pisaniem reguł przeczytajcie pięć rozwinięć:
[produkcja](production-rules.md), [poprawki](corrections.md),
[dwa klucze](quality-approval.md), [certyfikaty](station-identity.md)
i [uprawnienia/logowanie](access-control.md).
Ogólna marszruta jest wybrana, ale brak produktu oznacza brak uzasadnionych
fizycznych progów pomiarowych. Recovery jest porównane, nie wybrane.

1. Przeczytajcie [mapę zgodności z PDF](source-alignment.md) i
   [plan 12 tygodni](plan.md). Zatwierdźcie protokół, etapy i wariant dwóch
   podpisów przy zamknięciu kontroli oraz format certyfikatu stacji.
2. Osoby 1 i 4 przygotowują schemat EIP-712 i wektory; osoba 2 sprawdza je w Javie.
3. Osoba 1 implementuje kontrakt, testuje, wdraża lokalnie i eksportuje ABI/adres.
4. Osoba 2 zaczyna od walidacji i trwałego `202`, potem dodaje IPFS i worker.
5. Osoba 4 zaczyna od jednego podpisanego zgłoszenia, potem buduje scenariusze.
6. Osoba 3 tworzy widoki na wspólnych fixture'ach; nie wymyśla innego formatu API.
7. Zespół uruchamia jeden pełny przepływ, restart workera, błędny podpis i QA fail.
8. Po lokalnym odbiorze konfiguruje Amoy i prawdziwą Pinatę.

Backend i kontrakty mają osiągnąć podstawową integrację w tygodniach 4-5.
Osoba 4 koordynuje również testy systemowe i dokumentację, nie tylko symulator.
Nie wdrażamy fizycznej hali ani produkcyjnego PKI; to programowy prototyp.

## Zasady pisania kodu

- Małe klasy/moduły z nazwą od odpowiedzialności; bez pustych abstrakcji na zapas.
- Java: `PascalCase` klas, `camelCase` metod; DTO jako records, constructor injection.
- Python: `snake_case`, adnotacje typów własnego kodu, rozdziel modele, podpis i HTTP.
- TypeScript: `PascalCase` komponentów, `use...` hooków, brak `any` zamiast modelu.
- Solidity: jawna widoczność, custom errors, zdarzenia, sprawdzenie ról i podpisu.
- Logi zawierają correlation/command/transaction ID, nie klucze i całe sekrety.
- Błędy mają jawne statusy; brak usługi nie zwraca pustej historii jako sukcesu.
- Komentarze wyjaśniają trudne decyzje, nie powtarzają instrukcji z kodu.

## Zmiany integracyjne i przegląd

W jednym PR aktualizuj źródła, bezpośrednią dokumentację i testy. Zmiana ABI
wymaga eksportu oraz ponownego generowania klienta Java. Zmiana DTO wymaga
OpenAPI, klienta TS, modeli Python i fixture'ów. Nie edytuj wygenerowanych
wrapperów ręcznie. Przed połączeniem zmian sprawdź kryteria z [testing](testing.md).
