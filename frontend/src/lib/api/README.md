# Klient backendu

**Język:** TypeScript; Axios.

Plan: `client.ts`, `batchesApi.ts`, `commandsApi.ts` i typy wynikające z OpenAPI.
Generowane typy/klient mają jawny proces aktualizacji; nie poprawiaj ich ręcznie.
Wspólna instancja Axios: baseURL, timeout, obsługa cancellation i Problem Details.

Funkcje zwracają typowane dane lub jawny błąd. Nie zwracaj `[]` po timeout.
Query hooks należą do features/batches, nie do tej warstwy transportowej.
POST używa właściwej idempotencji; nie włączaj automatycznego ponawiania
każdego zapisu bez zachowania tej samej tożsamości i treści.

Typowanie TS nie waliduje runtime odpowiedzi - dane raportu z IPFS i
niezaufane odpowiedzi wymagają guards/schema validation.
Nie dodawaj JWT Pinaty; backend jest właścicielem integracji z Pinatą.
