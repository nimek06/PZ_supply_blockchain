# Scenariusze linii

**Język:** YAML, walidowany modelem Pydantic (`scenario.py`).

Plan: `ebike-happy-path.yaml`, `ebike-qa-fail.yaml`, docelowo `out-of-order`,
`retry-after-timeout`. Opisz stacje, seed, kolejność kroków, parametry/anomalie oraz
oczekiwane wyniki. Nazwy etapów mapuj do uzgodnionej listy (0..3). Nie dodawaj
executable Python ani `eval` do danych scenariusza.

Scenariusze nie zawierają kluczy. Adres/sekret stacji pochodzą z konfiguracji
(`config.py`; CLI `--station-key`/`--certificate`). Timestamp, `deadline` i podpis
tworzy runner (brak nonce); zapisany stary podpis szybko wygasa. Test negatywny ma
jawnie określać spodziewany błąd i brak przejścia stanu; `ebike-qa-fail.yaml`
(etap 1, `qaPassed=false` zgodne z profilem) oczekuje `outcome=REJECTED`
(`StageRejected`, `202`), nie `422`. Scenariusz retry powtarza tę samą treść,
nie dwa losowe raporty.

Dodaj scenariusz kontroli z podpisem stacji i osobnym zatwierdzeniem operatora
zmiany (`StageApproval`) oraz negatywne warianty bez drugiego klucza i z tym samym adresem.
Osobne przypadki: odwołany certyfikat stacji, stacja wyrejestrowana/poza `stageMask`.
Podpisu operatora nie generuj kluczem stacji ukrytym pod inną nazwą.

Uruchomienie: `python -m supply_chain_simulator --scenario scenarios/ebike-happy-path.yaml
--backend-url ... --rpc-url ... --verifying-contract ... --station-key ... --certificate ...`.

## Tryby i scenariusze (tydzień 4)

- `ebike-happy-path.yaml`, `ebike-qa-fail.yaml` — **accept-only**: każdy krok kończy
  się na `202` (`status=ACCEPTED`), bez `outcome` i `nextStage`. Działają bez workera.
- `ebike-vertical.yaml` — **pion**: etap 0 partii utworzonej przez operatora i
  czekanie, aż publiczna projekcja pokaże `nextStage=1`. Wymaga workera/relayera
  i indexera; bez nich runner zgłosi brak potwierdzenia w limicie czasu.
- `ebike-repeat-completed-stage.yaml` — **krok negatywny**: po potwierdzeniu etapu 0
  ponowne zgłoszenie tego samego etapu ma dać `409 STAGE_ALREADY_COMPLETED`.

Numer partii nadaje backend, więc dla pionu podaj go przez `--batch-number`
(albo użyj `scripts/run-vertical.sh`, które tworzy partię i uruchamia scenariusz).

Pola oczekiwań kroku: `httpStatus` (domyślnie `202`), `outcome` (wynik polecenia,
wymaga tokenu), `nextStage` (potwierdzenie etapu w projekcji) i `errorCode`
(oczekiwany kod odrzucenia dla kroku negatywnego). Krok ze `httpStatus != 202` jest
krokiem negatywnym: nie czeka na potwierdzenie i nie przesuwa partii.

## Wynik W4-11 (2026-11-24)

Sprawdzone na realnym stosie (Anvil 31337, Kubo, PostgreSQL) przez `run-vertical.sh`
oraz `cast`:

| Sprawdzenie | Wynik |
|---|---|
| login SIWE operatora + `POST /batches` | `202`, polecenie utworzone (`58e5fa00…`) |
| indexer: `BatchCreated` → projekcja | `GET /batch-numbers/B-2026-0100` → `nextStage: 0`, `stale: false` |
| `completeStage` etapu 0 (podpis stacji + CID z Kubo) | `getBatch` → `nextStage = 1`; projekcja → `nextStage: 1` |
| CID/hash | bajty z Kubo (279 B) → Keccak == `reportHash` == `stage_projections.report_hash` |
| tryb czekania runnera | `etap 0: qaPassed=True status=ACCEPTED outcome=None nextStage=1` |

