# Kontrakty - osoba 1

**Język:** Solidity 0.8.24+. **Narzędzia:** Foundry, Anvil, OpenZeppelin Contracts.
Dokładną wersję kompilatora i bibliotek przypnij przy inicjalizacji.
Nadrzędny [opis PDF](../docs/source-alignment.md) wymaga maszyny stanów, RBAC,
minimalnego rejestru on-chain i dwóch kluczy do zamknięcia kontroli:
stacji oraz operatora zmiany. Nazwa roli QA nie oznacza trzeciego podpisu.

## Zakres i katalogi

- [src](src/README.md): stan partii, role, weryfikacja podpisów i QA.
- [test](test/README.md): testy jednostkowe, negatywne i fuzz.
- [script](script/README.md): wdrożenie i konfiguracja środowiska.

Planowane pliki w korzeniu: `foundry.toml` (z jawnie ustawionym `evm_version`) i deklaracje zależności.
Zmiany projektowe v2 (bez nonce, approval podpisem poza łańcuchem, rejestr stacji,
`StageRejected`): [changes-v2](../docs/changes-v2.md) - wiążące.
`out/`, `cache/` i `broadcast/` są wynikami narzędzi, nie kodem do wersjonowania.
Jeżeli instalator zależności tworzy `lib/`, nie traktuj go jako własnego modułu.

## Kolejność pisania

Wejścia do projektu reguł: [marszruta](../docs/production-rules.md),
[dokładna polityka dwóch kluczy](../docs/quality-approval.md)
i [role](../docs/access-control.md). Recovery: DEC-09 zamknięta (V2-02) - bez
unieważniania nonce i bez zmian ABI; maksymalne okno `deadline` egzekwuje kontrakt
(`setMaxDeadlineWindow`).

1. Uzgodnij [protokół](../docs/protocol.md), enum/listę etapów oraz ręczne QA.
2. Napisz testy tworzenia partii i niedozwolonej zmiany etapu.
3. Dodaj `AccessControl`: `DEFAULT_ADMIN_ROLE`, `RELAYER_ROLE`, `QA_INSPECTOR_ROLE`
   (operatorzy zmiany), `OPERATOR_ROLE` (tylko API); nie utożsamiaj relayera
   z żadnym z dwóch signerów. `STATION_ROLE` nie istnieje.
4. Dodaj rejestr stacji (`registerStation`/`revokeStation`, `expiresAt`, `stageMask`).
5. Dodaj OpenZeppelin `EIP712` i `ECDSA`, domenę; typy `StageCompletion` (bez nonce)
   i `StageApproval`. Zweryfikuj wspólny wektor i wszystkie mutacje podpisanej treści.
6. Dodaj przejścia, `StageRejected` dla `qaPassed=false`, custom errors i pełne
   zdarzenia do indeksowania.
7. Wdróż na Anvil, eksportuj ABI i publiczne dane wdrożenia.
8. Smoke deploy na Amoy już w tygodniu 1, potem po testach integracji
   (tydzień 5) i weryfikacja kodu kontraktu.

## Zasady

`msg.sender` przy zgłoszeniu etapu jest relayerem; adres stacji odzyskaj z podpisu
i sprawdź w rejestrze: aktywna, niewygasła, `stageMask` zawiera etap.
Podpis obejmuje `reportHash`, nie nieznany jeszcze CID. Brak nonce - replay chronią:
jedno ukończenie na `(batchId, stageId)`, `deadline` (z górnym limitem okna)
i domena EIP-712. `qaPassed=false` nie przesuwa partii: kontrakt emituje
`StageRejected` i zapisuje skrót autoryzacji, by nie powielać zdarzenia.
Kontrakt nie pobiera IPFS: zapisuje CID i hash do późniejszej weryfikacji.
Używaj odzyskiwania podpisów odpornego na malleability z biblioteki zamiast
ręcznego `ecrecover`.

Nie przechowuj całych raportów w storage. Ogranicz rozmiar CID i danych
wejściowych. Rozważ historię opartą na zdarzeniach zamiast nieograniczonej
tablicy odczytywanej w jednym wywołaniu. To ma być prosty kontrakt, bez proxy
i systemu aktualizacji, dopóki nie pojawi się wymaganie.
Pełne certyfikaty i kopie podpisów również nie należą do storage.
Approval jest podpisem EIP-712 `StageApproval` poza łańcuchem (bez gazu operatora);
nie ma `approveStage` ani storage approval. `completeStage` weryfikuje oba podpisy
atomowo: `approvalDeadline`, aktualna `QA_INSPECTOR_ROLE` operatora i adres różny
od adresu stacji. Szczegóły: [protokół](../docs/protocol.md);
nie zakładaj automatycznie portfela Safe ani kosztownego wdrożenia multisig.
`createBatch(string batchNumber)` liczy `batchId = keccak256(utf8(batchNumber))`.

## Współpraca i sprawdzenie

Po zmianie publicznych funkcji/events eksportuj ABI (`forge inspect <Contract> abi`,
bytecode: `forge inspect <Contract> bytecode`) do
[shared/abi](../shared/abi/README.md), a adresy do
[deployments](../shared/deployments/README.md). Backend regeneruje wrapper.
Nigdy nie przekazuj klucza administratora plikiem w repozytorium.

Po inicjalizacji: `forge fmt --check`, `forge build`, `forge test`.
Kontrakt jest gotowy do integracji, gdy testuje role, rejestr stacji, kolejność, QA
(w tym `StageRejected`), replay (deadline, jedno ukończenie) i wspólny podpis, a ABI odpowiada rzeczywistemu wdrożeniu.
