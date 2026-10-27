# Panel operatora zmiany - kontrola QA

**Język:** TypeScript/TSX; Wagmi/Viem i RainbowKit.

Plan: `QaPage.tsx`, lista etapów czekających na kontrolę,
`QaApprovalForm.tsx` i hook zatwierdzenia.
Wyświetl partię, etap, zweryfikowany reportHash i wynik pomiarów przed podpisem.

Według PDF zamknięcie kontroli wymaga klucza stacji/czujnika i klucza
operatora zmiany. QA_INSPECTOR_ROLE to nazwa techniczna uprawnienia tego
operatora. Nie dodajemy trzeciego zatwierdzającego.

Bazowy wariant: portfel operatora zmiany wysyła operację bezpośrednio do kontraktu
według aktualnego ABI; backend indeksuje event i odświeża projekcję.
Nie dodawaj endpointu relayera QA bez osobno uzgodnionego podpisu/uprawnień.
Zatwierdzenie dotyczy konkretnego etapu/hash, nie przyszłych raportów.
Kandydat pochodzi z chronionego API i ma już podpis stacji oraz CID;
nie może pochodzić wyłącznie z StageCompleted, bo byłoby za późno na approval.
Sprawdź, że operator i stacja mają różne adresy. Relayer wysyła completeStage
dopiero po tym approval i nadal musi dostarczyć podpis stacji.
Szczegóły: [przepływ kontroli](../../../../docs/flows.md).

Sprawdź właściwy chainId i adres kontraktu, rolę QA, rejection portfela,
revert, receipt i wymagane potwierdzenia. Po potwierdzeniu invaliduj queries.
Samo połączenie portfela ani widoczny przycisk nie daje roli w kontrakcie.
