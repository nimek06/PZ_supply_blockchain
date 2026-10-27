# Panel operatora zmiany - kontrola QA

**Język:** TypeScript/TSX; Wagmi/Viem i RainbowKit.

Plan: `QaPage.tsx`, lista etapów czekających na kontrolę,
`QaApprovalForm.tsx` i hook podpisu approval.
Wyświetl partię, etap, zweryfikowany reportHash i wynik pomiarów przed podpisem.

Według PDF zamknięcie kontroli wymaga klucza stacji/czujnika i klucza
operatora zmiany. QA_INSPECTOR_ROLE to nazwa techniczna uprawnienia tego
operatora. Nie dodajemy trzeciego zatwierdzającego.

Approval to podpis EIP-712 `StageApproval(bytes32 batchId, uint32 stageId, bytes32 reportHash,
uint256 deadline)` wykonany portfelem operatora zmiany (`signTypedData`): bez transakcji, bez gazu i bez `approveStage`.
Frontend wysyła podpis do backendu (tabela `qa_approvals`); relayer dołącza go do `completeStage`.
Podpis dotyczy konkretnego etapu/hash, nie przyszłych raportów; pokaż `deadline` podpisu.
Kandydat pochodzi z chronionego API i ma już podpis stacji oraz CID;
nie może pochodzić wyłącznie z StageCompleted, bo byłoby za późno na approval.
Sprawdź, że operator i stacja mają różne adresy; kontrakt i tak weryfikuje oba podpisy atomowo.
Szczegóły: [przepływ kontroli](../../../../docs/flows.md).

Sprawdź właściwy chainId (domena EIP-712) i adres kontraktu, rolę QA, rejection portfela
i wygaśnięcie podpisu. Po potwierdzeniu `completeStage` (przez backend) invaliduj queries.
Samo połączenie portfela ani widoczny przycisk nie daje roli w kontrakcie.
