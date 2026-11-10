# Analiza zakresu: tylko lokalny blockchain (bez Polygon Amoy)

**Status:** propozycja do decyzji zespołu (2026-11-10). Do czasu zatwierdzenia
obowiązuje dotychczasowy plan z [decisions.md](decisions.md) i [plan.md](../planning/plan.md).

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

## Dokumenty do poprawy po decyzji

- [decisions.md](decisions.md): nowa decyzja o zakresie; korekta DEC-07 (finalność)
  i DEC-11 (priority fee relayera).
- [plan.md](../planning/plan.md) i [team-split.md](../planning/team-split.md): T029 i
  kroki Amoy w tygodniach 5 i 10.
- [Stack_Tech.md](../../Stack_Tech.md), [README.md](../../README.md) i
  [contracts/script/README.md](../../contracts/script/README.md): wzmianki o Amoy i Pinacie.
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
