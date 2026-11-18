/// <reference types="bun" />
import { describe, expect, test } from "bun:test";
import type { PasswordPolicies } from "keycloakify/login/KcContext";
import type { I18n } from "./i18n";
import { getPasswordSchema } from "./passwordSchema";

// Echoes the key and its arguments, so tests can check both the message and the realm's number.
const msgStr = ((key: string, ...args: string[]) =>
  `${key}:${args.join(",")}`) as unknown as I18n["msgStr"];

const errorFor = (policies: PasswordPolicies | undefined, password: string) => {
  try {
    getPasswordSchema(policies, msgStr).validateSync(password);
    return null;
  } catch (error) {
    return (error as Error).message;
  }
};

const realmPolicy: PasswordPolicies = {
  length: 10,
  maxLength: 20,
  digits: 2,
  lowerCase: 1,
  upperCase: 1,
  specialChars: 1,
};

describe("getPasswordSchema", () => {
  // Keycloak accepts any non-empty password when the realm has no policy,
  // so the form must not block passwords the server would accept.
  test("without a realm policy only requires a password", () => {
    expect(errorFor(undefined, "")).toBe("passwordRequired:");
    expect(errorFor({}, "a")).toBeNull();
  });

  // Each rule must reject with Keycloak's message and the realm's configured
  // value, so users see the same limit the server enforces.
  test.each([
    ["too short", "Ab1!5", "invalidPasswordMinLengthMessage:10"],
    [
      "too long",
      "Ab1!567890123456789012",
      "invalidPasswordMaxLengthMessage:20",
    ],
    ["too few digits", "Abcdefgh1!", "invalidPasswordMinDigitsMessage:2"],
    ["no lowercase", "ABCDEFG12!", "invalidPasswordMinLowerCaseCharsMessage:1"],
    ["no uppercase", "abcdefg12!", "invalidPasswordMinUpperCaseCharsMessage:1"],
    [
      "no special character",
      "Abcdefg123",
      "invalidPasswordMinSpecialCharsMessage:1",
    ],
  ])("rejects a password with %s", (_, password, message) => {
    expect(errorFor(realmPolicy, password)).toBe(message);
  });

  // Keycloak counts with Java's Character.isUpperCase / isLetterOrDigit, so
  // non-ASCII letters count as letters and a space counts as a special character.
  // An ASCII-only check would wrongly reject Danish passwords the server accepts.
  test("counts characters the same way as Keycloak", () => {
    expect(errorFor(realmPolicy, "Æbcdefg12!")).toBeNull();
    expect(errorFor(realmPolicy, "Øø12 abcdef")).toBeNull();
  });
});
