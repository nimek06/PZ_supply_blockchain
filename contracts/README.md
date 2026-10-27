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

Planowane pliki w korzeniu: `foundry.toml` i deklaracje zależności.
`out/`, `cache/` i `broadcast/` są wynikami narzędzi, nie kodem do wersjonowania.
Jeżeli instalator zależności tworzy `lib/`, nie traktuj go jako własnego modułu.

## Kolejność pisania

Wejścia do projektu reguł: [marszruta](../docs/production-rules.md),
[dokładna polityka dwóch kluczy](../docs/quality-approval.md)
i [role](../docs/access-control.md). [Recovery](../docs/corrections.md)
pozostaje porównaniem alternatyw; nie dodawaj jeszcze samodzielnie funkcji
unieważniania nonce ani rewizji podpisu.

1. Uzgodnij [protokół](../docs/protocol.md), enum/listę etapów oraz ręczne QA.
2. Napisz testy tworzenia partii i niedozwolonej zmiany etapu.
3. Dodaj `AccessControl`: osobny admin, relayer, stacje i operatorzy zmiany
   z uprawnieniem QA; nie utożsamiaj relayera z żadnym z dwóch signerów.
4. Dodaj OpenZeppelin `EIP712` i `ECDSA`, domenę oraz licznik nonce dla stacji.
5. Zweryfikuj wspólny wektor podpisu i wszystkie mutacje podpisanej treści.
6. Dodaj przejścia, bramki QA, custom errors i pełne zdarzenia do indeksowania.
7. Wdróż na Anvil, eksportuj ABI i publiczne dane wdrożenia.
8. Po testach integracji wdróż na Amoy i zweryfikuj kod kontraktu.

## Zasady

`msg.sender` przy zgłoszeniu etapu jest relayerem; adres stacji odzyskaj z podpisu
i sprawdź jego rolę. Podpis obejmuje `reportHash`, nie nieznany jeszcze CID.
Kontrakt nie pobiera IPFS: zapisuje CID i hash do późniejszej weryfikacji.
Nie zwiększaj nonce przy rewercie. Używaj odzyskiwania podpisów odpornego
na malleability z biblioteki zamiast ręcznego `ecrecover`.

Nie przechowuj całych raportów w storage. Ogranicz rozmiar CID i danych
wejściowych. Rozważ historię opartą na zdarzeniach zamiast nieograniczonej
tablicy odczytywanej w jednym wywołaniu. To ma być prosty kontrakt, bez proxy
i systemu aktualizacji, dopóki nie pojawi się wymaganie.
Pełne certyfikaty i kopie podpisów również nie należą do storage.
Approval zapisuje adres operatora związany z partią/etapem/hash.
Przy zamykaniu kontroli sprawdź różne adresy operatora i stacji oraz aktualne role.
Wariant dwóch powiązanych podpisów opisuje [protokół](../docs/protocol.md);
nie zakładaj automatycznie portfela Safe ani kosztownego wdrożenia multisig.

## Współpraca i sprawdzenie

Po zmianie publicznych funkcji/events eksportuj ABI do
[shared/abi](../shared/abi/README.md), a adresy do
[deployments](../shared/deployments/README.md). Backend regeneruje wrapper.
Nigdy nie przekazuj klucza administratora plikiem w repozytorium.

Po inicjalizacji: `forge fmt --check`, `forge build`, `forge test`.
Kontrakt jest gotowy do integracji, gdy testuje role, kolejność, QA, replay
i wspólny podpis, a ABI odpowiada rzeczywistemu wdrożeniu.
