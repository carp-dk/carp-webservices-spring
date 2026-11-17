// ejected using 'npx eject-keycloak-page'
import { useState, type FormEventHandler } from "react";
import { useConstCallback } from "keycloakify/tools/useConstCallback";
import { useFormik } from "formik";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import * as yup from "yup";
import AuthActionButton from "../../components/Buttons/AuthActionButton";
import CarpInput from "../../components/CarpInput";
import { AuthInfoText } from "../../components/Layout/PublicPageLayout/AuthPageLayout/styles";
import BannerLogin from "../../components/Layout/PublicPageLayout/BannerLogin";
import StyledLink from "../../components/StyledLink";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";

const Register = (
  props: PageProps<Extract<KcContext, { pageId: "register.ftl" }>, I18n>,
) => {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { kcClsx } = getKcClsx({
    doUseDefaultCss,
    classes,
  });

  const [isLoading, setIsLoading] = useState(false);

  const onSubmit = useConstCallback<FormEventHandler<HTMLFormElement>>((e) => {
    e.preventDefault();
    setIsLoading(true);
    const formElement = e.target as HTMLFormElement;
    formElement.submit();
  });

  const { url, passwordRequired, recaptchaRequired, recaptchaSiteKey, realm } =
    kcContext;

  const { msg, msgStr } = i18n;

  const validationSchemaNoUsername = yup.object({
    firstName: yup.string().required(msgStr("firstNameRequired")),
    lastName: yup.string().required(msgStr("lastNameRequired")),
    email: yup
      .string()
      .email(msgStr("invalidEmailMessage"))
      .required(msgStr("emailRequired")),
    password: yup
      .string()
      .min(8, msgStr("passwordMinLength"))
      .required(msgStr("passwordRequired")),
    "password-confirm": yup
      .string()
      .min(8, msgStr("passwordMinLength"))
      .required(msgStr("passwordRequired"))
      .oneOf([yup.ref("password"), null], msgStr("passwordsDontMatch")),
  });

  const validationSchemaWithUsername = yup.object({
    username: yup.string().required(msgStr("usernameRequired")),
    firstName: yup.string().required(msgStr("firstNameRequired")),
    lastName: yup.string().required(msgStr("lastNameRequired")),
    email: yup
      .string()
      .email(msgStr("invalidEmailMessage"))
      .required(msgStr("emailRequired")),
    password: yup
      .string()
      .min(8, msgStr("passwordMinLength"))
      .required(msgStr("passwordRequired")),
    "password-confirm": yup
      .string()
      .min(8, msgStr("passwordMinLength"))
      .required(msgStr("passwordRequired"))
      .oneOf([yup.ref("password"), null], msgStr("passwordsDontMatch")),
  });

  const formik = useFormik({
    initialValues: {
      username: "",
      firstName: "",
      lastName: "",
      email: "",
      password: "",
      "password-confirm": "",
    },
    validationSchema: realm.registrationEmailAsUsername
      ? validationSchemaNoUsername
      : validationSchemaWithUsername,
    onSubmit: () => {},
  });

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss, classes }}
      headerNode={msg("registerTitle")}
      infoNode={<BannerLogin loginUrl={url.loginUrl} msgStr={msgStr} />}
    >
      <form
        id="kc-register-form"
        action={url.registrationAction}
        method="post"
        onSubmit={onSubmit}
      >
        {!realm.registrationEmailAsUsername && (
          <CarpInput
            name="username"
            label={msgStr("username")}
            type="text"
            formikConfig={formik}
            autoComplete="username"
            variant="outlined"
          />
        )}
        <CarpInput
          name="firstName"
          label={msgStr("firstName")}
          type="text"
          formikConfig={formik}
          autoComplete="given-name"
          variant="outlined"
        />
        <CarpInput
          name="lastName"
          label={msgStr("lastName")}
          type="text"
          formikConfig={formik}
          autoComplete="family-name"
          variant="outlined"
        />
        <CarpInput
          name="email"
          label={msgStr("email")}
          type="text"
          formikConfig={formik}
          autoComplete="email"
          variant="outlined"
        />
        {passwordRequired && (
          <>
            <CarpInput
              name="password"
              label={msgStr("password")}
              type="password"
              formikConfig={formik}
              autoComplete="new-password"
              variant="outlined"
            />
            <CarpInput
              name="password-confirm"
              label={msgStr("passwordConfirm")}
              type="password"
              formikConfig={formik}
              autoComplete="new-password"
              variant="outlined"
            />
          </>
        )}
        {recaptchaRequired && (
          <div className="form-group">
            <div className={kcClsx("kcInputWrapperClass")}>
              <div
                className="g-recaptcha"
                data-size="compact"
                data-sitekey={recaptchaSiteKey}
              />
            </div>
          </div>
        )}

        <AuthActionButton text={msgStr("doRegister")} loading={isLoading} />
        <AuthInfoText variant="h4_web" hideOnMobile>
          {msgStr("byRegisteringYouAgree")}{" "}
          <StyledLink to="https://carp.dk/privacy-policy-service/">
            {msgStr("carpPrivacyPolicy")}
          </StyledLink>{" "}
          {msgStr("and")}{" "}
          <StyledLink to="/forgot-password">
            {msgStr("termsOfService")}
          </StyledLink>
          .
        </AuthInfoText>
      </form>
    </Template>
  );
};

export default Register;
