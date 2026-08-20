# Security Policy

## Versiones soportadas

Mientras no se definan ramas de mantenimiento, solo se considera soportada la rama principal (`main`).

## Reportar vulnerabilidades

Por favor, no publiques vulnerabilidades en issues publicos.

1. Abre un canal privado con los mantenedores del repositorio.
2. Describe impacto, pasos de reproduccion y posibles mitigaciones.
3. Espera confirmacion antes de divulgar detalles publicamente.

## Buenas practicas de seguridad

- No subir llaves, keystores ni tokens al repositorio.
- Usar `GitHub Secrets` para credenciales de CI/CD.
- Revisar permisos sensibles (ubicacion, Bluetooth, foreground service) antes de cada release.
- Mantener dependencias al dia con Dependabot.

