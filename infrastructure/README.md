# Infrastruktura

**Języki:** YAML (Compose/CI), opcjonalnie Dockerfile i shell.

- [local](local/README.md): usługi potrzebne lokalnie.
- [ci](ci/README.md): plan pipeline'ów i wymagane kroki weryfikacji.

Najpierw przygotuj działającą lokalną ścieżkę, potem kontenery aplikacji.
Nie dodawaj Kubernetes ani usług chmurowych bez wymagania. Wartości publiczne
sieci są w `shared/deployments`, a sekrety poza repozytorium.

Pliki Compose i Dockerfile są dopiero do utworzenia. Zanim dodasz skrypty,
opisz wejścia, wymagane narzędzia, timeout i zachowanie przy błędzie.
Skrypt ma kończyć się błędem przy niedostępności usługi, nie pomijać problemu.
