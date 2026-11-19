import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// En desarrollo, /api se redirige al backend Spring Boot local.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: process.env.VITE_BACKEND ?? "http://localhost:8080", changeOrigin: true },
    },
  },
  test: {
    environment: "node",
  },
});
