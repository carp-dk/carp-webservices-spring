import { InputLabelProps } from "@mui/material";
import * as React from "react";

import { useState } from "react";
import StyledInput from "./styles";

interface FormikConfigProps {
  [key: string]: string;
}

interface Props {
  id: string;
  value: string;
  type: string;
  name: keyof FormikConfigProps;
  label: string;
  placeholder?: string;
  autoComplete?: string;
  rows?: number;
  variant?: "standard" | "filled" | "outlined";
  inputLabelProps?: InputLabelProps;
  disabled?: boolean;
  required?: boolean;
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onBlur: (event: React.FocusEvent<HTMLInputElement>) => void;
}

const CarpInputNoFormik = ({
  id,
  value,
  type,
  name,
  label,
  placeholder,
  autoComplete,
  rows,
  variant,
  disabled = false,
  required = false,
  onChange,
  onBlur,
  inputLabelProps,
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
      id={id || (name as string)}
      placeholder={placeholder}
      value={value}
      onChange={onChange}
      onBlur={onBlur}
      fullWidth
      type={type}
      variant={variant || "standard"}
      autoComplete={autoComplete}
      rows={rows}
      multiline={!!rows}
      disabled={disabled}
      slotProps={{
        htmlInput: {
          "data-testid": rows ? "single-line-input" : "multi-line-input",
          onAnimationStart: handleAnimationStart,
        },
        inputLabel: {
          shrink: isAutoFilled || name !== "",
          ...inputLabelProps,
        },
      }}
      required={required}
    />
  );
};

export default CarpInputNoFormik;
