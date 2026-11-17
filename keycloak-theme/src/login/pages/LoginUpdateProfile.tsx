import { useConstCallback } from "keycloakify/tools/useConstCallback";
import { useState, type FormEventHandler } from "react";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { clsx } from "keycloakify/tools/clsx";
import * as yup from "yup";
import { useFormik } from "formik";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";
import CarpInput from "../../components/CarpInput";
import AuthActionButton from "../../components/Buttons/AuthActionButton";

const LoginUpdateProfile = (
  props: PageProps<
    Extract<KcContext, { pageId: "login-update-profile.ftl" }>,
    I18n
  >,
) => {
  const [isLoading, setIsLoading] = useState(false);
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

  const { kcClsx } = getKcClsx({
    doUseDefaultCss,
    classes,
  });

  const { msg, msgStr } = i18n;

  const { url, profile, isAppInitiatedAction } = kcContext;

  const validationSchemaUsername = yup.object({
    username: yup.string().required(msgStr("usernameRequired")),
    email: yup
      .string()
      .email(msgStr("invalidEmailMessage"))
      .required(msgStr("emailRequired")),
    firstName: yup.string().required(msgStr("firstNameRequired")),
    lastName: yup.string().required(msgStr("lastNameRequired")),
  });

  const validationSchemaNoUsername = yup.object({
    email: yup
      .string()
      .email(msgStr("invalidEmailMessage"))
      .required(msgStr("emailRequired")),
    firstName: yup.string().required(msgStr("firstNameRequired")),
    lastName: yup.string().required(msgStr("lastNameRequired")),
  });

  const formik = useFormik({
    initialValues: {
      username:
        typeof kcContext.profile.attributesByName.username === "string"
          ? kcContext.profile.attributesByName.username
          : "",
      firstName:
        typeof kcContext.profile.attributesByName.firstName === "string"
          ? kcContext.profile.attributesByName.firstName
          : "",
      lastName:
        typeof kcContext.profile.attributesByName.lastName === "string"
          ? kcContext.profile.attributesByName.lastName
          : "",
      email:
        typeof kcContext.profile.attributesByName.email === "string"
          ? kcContext.profile.attributesByName.email
          : "",
    },
    validationSchema: profile.attributesByName.editUsernameAllowed
      ? validationSchemaUsername
      : validationSchemaNoUsername,
    onSubmit: () => {},
  });

  const onSubmit = useConstCallback<FormEventHandler<HTMLFormElement>>((e) => {
    e.preventDefault();
    setIsLoading(true);
    const formElement = e.target as HTMLFormElement;
    formElement.submit();
    setIsLoading(false);
  });

  return (
    <Template
      {...{ kcContext, i18n, doUseDefaultCss, classes }}
      headerNode={msg("loginProfileTitle")}
    >
      <form
        id="kc-update-profile-form"
        className={kcClsx("kcFormClass")}
        onSubmit={onSubmit}
        action={url.loginAction}
        method="post"
      >
        {profile.attributesByName.editUsernameAllowed && (
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
          name="email"
          label={msgStr("email")}
          type="email"
          formikConfig={formik}
          autoComplete="email"
          variant="outlined"
        />

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

        <div className={kcClsx("kcFormGroupClass")}>
          <div id="kc-form-options" className={kcClsx("kcFormOptionsClass")}>
            <div className={kcClsx("kcFormOptionsWrapperClass")} />
          </div>

          <div id="kc-form-buttons" className={kcClsx("kcFormButtonsClass")}>
            {isAppInitiatedAction ? (
              <>
                <input
                  className={clsx(
                    kcClsx("kcButtonClass"),
                    kcClsx("kcButtonPrimaryClass"),
                    kcClsx("kcButtonLargeClass"),
                  )}
                  type="submit"
                  defaultValue={msgStr("doSubmit")}
                />
                <button
                  className={clsx(
                    kcClsx("kcButtonClass"),
                    kcClsx("kcButtonDefaultClass"),
                    kcClsx("kcButtonLargeClass"),
                  )}
                  type="submit"
                  name="cancel-aia"
                  value="true"
                >
                  {msg("doCancel")}
                </button>
              </>
            ) : (
              <div
                id="kc-form-buttons"
                className={kcClsx("kcFormButtonsClass")}
              >
                <AuthActionButton
                  text={msgStr("doSubmit")}
                  loading={isLoading}
                />
              </div>
            )}
          </div>
        </div>
      </form>
    </Template>
  );
};

export default LoginUpdateProfile;
