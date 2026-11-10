# Tydzień 1 - plan Adama (kontrakty i łańcuch)

Wersja „po ludzku” do [week1-plan.md](week1-plan.md). Techniczne szczegóły i tabele
są tam; tutaj opisuję, **co właściwie robisz, po co i po czym poznasz, że działa**.

## Twoja rola w jednym zdaniu

Piszesz **smart kontrakt** - program działający na blockchainie, który jest
strażnikiem całej produkcji: nie pozwala przesunąć partii dalej, jeśli ktoś nie ma
uprawnień, podpis jest zły albo kontrola jakości nie przeszła. Reszta zespołu
(backend, symulator, w przyszłości frontend) tylko do niego „puka”.

## Słowniczek (żebyś nie musiał niczego zgadywać)

| Słowo | Co to znaczy po ludzku |
|---|---|
| Smart kontrakt | Program na blockchainie. Po wdrożeniu nikt nie może go po cichu zmienić, a jego decyzje są publiczne i niezmienne. |
| Foundry (`forge`) | Zestaw narzędzi do pisania kontraktów: kompiluje je i uruchamia testy. `forge test` to Twój najważniejszy przycisk. |
| Anvil | Lokalny, „udawany” blockchain na Twoim komputerze. Startuje w sekundę, nic nie kosztuje. Służy do testów. |
| Polygon Amoy | Prawdziwy, publiczny blockchain testowy. Działa jak produkcyjny, ale ze „sztucznymi” pieniędzmi. |
| ABI | „Instrukcja obsługi” kontraktu w pliku JSON: jakie ma funkcje i zdarzenia. Z niej korzystają backend i frontend. Generuje ją kompilator, nie piszesz jej ręcznie. |
| EIP-712 | Standard podpisywania danych tak, żeby podpis dotyczył konkretnej wiadomości, konkretnego kontraktu i konkretnej sieci. Nie da się go „przenieść” gdzie indziej. |
| OpenZeppelin | Sprawdzona biblioteka gotowych klocków (role, weryfikacja podpisów). Używasz jej zamiast pisać to samo od zera. |
| `evm_version` | Ustawienie mówiące kompilatorowi, jakie „instrukcje maszynowe” może użyć. Jeśli wybierzesz nowsze niż wspiera sieć, kontrakt się nie uruchomi. |
| Tip / priority fee | Dopłata dla walidatorów, żeby wzięli Twoją transakcję. Na Polygonie jest wymagany wysoki minimalny tip - sprawdzasz to w piątek. |
| Faucet | Strona, która za darmo daje testowe monety (POL) na opłaty w sieci testowej. |

## Co będzie istniało w piątek wieczorem

- Kontrakt `SupplyChain.sol`, który umie:
  - utworzyć partię z numerem tekstowym, np. `B-2026-0001`;
  - prowadzić partię przez etapy **0 -> 1 -> 2 -> 3** (przygotowanie, montaż, kontrola, pakowanie), bez przeskakiwania;
  - pamiętać **rejestr stacji** (kto jest dopuszczony, do kiedy, do których etapów);
  - przy etapie 2 (kontrola) wymagać **dwóch podpisów**: stacji i operatora zmiany;
  - przy wyniku negatywnym (`qaPassed = false`) nie przesuwać partii, tylko zapisać zdarzenie `StageRejected`.
- Testy (`forge test`) pokazujące, że wszystkie powyższe reguły działają, także gdy ktoś próbuje oszukać.
- Skrypt wdrożenia na Anvil i **jedno prawdziwe wdrożenie na Amoy** (smoke test).
- Plik ABI w `shared/abi/` i adres lokalnego wdrożenia w `shared/deployments/`.
- Notatka z wnioskami z Amoy (jaki tip gazu, jakie limity RPC, czy faucet działa).

## Dzień po dniu

### Poniedziałek - fundamenty

**Co robisz:**
- Instalujesz Foundry (`foundryup`) - na tej maszynie nie ma `forge` ani `anvil`.
- Tworzysz `contracts/foundry.toml`: wersja kompilatora 0.8.24, **jawnie ustawione `evm_version`**, przypięte biblioteki OpenZeppelin i forge-std.
- Uruchamiasz `forge build` i `forge test` na pustym teście.
- Bierzesz udział w spotkaniu startowym (2 h) i potwierdzasz z zespołem zmiany v2.

**Po co:** żeby wtorek zaczynał się od działającego narzędzia, a nie od walki z instalacją. Zapisanie dokładnych wersji sprawia, że u każdego wynik jest taki sam.

**Poznasz, że działa:** `forge build` kończy się bez błędów, `forge test` pokazuje zielone.

### Wtorek - szkielet kontraktu

**Co robisz:** tworzysz `contracts/src/SupplyChain.sol` z podstawami: role (administrator, relayer, operator QA), rejestr stacji (`registerStation`, `revokeStation`), `createBatch(string)` oraz dwa typy podpisów `StageCompletion` i `StageApproval` (dokładne pola i kolejność są w [protokole](../../protocol.md)).

**Po co:** to „kręgosłup” - na nim zbudujesz resztę w czwartek. Typy podpisów muszą mieć dokładnie te pola, które ustalono, bo Robert i Kuba generują i sprawdzają te same podpisy.

**Ważne ustalenie z Robertem (wtorek rano):** wektor testowy zawiera adres kontraktu w domenie podpisu. Umówcie się na **stały adres** (np. wdrażasz kontrakt w teście pod ustalonym adresem), żeby podpis z Pythona zgadzał się w Twoim teście.