**Blokada automatycznego pionu (W4-04):** sender relayera podpisuje transakcję typu
`0x04` (EIP-7702) zamiast `0x02` (EIP-1559) i z pomieszanymi polami, więc Anvil ją
odrzuca, a polecenia kończą `FAILED SEND_REJECTED`; żadna transakcja nie trafia do
łańcucha. Poprawka: użyć przeciążenia EIP-1559 bez `authorizationList`. Szczegóły:
[week4-summary](../../docs/planning/weekly/week4/week4-summary.md).

## Wynik W3-11 (2026-11-23)

Kanoniczne uruchomienie: `backend/scripts/local-e2e/run-e2e.sh` (stos Compose, kontrakt,
backend, stacja, scenariusz). Przebieg zakończony sukcesem (5/5), `202` na oba kroki:

```text
scenariusz ebike-happy-path: partia 0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1 (2 kroki)
  etap 0: qaPassed=True status=ACCEPTED outcome=None nextStage=0
  etap 1: qaPassed=True status=ACCEPTED outcome=None nextStage=0
```

`outcome=None` i `nextStage=0` są oczekiwane: harness nie włącza workera/relayera
(`start-backend.sh` nie ustawia `RELAYER_WORKER_ENABLED`), więc nie powstaje
`StageCompleted`/`StageRejected`, a indexer nie ma czego potwierdzić. To nie jest
"udawane" potwierdzenie - polecenia trwale leżą jako `ACCEPTED` w bazie.

Zgłoszenia przez lokalne API (backend z E2E, certyfikaty fixture'ów wstawione do
`station_certificates`, okno deadline powiększone dla stałych `deadline` z fixture'ów):

| Sprawdzenie | Wynik | HTTP / kod |
|---|---|---|
| poprawne zgłoszenie (świeży certyfikat stacji) ×10 | ten sam `commandId`, jedno polecenie | `202`, `8ed4d701…` |
| zgłoszenie po restarcie backendu (te same bajty) | ten sam `commandId` | `202`, `a122f9b0…` |
| `report-profile-violation-stage0..3.json` | naruszenie profilu | `422 PROFILE_VIOLATION` |
| `report-valid-stage0.json` ze zmutowanym podpisem | zły podpis | `403 INVALID_SIGNATURE` |
| `report-valid-stage0.json` z nieznanym `stationCertificateId` | brak certyfikatu | `403 CERTIFICATE_INVALID` |
| `station-certificate-expired.json` | certyfikat wygasły | `403 CERTIFICATE_INVALID` |
| `station-certificate-untrusted-issuer.json` | wystawca spoza allowlisty | `403 CERTIFICATE_INVALID` |
| `report-valid-stage0.json` bez zmian | `issuedAt` w przyszłości | `403 CERTIFICATE_INVALID` |

Ustalenie: statyczne fixture'y mają `issuedAt = 1800000000`, czyli **przyszłość**
względem zegara rzeczywistego, a weryfikator odrzuca `now < issuedAt`
(`NOT_YET_VALID`). Dlatego harness wystawia certyfikat z bieżącym `issuedAt`
(`prepare-station.sh --issued-at` domyślnie teraz) i tylko taki certyfikat przechodzi
pozytywną ścieżkę. Fixture'y nadają się do testów negatywnych i do walidacji schematu,
ale nie do "na żywo" pozytywnego przyjęcia bez re-generacji czasu.

Idempotencja i trwałość są też pokryte testem backendu
`backend/src/test/java/pl/pz/supplyblockchain/api/StageSubmissionIT.java`
(10 identycznych POST-ów → jedno `commandId`, także po wyczyszczeniu kontekstu
persystencji). Odczyt `GET /api/v1/commands/{id}` wymaga tokenu Bearer (SIWE), więc
scenariusz pozostaje accept-only, a prywatny status polecenia czyta frontend.
