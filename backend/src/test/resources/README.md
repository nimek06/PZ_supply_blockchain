# Zasoby testów backendu

**Języki:** YAML, JSON, opcjonalnie SQL wyłącznie testowy.

Plan: `application-test.yml`, kontrolowane odpowiedzi Pinaty/RPC, Kubo w Testcontainers,
konfiguracje stubów i inicjalizacja odizolowanej bazy.
Wspólne raporty/podpisy odczytuj z shared/fixtures przez skonfigurowany
test resources lub loader; nie kopiuj ich bez kontroli zgodności.

Nie ustawiaj realnych sekretów i nie wskazuj produkcyjnej bazy.
Testy mają kończyć się czytelnym błędem, gdy brakuje wymaganej usługi testowej,
zamiast przechodzić po pominięciu wszystkich asercji.
