import { createTheme, PaletteOptions } from "@mui/material/styles";
import { createStyled } from "@mui/system";
import {
  opensans100,
  opensans300,
  opensans500,
  opensans700,
  opensans900,
} from "../assets/fonts";

declare module "@mui/material/styles" {
  interface TypeText {
    primary: string;
    secondary: string;
    heading: string;
    disabled: string;
  }

  interface Palette {
    company: {
      isotype: string;
      logotype: string;
    };
    status: {
      live: string;
      ready: string;
      draft: string;
    };
    text: {
      primary: string;
      secondary: string;
      heading: string;
      disabled: string;
    };
    drawer: {
      active: string;
    };
  }
  interface PaletteOptions {
    company: {
      isotype: string;
      logotype: string;
    };
    status: {
      live: string;
      ready: string;
      draft: string;
    };
    drawer: {
      active: string;
    };
  }
}

declare module "@mui/material/Typography" {
  interface TypographyPropsVariantOverrides {
    h1_web: true;
    h2_web: true;
    h3_web: true;
    h4_web: true;
    h5_web: true;
  }
}

declare module "@mui/material/styles" {
  interface TypographyVariants {
    h1_web: React.CSSProperties;
    h2_web: React.CSSProperties;
    h3_web: React.CSSProperties;
    h4_web: React.CSSProperties;
    h5_web: React.CSSProperties;
  }

  interface TypographyVariantsOptions {
    h1_web?: React.CSSProperties;
    h2_web?: React.CSSProperties;
    h3_web?: React.CSSProperties;
    h4_web?: React.CSSProperties;
    h5_web?: React.CSSProperties;
  }
}

export const customPalette = {
  primary: {
    main: "#006398",
    dark: "#003352",
    light: "#CDE5FF",
  },
  secondary: {
    main: "#5E7F97",
    dark: "#233240",
    light: "#D6E8F4",
  },
  error: {
    main: "#BA1A1A",
  },
  info: {
    main: "#5E7F97",
  },
  grey: {
    100: "#FCFCFF",
    200: "#E3E3E4",
    300: "#AAABAE",
    500: "#76777A",
    700: "#45474A",
    900: "#1A1C1E",
  },
  text: {
    heading: "#001D32",
    primary: "#233240",
    secondary: "#374953",
    disabled: "#979799",
  },
  background: {
    default: "#FCFCFF",
  },
  success: {
    main: "#00A300",
  },
  warning: {
    main: "#EE9926",
  },
  company: {
    isotype: "#D61D41",
    logotype: "#006398",
  },
  status: {
    purple: "#67587A",
    yellow: "#EE9926",
    green: "#00A300",
    red: "#BA1A1A",
    grey: "#76777A",
  },
  drawer: {
    active: "#E7F2FF",
  },
};

export const themeInstance = createTheme({
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          fontSize: "0.875rem",
          lineHeight: "1.25rem",
          fontWeight: 300,
        },
        "@font-face": opensans100,
        fallbacks: [
          {
            "@font-face": opensans300,
          },
          {
            "@font-face": opensans500,
          },
          {
            "@font-face": opensans700,
          },
          {
            "@font-face": opensans900,
          },
        ],
      },
    },
    MuiListItemText: {
      styleOverrides: {
        root: {
          color: "white",
        },
      },
    },
    MuiLink: {
      defaultProps: {
        underline: "hover",
      },
    },
    MuiBackdrop: {
      styleOverrides: {
        root: {
          // backgroundColor: '#00000080',
        },
      },
    },
    MuiFormLabel: {
      styleOverrides: {
        root: {
          fontSize: "1rem",
          lineHeight: "1.5rem",
          fontWeight: 700,
          marginTop: 26,
          marginBottom: 8,
          display: "block",
          "&.Mui-disabled": {
            opacity: "0.5",
          },
        },
      },
    },
    MuiFormControlLabel: {
      styleOverrides: {
        root: {
          "& > .MuiFormControlLabel-label": {
            fontWeight: 300,
          },
        },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: {
          marginTop: "inherit",
          marginBottom: "inherit",
          display: "inherit",
          fontWeight: 300,
        },
      },
    },
    MuiFormHelperText: {
      styleOverrides: {
        root: {
          marginLeft: 2,
        },
      },
    },
    MuiInputBase: {
      styleOverrides: {
        root: {
          "&.MuiButtonBase-root": {
            backgroundColor: customPalette.grey[100],
          },
          "& fieldset": {
            borderRadius: 8,
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderColor: customPalette.grey[700],
          backgroundColor: "#fff",
        },
      },
    },
    MuiTypography: {
      defaultProps: {
        variantMapping: {
          h1_web: "h1",
          h2_web: "h2",
          h3_web: "h3",
          h4_web: "h4",
          h5_web: "h5",
        },
      },
    },
  },
  typography: {
    fontFamily: "Open Sans",
    h1: {
      fontSize: "2.8125rem",
      lineHeight: "3.25rem",
      fontWeight: 700,
    },
    h2: {
      fontSize: "1.5rem",
      lineHeight: "2rem",
      fontWeight: 700,
    },
    h3: {
      fontSize: "1.375rem",
      lineHeight: "1.75rem",
      fontWeight: 700,
    },
    h4: {
      fontSize: "1rem",
      lineHeight: "1.5rem",
      fontWeight: 700,
    },
    h5: {
      fontSize: "0.875rem",
      lineHeight: "1.25rem",
      fontWeight: 700,
    },
    h6: {
      fontSize: "0.75rem",
      lineHeight: "1rem",
      fontWeight: 700,
      letterSpacing: "0.5px",
    },
    body2: {
      fontSize: "0.875rem",
      lineHeight: "1.25rem",
      fontWeight: 700,
    },
    button: {
      fontSize: "0.875rem",
      lineHeight: "1.25rem",
      fontWeight: 700,
    },
    h1_web: {
      fontSize: "2.8125rem",
      lineHeight: "3.25rem",
      fontWeight: 700,
    },
    h2_web: {
      fontSize: "1.375rem",
      lineHeight: "1.75rem",
      fontWeight: 700,
    },
    h3_web: {
      fontSize: "1.375rem",
      lineHeight: "1.75rem",
      fontWeight: 300,
    },
    h4_web: {
      fontSize: "1rem",
      lineHeight: "1.75rem",
      fontWeight: 300,
    },
    h5_web: {
      fontSize: "1rem",
      lineHeight: "1.5rem",
      fontWeight: 300,
    },
  },
  transitions: {
    duration: {
      leavingScreen: 500,
    },
  },
  palette: customPalette as unknown as PaletteOptions,
  breakpoints: {
    values: {
      xs: 400,
      sm: 600,
      md: 960,
      lg: 1280,
      xl: 1920,
    },
  },
});

export const styled = createStyled({ defaultTheme: themeInstance });
