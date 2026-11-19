# Se construye desde la raíz del repositorio (contexto ".") para incluir nginx/nginx.conf.

# ---- Compilación del frontend ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- Nginx: sirve el frontend y hace de proxy hacia /api ----
FROM nginx:1.27-alpine
COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
