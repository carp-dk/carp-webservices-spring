import { Box, Typography } from "@mui/material";
import { Turnstile } from "react-turnstile";

type Props = {
  siteKey: string;
  action?: string;
  error?: string;
  onVerify: (token: string | null) => void;
};

// Must render inside the form: Turnstile adds the hidden cf-turnstile-response field
// that Phase Two's Turnstile authenticators read on submit.
const TurnstileWidget = ({ siteKey, action, error, onVerify }: Props) => (
  <Box
    sx={{
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      my: 2,
    }}
  >
    <Turnstile
      sitekey={siteKey}
      action={action}
      // The CARP theme is light only; "auto" would follow the OS and show a dark widget.
      theme="light"
      refreshExpired="auto"
      onVerify={(token) => onVerify(token)}
      onExpire={() => onVerify(null)}
      onError={() => onVerify(null)}
    />
    {error && (
      <Typography color="error" variant="caption" role="alert">
        {error}
      </Typography>
    )}
  </Box>
);

export default TurnstileWidget;
