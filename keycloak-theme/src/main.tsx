import {
  CssBaseline,
  StyledEngineProvider,
  ThemeProvider,
} from "@mui/material";

import { themeInstance } from "./utils/theme";

import { createRoot } from "react-dom/client";
import { Suspense } from "react";
import { KcPage } from "./kc.gen";

createRoot(document.getElementById("root")!).render(
  <Suspense>
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={themeInstance}>
        <CssBaseline />
        {!window.kcContext ? (
          <h1>No Keycloak Context</h1>
        ) : (
          <KcPage kcContext={window.kcContext} />
        )}
      </ThemeProvider>
    </StyledEngineProvider>
  </Suspense>,
);
