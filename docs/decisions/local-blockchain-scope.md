# Zakres blockchainu: wariant 1 — lokalny system i jednorazowe demo Amoy

**Status:** wybrano wariant 1 dnia **2026-11-11**. Decyzja zastępuje wcześniejszy
obowiązek pełnej integracji aplikacji z Amoy i Pinatą. Rejestr:
[DEC-17](decisions.md); wykonanie: [plan projektu](../planning/plan.md).

## Obowiązujący zakres

- **Anvil (chainId 31337):** cały system — kontrakt, backend, relayer, indexer,
  symulator, frontend, portfel, testy i podstawowa demonstracja.
- **Kubo:** jedyny wymagany magazyn IPFS, także podczas demonstracji. Nie budujemy
  ani nie utrzymujemy adaptera Pinata.
- **Polygon Amoy (chainId 80002):** dodatkowe, jednorazowe demo kontraktu skryptem
  przed prezentacją (T029, tydzień 10): deploy, role, rejestracja stacji,
  utworzenie partii i etapy 0..3, w tym kontrola z dwoma niezależnymi podpisami.
  Zachowujemy adres kontraktu, hashe transakcji i odnośniki do eksploratora.
  Istniejący smoke test jednego etapu to punkt startowy, nie pełne demo.
- Backend, frontend, relayer i indexer **nie muszą działać na Amoy**. Nie ma
  synchronizacji między sieciami, mostu ani kotwiczenia lokalnych wyników.
  Demo Amoy ma osobną partię i podpisy dla właściwej domeny EIP-712; podpisów
  z Anvila nie można przenieść na Amoy.
- RPC URL, chainId i adres kontraktu pozostają konfiguracją. Nie oznacza to
  zobowiązania do obsługi drugiego środowiska przez całą aplikację.
- Indexer lokalny używa `latest` i jednego potwierdzenia (blok zawierający
  transakcję liczy się jako pierwsze). Zachowujemy wykrywanie zmiany hash bloku,
  okno ponownego skanu i testy reorgu na Anvilu. Nadal wymagane są trwałe próby
  transakcji, bezpieczne ponowienie i obsługa restartu.
- SIWE i podpisy QA przez portfel pozostają w zakresie. Anvil udostępnia
  domyślnie 10 kont po 10 000 fikcyjnych ETH; niczego nie kupujemy.
  Publicznych kluczy deweloperskich Anvila nie używamy na Amoy.
- Demo Amoy korzysta z osobnych kont testowych i testowego POL z faucetu.
  Awaria RPC lub faucetu nie blokuje lokalnego MVP; brak dema zapisujemy jawnie,
  a nie przedstawiamy jako wykonany test. Nie utrzymujemy go w CI.

**Granica prezentacji:** lokalny paszport jest publicznym odczytem bez logowania
w udostępnionym środowisku, nie niezależnym publicznym rejestrem Polygon.
Kod QR wskazuje adres aplikacji osiągalny z urządzenia, a nie samo RPC.
Demo Amoy potwierdza działanie kontraktu na publicznym EVM, nie pełną integrację
aplikacji ani publiczną dostępność raportów z lokalnego Kubo.

## Wcześniejsza analiza i rozważane alternatywy (2026-11-10)

Poniższy materiał zachowuje wcześniejszą dyskusję, w tym dosłowny opis czterech
wariantów. Propozycje i zalecenia poniżej nie zmieniają wybranego zakresu powyżej.
Wartość 10–15% była niezweryfikowanym szacunkiem, nie pomiarem oszczędności pracy.

## Pytanie

Czy rezygnujemy z wdrożenia na publiczny testnet Polygon Amoy i uruchamiamy system
wyłącznie na lokalnym łańcuchu (Anvil, chainId 31337)? Jak to wpływa na nakład pracy?

## Krótka odpowiedź

Praca **wyraźnie się upraszcza**. Szacujemy około 10–15% mniej pracy, głównie w relayerze,
indexerze, konfiguracji i zadaniach wdrożeniowych. Kosztem jest utrata
**publicznej weryfikowalności**, czyli głównego argumentu za użyciem blockchainu.

## Co się upraszcza

| Obszar | Na Amoy | Tylko lokalnie |
|---|---|---|
| Środki | faucet, saldo POL relayera i deployera | konta Anvila z gotowym saldem |
| RPC | limity publicznego RPC, niestabilne węzły, paginacja `eth_getLogs` | jeden lokalny węzeł bez limitów |
| Relayer | minimalny priority fee (DEC-11, 30 gwei), zablokowane lub zastąpione transakcje | przewidywalny gaz, natychmiastowe bloki |
| Indexer | `finalized`, obsługa reorgów, pomiary z DEC-07 | `latest` + 1 potwierdzenie |
| Testy i CI | i tak bez Amoy, ale demo zależy od sieci | deterministyczne adresy, `anvil --state`, powtarzalne demo |
| Sekrety | klucz RPC, klucz deployera na testnecie, token Pinaty | brak sekretów sieciowych |
| IPFS | Kubo lokalnie + Pinata publicznie | wystarczy lokalny Kubo |
| Zadania | T029 (Amoy, Pinata, publiczny frontend) w tygodniach 5 i 10 | zadanie odpada lub staje się opcjonalne |

## Co tracimy lub co się utrudnia

- **Wartość merytoryczna.** Lokalny łańcuch kontroluje jedna osoba, więc niezmienność
  i niezależna weryfikacja paszportu są tylko zasymulowane. Na obronie można usłyszeć
  pytanie, czym to się różni od bazy danych z podpisami.
- **Brak publicznej weryfikacji.** Nie ma Polygonscan, a kod QR paszportu działa tylko
  w sieci, w której działa nasz Anvil.
