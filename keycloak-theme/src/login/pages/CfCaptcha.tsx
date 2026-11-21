import { useRef, useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import AuthActionButton from "../../components/Buttons/AuthActionButton";
import TurnstileWidget from "../../components/TurnstileWidget";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

// Challenge page for Phase Two's standalone "Cloudflare Turnstile validation" step.
// Like Phase Two's own cf-captcha.ftl, it submits as soon as the challenge is solved.
const CfCaptcha = (
  props: PageProps<Extract<KcContext, { pageId: "cf-captcha.ftl" }>, I18n>,
) => {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { url, auth, captchaSiteKey, captchaAction } = kcContext;

  const { msg, msgStr } = i18n;

  const formRef = useRef<HTMLFormElement>(null);
  const [token, setToken] = useState<string | null>(null);

  const onVerify = (newToken: string | null) => {
    setToken(newToken);
    if (newToken !== null) formRef.current?.submit();
  };

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss, classes }}
      headerNode={msg("securityCheckTitle")}
    >
      <form
        ref={formRef}
        id="kc-form-login"
        action={url.loginAction}
        method="post"
      >
        {captchaSiteKey && (
          <TurnstileWidget
            siteKey={captchaSiteKey}
            action={captchaAction}
            onVerify={onVerify}
          />
        )}
        <input
          type="hidden"
          name="credentialId"
          value={auth?.selectedCredential ?? ""}
        />
        <AuthActionButton
          loading={token !== null}
          disabled={token === null}
          text={msgStr("doContinue")}
        />
      </form>
    </Template>
  );
};

export default CfCaptcha;
