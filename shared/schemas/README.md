# Schematy wymiany

**Języki:** YAML/JSON; dokumentacja Markdown.

Planowane pliki:
- `openapi.yaml`: ścieżki, auth, request/response, statusy i Problem Details.
- `telemetry.schema.json`: wersja raportu, wymagane pola, typy, limity, jednostki.
- `eip712.json`: domena demonstracyjna, primary type i uporządkowane definicje pól.
- `station-certificate.schema.json`: format poświadczenia stacji, wystawca,
  adres, serial i ważność; wariant certyfikatu wymaga DEC-13.

OpenAPI obejmuje też chronioną kolejkę raportów WAITING_FOR_QA i uzgodniony
login. Podpis stacji i podpis operatora zmiany dotyczą tej samej partii,
etapu i hash; nie definiuj approval jako dowolnego boolean z przeglądarki.

Przed tworzeniem tych plików zatwierdź pięć rozwinięć:
[reguły/profil pomiarów](../../docs/production-rules.md),
[zakres approval](../../docs/quality-approval.md),
[format certyfikatu](../../docs/station-identity.md)
i [transport loginu/sesji](../../docs/access-control.md).
[Recovery](../../docs/corrections.md) nadal porównuje alternatywy;
schema nie może narzucić nowego nonce/revision bez decyzji.
W bieżącym etapie rozpisano dokumentację tych obszarów, nie gotowe schematy.

Zacznij od [protokołu](../../docs/protocol.md). Zabroń nieznanych pól raportu.
JSON Schema waliduje format; spójność path/body, stan kontraktu i kryptografia
to osobna walidacja aplikacji. Domena z `eip712.json` nie może zastępować
rzeczywistej konfiguracji sieci i adresu wdrożenia.

Wybierz i zapisz dialekt JSON Schema i wersję OpenAPI wspieraną przez generator.
Nie utrzymuj sprzecznych definicji w kilku plikach; używaj referencji lub
testów zgodności, jeśli narzędzia wymagają osobnej reprezentacji.
Po zmianie regeneruj klientów i odpal testy wszystkich konsumentów.
