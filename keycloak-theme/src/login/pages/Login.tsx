import { useState, type FormEventHandler } from "react";
import { useConstCallback } from "keycloakify/tools/useConstCallback";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import {
  Checkbox,
  FormControlLabel,
  FormGroup,
  IconButton,
  InputAdornment,
} from "@mui/material";
import * as yup from "yup";
import { useFormik } from "formik";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import CarpInput from "../../components/CarpInput";
import { AuthInfoText } from "../../components/Layout/PublicPageLayout/AuthPageLayout/styles";
import StyledLink from "../../components/StyledLink";
import LoginOauthOption from "../../components/Buttons/OauthOptions";
import AuthActionButton from "../../components/Buttons/AuthActionButton";
import AppleLogo from "../../assets/images/logo-apple.png";
import GoogleLogo from "../../assets/images/logo-google.png";
import PasskeyLogo from "../../assets/images/logo-passkey.png";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import BannerRegister from "../../components/Layout/PublicPageLayout/BannerRegister";
import {
  LoginAdditionalActions,
  LoginOauthOptions,
  LoginSeparator,
  LoginSeparatorText,
} from "./styles";
import { QrCodeScannerOutlined } from "@mui/icons-material";
import QRScannerModal from "../../components/QRScannerModal";
import TurnstileWidget from "../../components/TurnstileWidget";

// The "Or log in with" section (QR code, Apple, Passkey, Google) is hidden until those
// options work. Set to true to show it again.
const SHOW_OTHER_LOGIN_OPTIONS = false;

const Login = (
  props: PageProps<Extract<KcContext, { pageId: "login.ftl" }>, I18n>,
) => {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const {
    social,
    realm,
    url,
    usernameHidden,
    login,
    registrationDisabled,
    messagesPerField,
    turnstileRequired,
    turnstileSiteKey,
    turnstileAction,
  } = kcContext;

  const { msgStr } = i18n;

  const [isLoading, setIsLoading] = useState(false);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);

  const onSubmit = useConstCallback<FormEventHandler<HTMLFormElement>>((e) => {
    e.preventDefault();
    setIsLoading(true);
    const formElement = e.target as HTMLFormElement;

    // NOTE: Even if we login with email Keycloak expect username and password in
    // the POST request.
    formElement
      .querySelector("input[name='email']")
      ?.setAttribute("name", "username");
    formElement.submit();
  });

  const validationSchemaWithEmail = yup.object({
    username: yup
      .string()
      .email(msgStr("invalidEmailMessage"))
      .required(msgStr("emailRequired")),
    password: yup.string().required(msgStr("passwordRequired")),
  });

  const validationSchemaWithString = yup.object({
    username: yup
      .string()
      .required(
        realm.loginWithEmailAllowed
          ? msgStr("emailOrUsernameRequired")
          : msgStr("usernameRequired"),
      ),
    password: yup.string().required(msgStr("passwordRequired")),
  });

  const formik = useFormik({
    initialValues: {
      username: login.username ?? "",
      password: login.password ?? "",
    },
    validationSchema: realm.registrationEmailAsUsername
      ? validationSchemaWithEmail
      : validationSchemaWithString,
    onSubmit: () => {},
  });

  const [staySignedIn, setStaySignedIn] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const toggleSignedIn = () => setStaySignedIn(!staySignedIn);

  const [open, setOpen] = useState(false);

  const usernameLabel = () => {
    if (!realm.loginWithEmailAllowed) {
      return "username";
    }
    if (realm.registrationEmailAsUsername) {
      return "email";
    }
    return "usernameOrEmail";
  };

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss, classes }}
      displayInfo={social.displayInfo}
      headerNode={msgStr("doLogIn")}
      infoNode={
        realm.registrationAllowed &&
        !registrationDisabled && (
          <BannerRegister registerUrl={url.registrationUrl} msgStr={msgStr} />
        )
      }
      key={"login-" + (realm.password ? "password" : "no-password")}
    >
      <QRScannerModal
        open={open}
        onClose={() => setOpen(false)}
      ></QRScannerModal>
      {realm.password && (
        <form
          id="kc-form-login"
          onSubmit={onSubmit}
          action={url.loginAction}
          method="post"
        >
          {!usernameHidden &&
            (() => {
              const label = usernameLabel();

              const autoCompleteHelper: typeof label =
                label === "usernameOrEmail" ? "username" : label;
              const type = label === "usernameOrEmail" ? "text" : label;

              return (
                <CarpInput
                  name="username"
                  label={msgStr(label)}
                  type={type}
                  formikConfig={formik}
                  autoComplete={autoCompleteHelper}
                  variant="outlined"
                />
              );
            })()}

          <CarpInput
            name="password"
            label={msgStr("password")}
            type={showPassword ? "text" : "password"}
            formikConfig={formik}
            autoComplete="current-password section-blue"
            variant="outlined"
            InputProp={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label="toggle password visibility"
                    onClick={() => setShowPassword(!showPassword)}
                    edge="end"
                  >
                    {showPassword ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
          <LoginAdditionalActions>
            {realm.rememberMe && !usernameHidden && (
              <FormGroup>
                <FormControlLabel
                  control={
                    <Checkbox
                      // eslint-disable-next-line react/jsx-props-no-spreading
                      {...(login.rememberMe
                        ? {
                            checked: true,
                          }
                        : {})}
                      onChange={toggleSignedIn}
                      name="rememberMe"
                      slotProps={{ input: { "aria-label": "controlled" } }}
                    />
                  }
                  label={msgStr("rememberMe")}
                />
              </FormGroup>
            )}
            {realm.resetPasswordAllowed && (
              <AuthInfoText variant="h4_web">
                <StyledLink to={url.loginResetCredentialsUrl}>
                  {msgStr("doForgotPassword")}
                </StyledLink>
              </AuthInfoText>
            )}
          </LoginAdditionalActions>
          {turnstileRequired && turnstileSiteKey && (
            <TurnstileWidget
              siteKey={turnstileSiteKey}
              action={turnstileAction}
              error={
                messagesPerField.existsError("cf-turnstile-response")
                  ? messagesPerField.get("cf-turnstile-response")
                  : undefined
              }
              onVerify={setCaptchaToken}
            />
          )}
          <AuthActionButton
            loading={isLoading}
            disabled={turnstileRequired && !captchaToken}
            text={msgStr("doLogIn")}
          />
          <AuthInfoText variant="h4_web" hideOnMobile>
            {msgStr("agreeToPrivacyPolicy")}{" "}
            <StyledLink to="https://carp.dk/privacy-policy-service/">
              {msgStr("carpPrivacyPolicy")}
            </StyledLink>
            .
          </AuthInfoText>
        </form>
      )}
      {SHOW_OTHER_LOGIN_OPTIONS && (
        <>
          <LoginSeparator>
            <LoginSeparatorText variant="h4_web" component="span">
              Or log in with
            </LoginSeparatorText>
          </LoginSeparator>
          <LoginOauthOptions>
            <LoginOauthOption
              logoComponent={QrCodeScannerOutlined}
              name="QR Code"
              onClick={() => {
                setOpen(true);
              }}
            />
            {social.providers !== undefined && (
              <>
                <LoginOauthOption logoSrc={AppleLogo} name="Apple" />
                <LoginOauthOption logoSrc={PasskeyLogo} name="Passkey" />
                <LoginOauthOption logoSrc={GoogleLogo} name="Google" />
              </>
            )}
          </LoginOauthOptions>
        </>
      )}
    </Template>
  );
};

export default Login;