**Poznasz, że działa:** kod się kompiluje, a typy podpisów mają dokładnie ten zapis, który jest w protokole.

### Środa - pierwszy test prawdziwego podpisu

**Co robisz:** bierzesz od Roberta plik `shared/fixtures/signature-vector.json` (opublikuje go we wtorek wieczorem) i piszesz test: „kontrakt, dostając ten raport i ten podpis, odzyskuje adres stacji, który jest w pliku”. To samo dla podpisu operatora.

**Po co:** to dowód, że Python (Robert), Java (Kuba, Mikołaj) i Solidity (Ty) liczą dokładnie to samo. Jeśli tu coś się rozjedzie, lepiej to odkryć w środę niż w tygodniu 5.

**Poznasz, że działa:** test wektora jest zielony. Jeśli nie - nie „naprawiaj po swojemu”, tylko dogadaj z Robertem, kto ma błąd. Wektor jest wspólny.

**Jeśli wektora jeszcze nie ma:** napisz własny test na znanym kluczu testowym i podmień na wspólny, gdy się pojawi.

### Czwartek - serce kontraktu: `completeStage`

**Co robisz:** funkcja `completeStage`, która sprawdza po kolei:
1. Wywołuje ją relayer (serwer Mikołaja), partia istnieje i nie jest zakończona.
2. Etap jest dokładnie tym oczekiwanym i termin (`deadline`) nie minął oraz nie jest dalej niż dozwolone okno (`maxDeadlineWindow`, roboczo 3600 s).
3. Podpis pochodzi od **aktywnej, niewygasłej stacji**, której wolno robić ten etap.
4. Jeśli stacja zgłosiła wynik negatywny - zapisz `StageRejected` i **zakończ bez zmiany stanu**.
5. Dla etapu 2: drugi podpis od operatora z rolą QA, **innego adresu niż stacja**, niewygasły.
6. Dopiero wtedy przesuń partię i wyemituj `StageCompleted`.

Zdarzenia mają mieć `batchId` oznaczony jako „indeksowany”, żeby można je było szybko przeszukać (będzie to potrzebne w paszporcie produktu).

**Po co:** to jest cała „ochrona” systemu. Ważne: kontrakt **nie ufa** backendowi - sam sprawdza wszystko.

**Testy, które piszesz (każdy próbuje oszukać system):** zła kolejność etapów; po terminie; stacja wygasła lub odwołana; stacja spoza zakresu etapów; brak drugiego podpisu przy kontroli; ten sam adres jako stacja i operator; podpis na inny raport; powtórzenie podpisu po ukończeniu etapu; wynik negatywny nie przesuwa partii.

**Poznasz, że działa:** `forge test` zielone, a każdy test „oszukańczy” kończy się odrzuceniem (revert).

### Piątek - wdrożenie, Amoy i ABI

**Co robisz:**
- Skrypt wdrożenia na Anvil (`contracts/script/DeploySupplyChain.s.sol`) i skrypt rejestracji stacji.
- **Smoke deploy na Amoy:** wdrażasz kontrakt na prawdziwej sieci testowej, rejestrujesz stację, tworzysz partię i wykonujesz jedno `completeStage`. Potrzebujesz testowych monet z faucetu i publicznego RPC.
- Eksport ABI: `forge inspect SupplyChain abi` do `shared/abi/`.
- Zapisujesz adres lokalnego wdrożenia w `shared/deployments/`.
- Przeglądasz moduł krypto w Javie, który napisze Kuba (drugie oczy).
- Bierzesz udział w piątkowym demo.

**Po co:** Amoy w tygodniu 1 to „test zapalnikowy”: odkrywa problemy (nieobsługiwana wersja EVM, minimalny tip gazu, limity RPC), które inaczej wyszłyby dopiero w tygodniu 10. ABI to rzecz, na którą czeka Mikołaj (od tygodnia 2).

**Notatka, którą zostawiasz zespołowi:** jaki minimalny tip działa, jakie są limity `eth_getLogs` (ile bloków naraz), czy faucet dał dość monet, jakie `evm_version` zadziałało.

**Poznasz, że działa:** jest transakcja `completeStage` na Amoy widoczna w eksploratorze, a `forge test` jest zielone.

## Od kogo czekasz i kto czeka na Ciebie

- **Czekasz na Roberta:** wektor podpisu (wtorek wieczorem).
- **Czekają na Ciebie:** Mikołaj - ABI i adres (piątek); cały zespół - wynik smoke z Amoy.
- **Prosisz o pomoc:** Mikołaja o przegląd kontraktu, jeśli coś jest niejasne w kontekście tego, jak backend będzie go wołał.

## Czego w tym tygodniu NIE robisz

- Nie piszesz indexera (to tydzień 4-5), weryfikatora certyfikatów (tydzień 3) ani CI (tydzień 2; szkielet da Robert).
- Nie dodajesz funkcji `approveStage` ani licznika nonce - celowo ich nie ma ([zmiany v2](../../changes-v2.md)).
- Nie zmieniasz podpisywanych pól po cichu. Zmiana wymaga ogłoszenia, bo dotyka Pythona, Javy i TS.

## Jeśli się zablokujesz

- Kontrakt nie działa na Amoy -> zapisz błąd i `evm_version`, spróbuj starszej wersji, napisz na kanale; to jest wynik, nie porażka.
- Test wektora się nie zgadza -> sprawdź kolejność pól i domenę (nazwa, wersja, chainId, adres) i porównaj z Robertem.
- Nie wiesz, czy reguła jest zdefiniowana -> sprawdź [changes-v2](../../changes-v2.md) (sekcja „Nowa powierzchnia kontraktu”).
