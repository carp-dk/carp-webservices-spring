import * as yup from "yup";
import type { PasswordPolicies } from "keycloakify/login/KcContext";
import type { I18n } from "./i18n";

// Character classes match Keycloak's policy providers, which count with
// Character.isDigit, isLowerCase, isUpperCase and !isLetterOrDigit.
const characterRules = [
  {
    policy: "digits",
    pattern: /\p{Nd}/gu,
    message: "invalidPasswordMinDigitsMessage",
  },
  {
    policy: "lowerCase",
    pattern: /\p{Ll}/gu,
    message: "invalidPasswordMinLowerCaseCharsMessage",
  },
  {
    policy: "upperCase",
    pattern: /\p{Lu}/gu,
    message: "invalidPasswordMinUpperCaseCharsMessage",
  },
  {
    policy: "specialChars",
    pattern: /[^\p{L}\p{Nd}]/gu,
    message: "invalidPasswordMinSpecialCharsMessage",
  },
] as const;

/**
 * Client-side checks for the realm's password policy, so users see failures before submitting.
 * Keycloak still enforces the full policy (history, username, email, blacklist, regex) on submit.
 */
export const getPasswordSchema = (
  policies: PasswordPolicies | undefined,
  msgStr: I18n["msgStr"],
) => {
  let schema = yup.string().required(msgStr("passwordRequired"));

  const { length, maxLength } = policies ?? {};
  if (length) {
    schema = schema.min(
      length,
      msgStr("invalidPasswordMinLengthMessage", `${length}`),
    );
  }
  if (maxLength) {
    schema = schema.max(
      maxLength,
      msgStr("invalidPasswordMaxLengthMessage", `${maxLength}`),
    );
  }

  for (const { policy, pattern, message } of characterRules) {
    const minimum = policies?.[policy];
    if (!minimum) continue;
    schema = schema.test(
      policy,
      msgStr(message, `${minimum}`),
      (value) => (value?.match(pattern)?.length ?? 0) >= minimum,
    );
  }

  return schema;
};
