import {
  CssBaseline,
  StyledEngineProvider,
  ThemeProvider,
} from "@mui/material";
import { themeInstance } from "../utils/theme";

import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
import Register from "./pages/Register";
import Login from "./pages/Login";
import LoginUpdateProfile from "./pages/LoginUpdateProfile";
import LoginUpdatePassword from "./pages/LoginUpdatePassword";
import LoginResetPassword from "./pages/LoginResetPassword";
import Info from "./pages/Info";
import CfCaptcha from "./pages/CfCaptcha";
const UserProfileFormFields = lazy(
  () => import("keycloakify/login/UserProfileFormFields"),
);

const doMakeUserConfirmPassword = true;

export default function KcPage(props: Readonly<{ kcContext: KcContext }>) {
  const { kcContext } = props;

  const { i18n } = useI18n({ kcContext });

  return (
    <Suspense>
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={themeInstance}>
          <CssBaseline />
          {(() => {
            switch (kcContext.pageId) {
              case "login.ftl":
                return (
                  <Login
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "register.ftl":
                return (
                  <Register
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "login-update-profile.ftl":
                return (
                  <LoginUpdateProfile
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "login-update-password.ftl":
                return (
                  <LoginUpdatePassword
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "login-reset-password.ftl":
                return (
                  <LoginResetPassword
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "info.ftl":
                return (
                  <Info
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              case "cf-captcha.ftl":
                return (
                  <CfCaptcha
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                  />
                );
              default:
                return (
                  <DefaultPage
                    kcContext={kcContext}
                    i18n={i18n}
                    classes={classes}
                    Template={Template}
                    doUseDefaultCss={false}
                    UserProfileFormFields={UserProfileFormFields}
                    doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                  />
                );
            }
          })()}
        </ThemeProvider>
      </StyledEngineProvider>
    </Suspense>
  );
}

const classes = {} satisfies { [key in ClassKey]?: string };
