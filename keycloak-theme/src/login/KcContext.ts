import type { ExtendKcContext } from "keycloakify/login";
import type { PasswordPolicies } from "keycloakify/login/KcContext";
import type { KcEnvName, ThemeName } from "../kc.gen";

export type KcContextExtension = {
  themeName: ThemeName;
  properties: Record<KcEnvName, string> & {};
  message: "Welcome to CARP";
  // NOTE: Here you can declare more properties to extend the KcContext
  // See: https://docs.keycloakify.dev/faq-and-help/some-values-you-need-are-missing-from-in-kccontext
};

export type KcContextExtensionPerPage = {
  // Set by Phase Two's "Cloudflare Turnstile Username Password Form" (magic-links.jar).
  "login.ftl": {
    turnstileRequired?: boolean;
    turnstileSiteKey?: string;
    turnstileAction?: string;
  };
  // Rendered by Phase Two's standalone "Cloudflare Turnstile validation" step (magic-links.jar).
  // Declaring it here makes keycloakify generate the page in this theme.
  "cf-captcha.ftl": {
    captchaSiteKey?: string;
    captchaAction?: string;
    // Posted back like Phase Two's own template does, so the selected credential survives the step.
    auth?: { selectedCredential?: string };
  };
  // Keycloak sets passwordPolicies on every login page, but keycloakify only types it on some.
  "login-update-password.ftl": {
    passwordPolicies?: PasswordPolicies;
  };
};

export type KcContext = ExtendKcContext<
  KcContextExtension,
  KcContextExtensionPerPage
>;
