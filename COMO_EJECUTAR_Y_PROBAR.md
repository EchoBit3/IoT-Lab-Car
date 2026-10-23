# Cómo ejecutar y probar IoT Lab Car (paso a paso)

Esta guía explica cómo compilar, instalar y probar la app en un emulador o dispositivo físico, y cómo verificar sus funciones principales.

## 1. Requisitos

- Android Studio (recomendado: versión reciente con soporte para AGP 9.x).
- JDK 11 o superior.
- Android SDK con `compileSdk 36`.
- Un emulador Android (API 24+) o un dispositivo físico con Android 7.0+.
- (Opcional) Un segundo teléfono en la misma red Wi-Fi para probar el modo simulador.
- (Opcional) Un ESP32 / ESP32-CAM / Arduino con firmware compatible para control real.

## 2. Clonar / abrir el proyecto

1. Abre Android Studio.
2. Selecciona **File → Open** y elige la carpeta `iot-lab-car`.
3. Espera a que Gradle sincronice las dependencias (puede tardar la primera vez).
4. Si Android Studio sugiere "fix" de incompatibilidades, acéptalas.

## 3. Configurar el entorno

1. En la raíz del proyecto, crea un archivo `.env` (puedes copiar `.env.example`).
2. Si usas la API de Gemini, descomenta y completa:
   ```
   GEMINI_API_KEY=TU_CLAVE
   ```
   Si no la usas, puedes dejarla comentada; la app sigue funcionando en modo simulador.
3. En `app/build.gradle.kts`, si vas a compilar en debug local, **elimina** la línea:
   ```
   signingConfig = signingConfigs.getByName("debugConfig")
   ```
   (solo si Android Studio lo requiere; normalmente no es necesario para debug).

## 4. Compilar

1. En Android Studio: **Build → Make Project** (o `Ctrl+F9`).
2. Verifica que no haya errores de compilación.
3. Si hay errores de SDK, instala la plataforma Android 36 desde el SDK Manager.

## 5. Ejecutar en emulador o dispositivo

1. Selecciona un dispositivo/emulador en la barra superior.
2. Pulsa **Run** (▶) o `Shift+F10`.
3. La app se instalará y abrirá automáticamente.
4. Acepta los permisos cuando aparezcan (Red, Ubicación, Notificaciones, Vibración).

## 6. Probar las funciones (checklist)

### A. Acceso y seguridad
- [ ] Abre la app: aparece la pantalla de login del operador.
- [ ] Inicia sesión con el perfil de operador.
- [ ] Verifica el badge "Almacenamiento Cifrado con Clave Maestra (ISO 27400)".
- [ ] Cierra y vuelve a abrir la app: la sesión debe persistir (o limpiarse según configuración).
- [ ] Abre "Privacidad y cumplimiento" y prueba "Eliminar cuenta y datos".

### B. Permisos educativos
- [ ] Si los permisos no están concedidos, aparece el modal explicativo.
- [ ] Pulsa "Ahora no": la app sigue funcionando en modo simulador sin crashear.
- [ ] Pulsa "Conceder": aparece el popup nativo de Android.
- [ ] Deniega un permiso: la app degrada funciones amigablemente.

### C. Conectividad Wi-Fi
- [ ] Con Wi-Fi apagado: la app muestra estado desconectado (`wifioff.jpeg`).
- [ ] Activa Wi-Fi: la app detecta la red y muestra IP/puerto (`wifiON.jpeg`).
- [ ] Pulsa el botón para abrir ajustes de Wi-Fi: se abre el panel del sistema.

### D. Rol didáctico
- [ ] Selecciona **Estación de Mando**: se muestran controles de conducción.
- [ ] Selecciona **Nodo Sensor (Vehículo Virtual)**: el teléfono emite telemetría simulada.
- [ ] Con dos teléfonos en la misma red, conecta uno como Estación y otro como Nodo: verifica telemetría en vivo.

### E. Visor FPV y sensores
- [ ] Abre el visor de cámara: se muestran líneas guía de trayectoria (`CamaraOn.jpeg` / `camaraoff.jpeg`).
- [ ] Verifica las lecturas FL (frontal izquierdo) y FR (frontal derecho) en cm.
- [ ] Acerca un objeto a menos de 25 cm: la alerta debe ponerse roja y vibrar.
- [ ] A entre 25 y 60 cm: alerta amarilla.

### F. Controles de conducción
- [ ] Pulsa **Adelante**: el vehículo avanza; al soltar, se detiene (dead-man switch).
- [ ] Pulsa **Atrás**: retrocede; al soltar, se detiene.
- [ ] Prueba **Izquierda / Centro / Derecha**.
- [ ] Cambia la velocidad (30% / 65% / 100%).
- [ ] Pulsa **E-STOP**: parada de emergencia inmediata (`frenoON.jpeg`).

### G. Robustez y diagnóstico
- [ ] Abre la pestaña de diagnóstico.
- [ ] Pulsa "Simular Desconexión Forzada": la app no debe crashear y debe intentar reconectar.
- [ ] Observa el monitor de tramas JSON en vivo.
- [ ] Rota la pantalla: la sesión y la conexión no deben perderse.
- [ ] Verifica que el watchdog detenga el vehículo si no hay comandos (≈1.5 s).

## 7. Pruebas automatizadas

Desde Android Studio o terminal con Gradle:

```bash
./gradlew test            # Pruebas unitarias (Robolectric)
./gradlew connectedAndroidTest  # Pruebas instrumentadas (requiere dispositivo/emulador)
```

Archivos de prueba incluidos:
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

## 8. Solución de problemas

- **Gradle no sincroniza:** verifica conexión a internet y versión de JDK (11+).
- **SDK 36 no encontrado:** instálalo desde SDK Manager.
- **La app no conecta al vehículo:** confirma que ambos dispositivos estén en la misma red Wi-Fi y que la IP/puerto coincidan (`192.168.4.1:8088` por defecto).
- **Cámara sin imagen:** verifica la URL del stream (`http://IP:81/stream`) y que el ESP32-CAM esté encendido.
- **Permisos denegados:** ve a Ajustes del sistema → Apps → IoT Lab Car → Permisos y actívalos.

## 9. Notas de seguridad

- No se guardan credenciales en texto plano.
- Las IPs se validan contra redes privadas (RFC 1918) para evitar conexiones a hosts externos.
- El *watchdog* detiene el vehículo automáticamente si se pierde la conexión.