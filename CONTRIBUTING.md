# Contributing to Ni Botao

Gracias por tu interes en contribuir.

## Flujo recomendado

1. Haz un fork del repositorio.
2. Crea una rama por cambio:
   - `feat/nombre-corto`
   - `fix/nombre-corto`
   - `docs/nombre-corto`
3. Realiza commits atomicos y descriptivos.
4. Abre un Pull Request contra `main`.

## Convenciones

- Kotlin idiomatico y nombres claros.
- Evitar logica de negocio en capa de UI.
- Mantener separacion de capas: `data`, `domain`, `presentation`.
- Agregar o actualizar tests cuando cambie comportamiento.

## Checklist para Pull Request

- [ ] Compila en local con `./gradlew assembleDebug`
- [ ] Tests unitarios en verde con `./gradlew testDebugUnitTest`
- [ ] Sin secretos ni credenciales en codigo
- [ ] Descripcion clara del problema y solucion

## Reporte de bugs

Incluye:

- Version de Android
- Pasos para reproducir
- Resultado esperado vs resultado actual
- Capturas o logs relevantes

