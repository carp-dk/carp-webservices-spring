/* eslint-disable no-nested-ternary */
import { Box, Typography } from "@mui/material";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { assert } from "keycloakify/tools/assert";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";
import CarpButton from "../../components/Buttons/AuthActionButton/styles";

const Info = (
  props: PageProps<Extract<KcContext, { pageId: "info.ftl" }>, I18n>
) => {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { advancedMsgStr, msgStr } = i18n;

  assert(
    kcContext.message !== undefined,
    "No message in kcContext.message, there will always be a message in production context, add it in your mock"
  );

  const {
    messageHeader,
    message,
    requiredActions,
    skipLink,
    pageRedirectUri,
    actionUri,
    client,
  } = kcContext;

  const headerNode = () => {
    if (messageHeader !== "<Message header>") {
      return <>{messageHeader}</>; // ✅ Apply translation
    }
    if (message?.summary) {
      switch (message.type) {
        case "info":
          return <Typography variant="h2">Information</Typography>;
        case "warning":
          return <Typography variant="h2">Warning</Typography>;
        case "error":
          return <Typography variant="h2">Error</Typography>;
        default:
          return <Typography variant="h2">Notification</Typography>;
      }
    }
    return <Typography variant="h2">Info</Typography>;
  };

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss, classes }}
      displayMessage={false}
      headerNode={headerNode()}
    >
      <div id="kc-info-message">
        <Typography variant="body1" className="instruction">
          {message.summary}{" "}
          {requiredActions !== undefined && (
            <b>
              {requiredActions
                .map((requiredAction) =>
                  advancedMsgStr(`requiredAction.${requiredAction}` as const)
                )
                .join(", ")}
            </b>
          )}
        </Typography>
        {(() => {
          if (!skipLink && pageRedirectUri !== undefined) {
            return (
              <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
                <CarpButton href={pageRedirectUri} variant="contained">
                  {msgStr("backToApplication")}
                </CarpButton>
              </Box>
            );
          }
          if (actionUri !== undefined) {
            return (
              <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
                <CarpButton href={actionUri} variant="contained">
                  {msgStr("proceedWithAction")}
                </CarpButton>
              </Box>
            );
          }
          if (client.baseUrl !== undefined) {
            return (
              <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
                <CarpButton href={client.baseUrl} variant="contained">
                  {msgStr("backToApplication")}
                </CarpButton>
              </Box>
            );
          }
          return null;
        })()}
      </div>
    </Template>
  );
};

export default Info;
