# Schematy wymiany

**Języki:** YAML/JSON; dokumentacja Markdown.

Planowane pliki:
- `openapi.yaml`: ścieżki, auth (SIWE -> opaque Bearer), request/response, statusy i Problem Details.
- `telemetry.schema.json`: wersja raportu, wymagane pola, typy, limity, jednostki.
- `eip712.json`: domena demonstracyjna, typy `StageCompletion` (bez nonce) i `StageApproval`
  oraz uporządkowane definicje pól.
- `station-certificate.schema.json`: format poświadczenia stacji, wystawca,
  adres, serial, ważność i `stageMask`; certyfikat jest poświadczeniem audytowym i musi zgadzać się
  z rejestrem on-chain (wariant certyfikatu wg DEC-13; sprawdź [decisions](../../docs/decisions.md)).

OpenAPI obejmuje też chronioną kolejkę raportów WAITING_FOR_QA i uzgodniony
login SIWE (wydanie tokenu). Podpis stacji i podpis operatora zmiany dotyczą tej samej partii,
etapu i hash; nie definiuj approval jako dowolnego boolean z przeglądarki.
Status polecenia ma `outcome` (ACCEPTED/REJECTED); `qaPassed=false` to `202`, nie `422`.
Numer partii: `B-RRRR-NNNN`.

Przed tworzeniem tych plików zatwierdź pięć rozwinięć:
[reguły/profil pomiarów](../../docs/production-rules.md),
[zakres approval](../../docs/quality-approval.md),
[format certyfikatu](../../docs/station-identity.md)
i [login i autoryzacja](../../docs/access-control.md).
Zmiany v2 ([changes-v2](../../docs/changes-v2.md)) są wiążące: brak nonce, brak cookie/CSRF.
W bieżącym etapie rozpisano dokumentację tych obszarów, nie gotowe schematy.

Zacznij od [protokołu](../../docs/protocol.md). Zabroń nieznanych pól raportu.
JSON Schema waliduje format; spójność path/body, stan kontraktu i kryptografia
to osobna walidacja aplikacji. Domena z `eip712.json` nie może zastępować
rzeczywistej konfiguracji sieci i adresu wdrożenia.

Wybierz i zapisz dialekt JSON Schema i wersję OpenAPI wspieraną przez generator.
Nie utrzymuj sprzecznych definicji w kilku plikach; używaj referencji lub
testów zgodności, jeśli narzędzia wymagają osobnej reprezentacji.
Po zmianie regeneruj klientów i odpal testy wszystkich konsumentów.
