import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

export const { useI18n, ofTypeI18n } = i18nBuilder
  .withThemeName<ThemeName>()
  .withCustomTranslations({
  en: {
    alphanumericalCharsOnly: "Only alphanumerical characters",
    gender: "Gender",
    // Here we overwrite the default english value for the message "doForgotPassword"
    // that is "Forgot Password?" see: https://github.com/InseeFrLab/keycloakify/blob/f0ae5ea908e0aa42391af323b6d5e2fd371af851/src/lib/i18n/generated_messages/18.0.1/login/en.ts#L17
    doForgotPassword: "Forgot your password?",
    invalidUserMessage: "Invalid username or password.",
    backToApplication: "Back to application",
    proceedWithAction: "Click here to proceed",
    doLogIn: "Log in",
    agreeToPrivacyPolicy: "By logging in, you agree to the",
    carpPrivacyPolicy: "CARP Privacy Policy",
    newToCarp: "New to CARP?",
    doRegister: "Sign up",
    byRegisteringYouAgree: "By registering, you agree to the",
    and: "and",
    termsOfService: "Terms of Service",
    loginBannerText: "Already have an account?",
    usernameRequired: "Username is required",
    emailRequired: "Email is required",
    emailOrUsernameRequired: "Username or email is required",
    firstNameRequired: "First name is required",
    lastNameRequired: "Last name is required",
    forgotPasswordInfo:
      "Enter your email address. If an account is found, a password reset link will be sent to your email.",
    passwordMinLength: "Password must be at least 8 characters long",
    passwordRequired: "Password is required",
    passwordsDontMatch: "Passwords don't match",
  },
  da: {
    agreeToPrivacyPolicy: "Når du logger ind, accepterer du",
    carpPrivacyPolicy: "CARP's privatlivspolitik",
    newToCarp: "Ny på CARP?",
    byRegisteringYouAgree: "Ved at registrere dig accepterer du ",
    and: "og",
    termsOfService: "servicevilkår",
    loginBannerText: "Har du allerede en konto?",
    alphanumericalCharsOnly: "Kun alfanumeriske tegn",
    gender: "Køn",
    doForgotPassword: "Glemt din adgangskode?",
    invalidUserMessage: "Ugyldigt brugernavn eller adgangskode",
    backToApplication: "Tilbage til ansøgning",
    proceedWithAction: "Klik her for at fortsætte",
    doLogIn: "Log ind",
    doRegister: "Tilmeld dig",
    usernameRequired: "Brugernavn er påkrævet",
    emailRequired: "Email er påkrævet",
    emailOrUsernameRequired: "Brugernavn eller e-mail er påkrævet",
    firstNameRequired: "Fornavn er påkrævet",
    lastNameRequired: "Efternavn er påkrævet",
    forgotPasswordInfo:
      "Indtast din e-mailadresse. Hvis en konto findes, sendes et link til nulstilling af adgangskode til din e-mail.",
    passwordMinLength: "Adgangskoden skal være mindst 8 tegn lang",
    passwordRequired: "Adgangskode er påkrævet",
    passwordsDontMatch: "Adgangskoderne matcher ikke",
  },
  })
  .build();

export type I18n = typeof ofTypeI18n;
