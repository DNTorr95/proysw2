/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Paleta institucional sobria (azul UAGRM aproximado)
        marca: {
          50: "#eef3fb",
          100: "#d6e1f4",
          200: "#adc3e8",
          500: "#2f5597",
          600: "#244682",
          700: "#1f3a68",
          800: "#172c4f",
          900: "#0f1d35",
        },
      },
    },
  },
  plugins: [],
};
