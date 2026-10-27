# REST API

**Język:** Java 21; Spring MVC.

Plan: `BatchController`, `CommandController`, `ApiExceptionHandler`.
Modele transportowe są w [dto](dto/README.md).

Kontroler mapuje HTTP na przypadek użycia, waliduje DTO i przekazuje
uwierzytelniony kontekst. Nie pinuj tutaj raportu i nie czekaj na blockchain.
Mapowanie ścieżek, odpowiedzi i statusów ustala protokół/OpenAPI w katalogu
dokumentacji projektu.

`POST` zwraca `202` dopiero po trwałym przyjęciu i podaje URL statusu.
GET zwraca projekcję, a nie ręcznie konstruowany stan z pending transakcji.
Wspólny `@RestControllerAdvice` mapuje konkretne błędy na Problem Details.
Sprawdzaj autoryzację (Bearer; SIWE tylko do wydania tokenu, bez cookie/CSRF), paginację i zgodność batchId path/body.
`qaPassed=false` nie jest błędem `422`: zwróć `202`, wynik polecenia ma `outcome=REJECTED`.

Testuj MockMvc: schema fail, auth fail, 404, konflikt, przyjęcie i brak
fikcyjnego txHash. Nie serializuj encji JPA bezpośrednio.
