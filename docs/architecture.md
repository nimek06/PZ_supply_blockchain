# Architektura i odpowiedzialności

## Stan projektu

Poniższy podział jest projektem implementacji. Nie opisuje działających usług.
Punktem wyjścia jest jeden backend i jedna instancja relayera, nie mikroserwisy.
Zakres wyznacza [opis PDF i mapa zgodności](source-alignment.md):
modelowa linia bez przebudowy fizycznej infrastruktury, 12 tygodni,
certyfikowane tożsamości stacji i dwa klucze przy zamknięciu kontroli.
Uwzględnia zmiany [v2](changes-v2.md) z 2026-10-27; podział prac: [team-split](team-split.md).

## Granice modułów

| Część | Może | Nie powinna |
|---|---|---|
| Kontrakt | autoryzować podpis i relayera, sprawdzać etapy, emitować historię | wykonywać HTTP, przechowywać pełne raporty, ufać samej walidacji backendu |
| Backend | walidować, pinować, kolejkować, indeksować, udostępniać projekcję | ogłaszać sukces przed potwierdzeniem, zmieniać potwierdzoną historię |
| Symulator | generować i podpisywać raporty stacji | trzymać klucz relayera, samodzielnie przypisywać sobie role |
| Frontend | czytać API, podpisywać dozwolone operacje QA portfelem | przechowywać sekrety backendu, uznawać ukryty przycisk za autoryzację |
| Shared | definiować wymianę danych i publiczne artefakty | zawierać biblioteki mieszające Java, Python i TypeScript |

Przepływ zależności backendu:
`api -> application -> domain`; `infrastructure` implementuje porty z
`application/ports`; `config` łączy implementacje w Springu.
Model domenowy nie importuje Web3j, HTTP, JPA ani adnotacji Spring.

## Źródła prawdy

- Kontrakt: dozwolone przejścia, role, rejestr stacji, potwierdzone zdarzenia.
- IPFS: treść raportów wskazana CID. Publiczny raport nie może zawierać danych
  osobowych, sekretów ani poufnych identyfikatorów stanowisk.
- PostgreSQL: trwałe polecenia relayera, stan przetwarzania, checkpoint indexera
  i odbudowywalna projekcja danych blockchainowych.
- Schematy: źródło prawdy dla formatu wymiany, nie osobne ręcznie rozbieżne DTO.

Nie ma transakcji ACID obejmującej jednocześnie PostgreSQL, IPFS i blockchain.
Każdy krok potrzebuje zapisanego wyniku i bezpiecznego wznowienia po awarii.

## Maszyna stanów i QA

Użytkownik wybrał ogólną marszrutę przygotowanie -> montaż -> kontrola ->
pakowanie, bez konkretnego produktu. [Reguły produkcji](production-rules.md)
opisują stageId 0..3 i bramkę kontroli na etapie 2. Profile pomiarów i szczegóły
formatów pozostają do ustalenia; numeracja i reguły muszą być jednakowe
w kontrakcie, schematach i symulatorze.

Odróżniaj wynik pomiarów stacji `qaPassed` od zatwierdzenia przez inspektora.
Inspektor w tych instrukcjach oznacza operatora zmiany uprawnionego do QA.
Na etapie kontroli samo `qaPassed = true` nie wystarcza:
wymagane są podpis stacji (`StageCompletion`) i niezależny podpis operatora (`StageApproval`).
Operator zatwierdza konkretną partię, etap i `reportHash`, nie całą partię
na przyszłość. Ten sam adres nie może pełnić obu stron dla danego raportu.
Raport negatywny nie przesuwa produkcji: kontrakt zapisuje go jako `StageRejected`
(ślad audytowy), nie jako `StageCompleted`.

## Obsługa awarii

- Brak magazynu raportów/RPC: jawny błąd lub zapisany stan oczekiwania na ponowienie.
- Nieznany wynik wysłania transakcji: sprawdź jej wcześniej obliczony hash,
  wyślij ponownie te same bajty; bez fee-bump w MVP.
- Restart: worker wznawia trwałe zadania; kolejka w pamięci może tylko budzić worker.
- Reorganizacja łańcucha: indexer przetwarza bloki z głębokością potwierdzeń i ponownie
  skanuje okno ostatnich N bloków z porównaniem block hash. Zmiana projekcji musi
  być widoczna dla klienta.
- Niezgodność raportu IPFS z `reportHash`: pokaż błąd integralności, nie raport
  oznaczony jako zweryfikowany.

## Autoryzacja

Docelowo: `DEFAULT_ADMIN_ROLE`, `RELAYER_ROLE`, `QA_INSPECTOR_ROLE`,
`OPERATOR_ROLE` (API) oraz **rejestr stacji** zamiast `STATION_ROLE`
(`registerStation`/`revokeStation`, `expiresAt`, `stageMask`). Autoryzacja stacji
dotyczy adresu odzyskanego z podpisu, nie `msg.sender` relayera.
Administrator wdrożenia nie jest kontem stacji.
`QA_INSPECTOR_ROLE` jest techniczną nazwą uprawnienia operatora zmiany
do współzatwierdzania kontroli; nie jest zamiennikiem podpisu stacji.
Backend dodatkowo weryfikuje [certyfikat stacji](station-identity.md) i jego zgodność z rejestrem.

On-chain zapisuj tylko identyfikatory, odniesienia kryptograficzne i niezbędny
stan reguł (role, rejestr stacji, nextStage, skróty odrzuceń). Pełny JSON, certyfikaty
i surowe pomiary pozostają off-chain. Eventy także kosztują gaz:
nie przenoś do nich pełnych raportów zamiast do storage.

Publiczne są odczyty paszportu. Tworzenie partii i operacje administracyjne
wymagają autoryzacji. Jeśli backend przyjmuje operacje operatora/QA, potrzebuje
uwierzytelnionego użytkownika: SIWE wydaje token Bearer (bez cookie i CSRF).
Samo połączenie MetaMask nie loguje do backendu.
Ostateczna autoryzacja operacji on-chain pozostaje w kontrakcie.

Szczegóły: [dwa klucze i approval](quality-approval.md),
[macierz uprawnień i login](access-control.md) oraz
[wznowienie po błędzie](corrections.md). Pełna lista zmian: [v2](changes-v2.md).
