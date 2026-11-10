# Zgodność z opisem projektu

**Źródło nadrzędne:** [Opis_Projectu (3).pdf](../Opis_Projectu%20%283%29.pdf),
3 strony, odczytane 2026-10-26. Dokument źródłowy pozostaje bez zmian.
Przy sprzeczności z wcześniejszym README pierwszeństwo ma PDF.

## Zakres i interpretacja

Wprowadzenie PDF mówi szeroko o łańcuchu dostaw od półproduktów do dystrybucji.
Numerowany zakres zawęża realizację do prototypu śledzenia partii komponentów
na **modelowej linii montażowej**. To zakres MVP; pełna logistyka dostaw
i dystrybucji jest kontekstem/rozszerzeniem, nie dodatkowym systemem do zbudowania.
Nie przebudowujemy hali, nie kupujemy PLC i nie wdrażamy nowej architektury
fizycznej. Zmieniamy sposób rejestrowania, przechowywania i weryfikacji danych.

## Mapa wymagań źródłowych

| Fragment PDF | Wymaganie dla projektu | Dokument wykonawczy / wymaganie |
|---|---|---|
| s. 1, zakres 1 | zdecentralizowany prototyp rejestru partii modelowej linii | [spec](spec.md), REQ-001..002 |
| s. 1, zakres 2 i podcel determinizmu | maszyna stanów, RBAC, pozytywne QA przed przejściem | [protocol](protocol.md), REQ-004..005 |
| s. 1, zakres 3 | symulator czujników podpisujący pakiet lokalnym kluczem | [simulator](../simulator/README.md), REQ-003..004, REQ-011 |
| s. 1, zakres 4 i s. 2 zero-bloat | pełny JSON w IPFS, identyfikator partii i odniesienie kryptograficzne on-chain; tylko niezbędny stan reguł | [data-model](data-model.md), REQ-007 |
| s. 1, zakres 5 | responsywny widok stacji, ręczne ID lub QR, publiczna oś czasu | [frontend](../frontend/README.md), REQ-009..010 |
| s. 2, atestacja i tożsamość stacji | własna para kluczy i certyfikat każdej emulowanej stacji | [tożsamość stacji](station-identity.md), REQ-013 |
| s. 2, cyfrowy paszport | audytowalna ścieżka komponentu po numerze partii | [flows](flows.md), REQ-008, REQ-010 |
| s. 3, Garbage In, Garbage Out | współautoryzacja zamknięcia kontroli: klucz maszyny/czujnika + klucz operatora zmiany | [flows](flows.md), REQ-005, REQ-014 |
| s. 3, 12 tygodni i EVM | backend i kontrakty w pierwszych 4-5 tygodniach, ograniczenie nadmiernej architektury | [plan](plan.md) |
| s. 3, cztery osoby | osoba 4 odpowiada także za testy integracyjne i dokumentację | [plan](plan.md), [testing](testing.md) |

## Co zachowano

Solidity/Polygon, Foundry/Anvil, IPFS/Pinata, symulator Python i REST są
zgodne z PDF. Java/Spring, React/TypeScript, PostgreSQL, JCS i EIP-712
pozostają decyzjami wykonawczymi z wcześniejszego README: PDF ich nie zabrania,
ale nie należy przedstawiać ich jako wymagań wprost z PDF.
Jedna instancja backendu/relayera nie zmienia faktu, że rejestr jest on-chain,
a raporty w IPFS. Nie oznacza jednak decentralizacji każdej części systemu.

## Co skorygowano

1. Ręczne zatwierdzenie kontroli nie jest opcjonalnym panelem niezależnego
   inspektora: ma być podpisem **operatora zmiany**, obok podpisu stacji.
   Nazwa katalogu `qa` i rola `QA_INSPECTOR_ROLE` mogą zostać; oznaczają operatora
   uprawnionego do zamknięcia kontroli. Nie wymagamy trzeciej osoby.
2. „Multi-sig” oznacza w tym MVP dwa niezależne dowody autoryzacji tego samego
   raportu: EIP-712 `StageCompletion` stacji i EIP-712 `StageApproval` operatora,
   weryfikowane razem w jednym wywołaniu kontraktu (v2; bez transakcji approval
   operatora). Nie jest to portfel Safe ani dwa podpisy pod tą samą transakcją.
   Samo wymaganie dwóch kluczy wynika z PDF i nie jest do dowolnego usunięcia.
3. Dodano obsługę certyfikowanej tożsamości symulowanych stacji, bez udawania
   sprzętowego TPM/TEE. Podpis potwierdza źródło, nie fizyczną prawdziwość pomiaru.
   Egzekucję (ważność, zakres etapów, odwołanie) przejął rejestr stacji on-chain.
4. On-chain nie kopiujemy JSON, certyfikatów, podpisów ani nieograniczonej historii.
   Podpisy mogą być argumentem transakcji do weryfikacji, nie muszą być storage.
5. Panel operatora obejmuje zmianę statusu przez właściwe zgłoszenie/approval,
   nie bezpośrednią edycję projekcji i nie tylko bierny podgląd.
6. Wstępny plan obejmuje 12 tygodni, MVP lokalne i końcową demonstrację testnet;
   smoke deploy na Amoy jest już w tygodniu 1.
7. Zmiany v2 z 2026-10-27 (brak nonce, off-chain approval, rejestr stacji,
   token Bearer, `StageRejected`) opisuje [changes-v2](changes-v2.md); w razie
   sprzeczności ten dokument ma pierwszeństwo przed starszym tekstem.

## Czego PDF nie rozstrzyga

Konkretnych etapów i limitów pomiarowych, formatu certyfikatu, TTL podpisów,
finalności, uwierzytelnienia API i dokładnego wariantu dwóch podpisów.
Wybory wykonawcze są jawne w [decyzjach](decisions.md). „Darmowy testnet” nie gwarantuje
bezterminowej darmowej Pinaty/RPC ani dostępności faucet; przed demonstracją
sprawdź limity kont i testowe środki na gaz. DPP nie oznacza certyfikacji
zgodności z regulacjami UE ani gotowego liczenia śladu węglowego.
