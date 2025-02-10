import { CircularProgress } from "@mui/material";
import CarpButton from "./styles";

type Props = {
  text: string;
  loading: boolean;
};

const AuthActionButton = ({ text, loading }: Props) => {
  return (
    <CarpButton
      disableElevation
      variant="contained"
      disabled={loading}
      type="submit"
    >
      {loading ? <CircularProgress size={24} /> : text}
    </CarpButton>
  );
};

export default AuthActionButton;
