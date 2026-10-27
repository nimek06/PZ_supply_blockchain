# Kod aplikacji React

**Języki:** TypeScript, TSX, CSS.

- [app](app/README.md): bootstrap, router, providery i globalne style.
- [assets](assets/README.md): obrazy/fonty importowane przez Vite.
- [components](components/README.md): komponenty wielokrotnego użytku.
- [features](features/README.md): funkcje biznesowe i ekrany.
- [lib](lib/README.md): integracje HTTP i blockchain.

Start Vite może pozostać w `main.tsx`, delegując konfigurację do app.
Nie umieszczaj każdego komponentu w korzeniu. Testy komponentów mogą leżeć
obok kodu; wspólny setup/E2E należy do katalogu tests modułu.
Nie dodawaj globalnego store tylko do cache odpowiedzi API.
