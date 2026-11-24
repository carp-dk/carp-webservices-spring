import { InputLabelProps, InputProps } from "@mui/material";
import * as React from "react";

import { FormikProps } from "formik";
import { useState } from "react";
import StyledInput from "./styles";

interface FormikConfigProps {
  [key: string]: string;
}

interface Props {
  type: string;
  name: keyof FormikConfigProps;
  label: string;
  placeholder?: string;
  autoComplete?: string;
  formikConfig: FormikProps<FormikConfigProps>;
  rows?: number;
  variant?: "standard" | "filled" | "outlined";
  inputLabelProps?: InputLabelProps;
  InputProp?: Partial<InputProps>;
}

const CarpInput = ({
  type,
  name,
  label,
  placeholder,
  autoComplete,
  formikConfig,
  rows,
  variant,
  inputLabelProps,
  InputProp,
}: Props) => {
  const [isAutoFilled, setIsAutoFilled] = useState(false);

  const handleAnimationStart = (
    event: React.AnimationEvent<HTMLInputElement>,
  ) => {
    if (event.animationName === "mui-auto-fill") {
      setIsAutoFilled(true);
      const input = document.getElementById("myTextField");
      if (input) input.dispatchEvent(new Event("blur"));
    }
  };
  return (
    <StyledInput
      name={name as string}
      label={label}
      id={name as string}
      placeholder={placeholder}
      error={formikConfig.touched[name] && Boolean(formikConfig.errors[name])}
      value={formikConfig.values[name]}
      onChange={formikConfig.handleChange}
      onBlur={formikConfig.handleBlur}
      fullWidth
      helperText={formikConfig.touched[name] && formikConfig.errors[name]}
      type={type}
      variant={variant || "standard"}
      autoComplete={autoComplete}
      rows={rows}
      multiline={!!rows}
      slotProps={{
        htmlInput: {
          "data-testid": rows ? "single-line-input" : "multi-line-input",
          onAnimationStart: handleAnimationStart,
        },
        inputLabel: {
          shrink: isAutoFilled || formikConfig.values[name] !== "",
          ...inputLabelProps,
        },
        input: InputProp,
      }}
    />
  );
};

export default CarpInput;