- **Portfel w przeglądarce i na telefonie.** MetaMask trzeba ręcznie skonfigurować
  na `http://localhost:8545` / chainId 31337, a telefon musi mieć dostęp do naszego RPC.
- **Mniej realistyczne pomiary.** Gaz liczy się tak samo, ale opóźnienia, koszty
  w POL i zachowanie pod obciążeniem nie odpowiadają prawdziwej sieci.
- **Praca z tygodnia 1 traci znaczenie.** Smoke deploy na Amoy
  ([notatka](../planning/weekly/week1/notes/adam-amoy-notes.md)) i
  `contracts/script/amoy-smoke.sh` przestają być wymagane.

## Wpływ na osoby

| Osoba | Wpływ |
|---|---|
| Adam (kontrakty i łańcuch) | mniej zadań w tygodniach 5 i 10; smoke deploy Amoy staje się opcjonalny |
| Mikołaj (backend) | prostszy relayer i indexer; reszta bez zmian, bo testy i tak działają na Anvilu |
| Kuba (frontend) | łatwiej: jedna sieć i stały chainId; trzeba opisać konfigurację MetaMask |
| Robert (symulator i integracja) | praktycznie bez zmian, bo symulator już celuje w Anvil |

## Dokumenty powiązane z decyzją

- [decisions.md](decisions.md): nowa decyzja o zakresie; korekta DEC-07 (finalność)
  i DEC-11 (priority fee relayera).
- [plan.md](../planning/plan.md) i [team-split.md](../planning/team-split.md): T029 i
  kroki Amoy w tygodniach 5 i 10.
- [Stack_Tech.md](../../Stack_Tech.md), [README.md](../../README.md) i
  [contracts/script/README.md](../../contracts/script/README.md): starsze wzmianki
  poza `docs/` nie zostały zmienione w tej aktualizacji; dla zakresu obowiązuje DEC-17.
- Notatki Adama ([gas](../planning/weekly/week2/notes/adam-gas-report.md),
  [indexer](../planning/weekly/week2/notes/adam-indexer-notes.md)): oznaczyć sekcje Amoy
  jako materiał archiwalny.

## Rekomendacja: wariant pośredni

1. **Anvil jako jedyny wymagany cel.** CI, testy E2E i demo działają tylko lokalnie.
2. **Kod niezależny od sieci.** RPC URL, chainId, adres kontraktu, liczba potwierdzeń
   indexera i minimalny priority fee pochodzą z konfiguracji, a nie z kodu. Backend
   już w dużej mierze tak działa.
3. **Amoy jako opcjonalny bonus.** Zostaje jeden skrypt deployu i zrzut ekranu
   z Polygonscan do prezentacji. Nie utrzymujemy go w CI ani w testach.

W ten sposób zyskujemy prawie całe uproszczenie, a na obronie nadal możemy pokazać,
że kontrakt działa na prawdziwej sieci.

## Warianty hybrydowe: lokalnie + ograniczone użycie Polygon Amoy

Możecie wybrać kilka wariantów — od symbolicznego użycia Polygon Amoy po faktyczną integrację:

| Wariant | Lokalnie | Na Polygon Amoy | Nakład |
|---|---|---|---|
| **1. Jednorazowe demo** | Cały rozwój i działająca aplikacja | Deploy kontraktu i jeden pełny przebieg partii przed prezentacją | Mały |
| **2. Opcjonalny tryb Amoy** | Domyślne środowisko, testy i CI | Ten sam system uruchamiany na żądanie z inną konfiguracją | Średni |
| **3. Kotwiczenie wyników** | Wszystkie etapy produkcji, podpisy i raporty | Tylko hash końcowego paszportu lub podsumowania partii | Mały–średni, ale wymaga dodatkowej logiki |
| **4. Wybrane partie demonstracyjne** | Większość partii i wszystkie automatyczne testy | Kilka partii przechodzi pełny proces przez aplikację | Średni |

### Co faktycznie daje każdy wariant?

**1. Jednorazowe demo**  
Pokazujecie, że kontrakt działa również na publicznym EVM. Nie musicie utrzymywać całej aplikacji pod Amoy — przebieg można wykonać skryptem. **Nie dowodzi to jednak integracji całego systemu z Polygonem.**

**2. Opcjonalny tryb Amoy**  
Najbardziej naturalny dla obecnej architektury. Zmieniacie RPC, chainId, adresy kontraktów i konta; frontend przełącza sieć. Dane backendu i stan indexera muszą być rozdzielone między środowiskami. **Nie wymaga synchronizacji Anvil ↔ Amoy** — to dwa niezależne uruchomienia.

**3. Kotwiczenie wyników**  
Po zakończeniu partii publikujecie na Amoy hash jej paszportu. Później można sprawdzić, czy dokument się zmienił. To **nie potwierdza prawdziwości produkcji ani poszczególnych etapów** — tylko zgodność dokumentu z publicznie zapisanym hashem. Nie trzeba mostu między sieciami, ale trzeba dopisać publikowanie i weryfikację tego hasha.

**4. Wybrane partie demonstracyjne**  
Pełna integracja z Amoy, ale używana sporadycznie. Ogranicza liczbę transakcji i obsługę środowiska, **nie usuwa jednak pracy nad relayerem, indexerem i portfelem dla Amoy**.

**Dla was polecałbym wariant 1**, jeśli priorytetem jest ograniczenie pracy. **Wariant 2**, jeśli chcecie mocniejszą prezentację bez zmiany architektury. Kotwiczenie jest sensowne, ale na tym etapie może oznaczać więcej nowych funkcji niż jednorazowe demo obecnego kontraktu.
