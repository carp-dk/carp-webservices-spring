// Copy pasted from: https://github.com/InseeFrLab/keycloakify/blob/main/../../login/Template.tsx

import { clsx } from "keycloakify/tools/clsx";
import { type TemplateProps } from "keycloakify/login/TemplateProps";
import { getKcClsx } from "keycloakify/login/lib/KcClsx";
import { useSetClassName } from "keycloakify/tools/useSetClassName";

import {
  Alert,
  FormControl,
  MenuItem,
  Select,
  Typography,
} from "@mui/material";
import LanguageIcon from "@mui/icons-material/Language";
import AuthPageLayout from "../components/Layout/PublicPageLayout/AuthPageLayout";
import type { KcContext } from "./KcContext";
import type { I18n } from "./i18n";
import PublicPageLayout from "../components/Layout/PublicPageLayout";
import BootstrapInput from "../components/BootstrapInput";
import { useEffect } from "react";
import { useInitialize } from "keycloakify/login/Template.useInitialize";

export const Template = (props: TemplateProps<KcContext, I18n>) => {
  const {
    displayMessage = true,
    headerNode,
    infoNode = null,
    documentTitle,
    bodyClassName,

    kcContext,
    i18n,
    doUseDefaultCss,
    classes,
    children,
  } = props;

  const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

  const { msg, msgStr, currentLanguage, enabledLanguages } = i18n;

  const { auth, url, message, isAppInitiatedAction } = kcContext;

  useEffect(() => {
    document.title =
      documentTitle ?? msgStr("loginTitle", kcContext.realm.displayName);
  }, []);

  useSetClassName({
    qualifiedName: "html",
    className: kcClsx("kcHtmlClass"),
  });

  useSetClassName({
    qualifiedName: "body",
    className: bodyClassName ?? kcClsx("kcBodyClass"),
  });

  const { isReadyToRender } = useInitialize({
    kcContext,
    doUseDefaultCss,
  });

  if (!isReadyToRender) {
    return null;
  }

  const getLanguageLabel = (languageTag: string) => {
    return enabledLanguages.find((l) => l.languageTag === languageTag)?.label;
  };

  return (
    <PublicPageLayout
      infoNode={
        <>
          {enabledLanguages.length > 1 && (
            <FormControl variant="standard">
              <Select
                value={currentLanguage.languageTag}
                input={<BootstrapInput />}
                renderValue={(selected) => {
                  return (
                    <div
                      style={{
                        display: "flex",
                        flexDirection: "row",
                        alignItems: "center",
                        gap: 8,
                      }}
                    >
                      <LanguageIcon sx={{ strokeWidth: 0.8, stroke: "#fff" }} />
                      <Typography>
                        {getLanguageLabel(selected)}
                      </Typography>
                    </div>
                  );
                }}
              >
                {enabledLanguages.map(({ languageTag, href }) => (
                  <a
                    href={href}
                    style={{ textDecoration: "none", color: "inherit" }}
                    key={languageTag}
                  >
                    <MenuItem
                      key={languageTag}
                      value={languageTag}
                      // onClick={() => getChangeLocaleUrl(languageTag)}
                    >
                      {getLanguageLabel(languageTag)}
                    </MenuItem>
                  </a>
                ))}
              </Select>
            </FormControl>
          )}
          {infoNode}
        </>
      }
    >
      <AuthPageLayout title={headerNode}>
        {/* 
        <header className={getClassName("kcFormHeaderClass")}>
          {!(auth !== undefined && auth.showUsername && !auth.showResetCredentials) ? (
            displayRequiredFields ? (
              <div className={getClassName("kcContentWrapperClass")}>
                <div className={clsx(getClassName("kcLabelWrapperClass"), "subtitle")}>
                  <span className="subtitle">
                    <span className="required">*</span>
                    {msg("requiredFields")}
                  </span>
                </div>
                <div className="col-md-10">
                  <h1 id="kc-page-title">{headerNode}</h1>
                </div>
              </div>
            ) : (
              <h1 id="kc-page-title">{headerNode}</h1>
            )
          ) : displayRequiredFields ? (
            <div className={getClassName("kcContentWrapperClass")}>
              <div className={clsx(getClassName("kcLabelWrapperClass"), "subtitle")}>
                <span className="subtitle">
                  <span className="required">*</span> {msg("requiredFields")}
                </span>
              </div>
              <div className="col-md-10">
                {showUsernameNode}
                <div className={getClassName("kcFormGroupClass")}>
                  <div id="kc-username">
                    <label id="kc-attempted-username">{auth?.attemptedUsername}</label>
                    <a id="reset-login" href={url.loginRestartFlowUrl}>
                      <div className="kc-login-tooltip">
                        <i className={getClassName("kcResetFlowIcon")} />
                        <span className="kc-tooltip-text">{msg("restartLoginTooltip")}</span>
                      </div>
                    </a>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <>
              {showUsernameNode}
              <div className={getClassName("kcFormGroupClass")}>
                <div id="kc-username">
                  <label id="kc-attempted-username">{auth?.attemptedUsername}</label>
                  <a id="reset-login" href={url.loginRestartFlowUrl}>
                    <div className="kc-login-tooltip">
                      <i className={getClassName("kcResetFlowIcon")} />
                      <span className="kc-tooltip-text">{msg("restartLoginTooltip")}</span>
                    </div>
                  </a>
                </div>
              </div>
            </>
          )}
        </header>
        */}
        {displayMessage &&
          message !== undefined &&
          (message.type !== "warning" || !isAppInitiatedAction) && (
            <Alert severity={message.type} sx={{ mb: 2 }}>
              {message.summary}
            </Alert>
          )}
        {children}
        {auth !== undefined &&
          auth.showTryAnotherWayLink && (
            <form
              id="kc-select-try-another-way-form"
              action={url.loginAction}
              method="post"
              className={clsx(kcClsx("kcContentWrapperClass"))}
            >
              <div className={kcClsx("kcFormGroupClass")}>
                <input type="hidden" name="tryAnotherWay" value="on" />
                {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
                <a
                  href="#"
                  id="try-another-way"
                  onClick={() => {
                    document.forms[
                      "kc-select-try-another-way-form" as never
                    ].submit();
                    return false;
                  }}
                >
                  {msg("doTryAnotherWay")}
                </a>
              </div>
            </form>
          )}
      </AuthPageLayout>
    </PublicPageLayout>
  );
};

export default Template;
