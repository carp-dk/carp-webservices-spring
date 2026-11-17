# CARP Keycloak theme

Custom Keycloak login theme for CARP Web Services, built with [Keycloakify](https://keycloakify.dev) 11, React and MUI.

Requires [Bun](https://bun.com). `bunfig.toml` makes `bun run` execute scripts with Bun instead of Node.

## Commands

```bash
bun install                   # Install dependencies
bun run storybook             # Develop pages in isolation (http://localhost:6006)
bun run dev                   # Start the Vite dev server
bun run build-keycloak-theme  # Build the theme jars into dist_keycloak/
bun lint                      # Lint (configured in bunfig.toml)
bun fmt                       # Format (configured in bunfig.toml)
bun fmt --check               # Check formatting without writing
```

Use `keycloak-theme-for-kc-all-other-versions.jar` from `dist_keycloak/` for Keycloak 26.

## Keycloakify CLI

```bash
bunx keycloakify eject-page              # Copy a built-in page into src/login/pages to customize it
bunx keycloakify add-story               # Add a Storybook story for a page
bunx keycloakify initialize-email-theme  # Set up an email theme
bunx keycloakify start-keycloak          # Run a local Keycloak in Docker with the theme loaded
```

After ejecting a page, add a case for it in `src/login/KcPage.tsx`.
