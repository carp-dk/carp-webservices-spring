import { CircularProgress } from "@mui/material";
import CarpButton from "./styles";

type Props = {
  text: string;
  loading: boolean;
  disabled?: boolean;
};

const AuthActionButton = ({ text, loading, disabled = false }: Props) => {
  return (
    <CarpButton
      disableElevation
      variant="contained"
      disabled={loading || disabled}
      type="submit"
    >
      {loading ? <CircularProgress size={24} /> : text}
    </CarpButton>
  );
};

export default AuthActionButton;
