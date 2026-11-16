# thinktwice

App Android nativa (Kotlin + Jetpack Compose) para reducir el uso impulsivo del móvil con **pausas conscientes**, **bloqueos temporales**, **rutinas**, **estadísticas** y **retos**. Todo funciona en el propio teléfono: sin cuentas, sin servidores, sin IA, sin anuncios y **sin permiso de Internet** (el manifiesto no declara `INTERNET`, así que la app no puede conectarse a ninguna red).

> thinktwice ayuda a decidir con calma si abrir una app. No cura ninguna adicción ni promete porcentajes de mejora.

## Estado real de esta entrega (léelo primero)

| | |
|---|---|
| Es una app real, no una demo visual | Sí: interfaz, almacenamiento, lógica de pausas/sesiones/rutinas/retos, estadísticas (API `UsageStatsManager`) y servicio de intervención están implementados. |
| **¿Se ha compilado el APK?** | **No, no en este entorno.** El entorno donde se escribió no tiene Android SDK ni acceso a Google Maven. El primer `assembleDebug` lo harás tú (ver abajo); es posible que Android Studio señale algún error de compilación menor que no he podido detectar. |
| ¿Se ha probado en un móvil? | **No.** Nada de lo que depende de Android real (servicio de accesibilidad, abrir la pausa encima de otra app, permisos, reinicio…) está verificado en dispositivo. Ver «Pruebas». |
| ¿Se ha probado algo? | Sí: la lógica pura (horarios, medianoche, solapes, zonas horarias, cambio de hora, salida de emergencia, retos) con **20 pruebas unitarias que pasan** en JVM. |
| Comparación visual con UnRespiro | **No he podido ver sus capturas.** La estética sigue tu descripción y tu paleta (`ui/Theme.kt`, objeto `TT`); los valores son de partida. Compara en tu móvil y ajusta ahí. |
| Logo | Se usa **tu imagen tal cual** (`res/drawable-nodpi/logo_thinktwice.png`, idéntica píxel a píxel a la adjunta). Solo cambia su tamaño. El icono de la app, el icono monocromo de notificación y el de «icono temático» se derivan de esa misma imagen (recorte/escala, y en el monocromo el brillo del logo como transparencia). |

## Instalar en tu móvil

Requisitos: Android 8.0 (API 26) o superior. Recomendado Android 10+.

### Opción A: Android Studio (la más fácil)
1. Instala [Android Studio](https://developer.android.com/studio) (última versión estable).
2. `File > Open…` y elige esta carpeta (`thinktwice/`). Deja que sincronice Gradle (necesita Internet **solo para compilar**, no para usar la app). Si pide instalar «Android SDK Platform 35», acepta.
3. En el móvil: `Ajustes > Acerca del teléfono` → toca 7 veces «Número de compilación» → `Ajustes > Sistema > Opciones de desarrollador` → activa **Depuración por USB**.
4. Conecta el móvil por USB, acepta la huella RSA que aparece en el móvil y pulsa ▶ **Run**.

### Opción B: línea de comandos
```bash
# Con ANDROID_HOME apuntando al SDK (o un local.properties con sdk.dir=...)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
También puedes copiar `app-debug.apk` al móvil y abrirlo (habrá que permitir «instalar apps desconocidas» para el gestor de archivos).

### Android 13+: «Ajustes restringidos»
Si instalas el APK a mano (no desde una tienda), Android bloquea por defecto conceder el servicio de accesibilidad. Antes de activarlo: `Ajustes > Aplicaciones > thinktwice > ⋮ (arriba a la derecha) > Permitir ajustes restringidos`. Instalando desde Android Studio/adb normalmente no hace falta. La app lo avisa en la primera apertura.

### Tests
```bash
./gradlew testDebugUnitTest      # LogicTest (20) + BackupTest (6, necesitan org.json real: ya está como dependencia de test)
```

## Permisos y para qué sirven (se explican antes de pedirlos)

| Permiso | Para qué | Si lo rechazas |
|---|---|---|
| **Acceso a estadísticas de uso** (`PACKAGE_USAGE_STATS`, ajuste especial) | Leer cuánto tiempo pasa cada app en primer plano. **No bloquea nada.** | No hay anillo ni estadísticas de uso; el resto funciona. |
| **Servicio de accesibilidad** (`BIND_ACCESSIBILITY_SERVICE`) | Saber qué app pasa a primer plano para mostrar encima la pausa o el aviso de bloqueo. | **No hay pausas ni bloqueos** (la app avisa con claridad y no presenta una sesión como protegida). Estadísticas, rutinas (como organización) y retos siguen. |
| Notificaciones (`POST_NOTIFICATIONS`, Android 13+) | Cuenta atrás de la sesión activa y recordatorios opcionales. | No verás la notificación; el bloqueo no depende de ella. |
| Mostrar sobre otras apps (`SYSTEM_ALERT_WINDOW`, **opcional**) | Refuerzo para móviles que impiden abrir la pausa desde segundo plano (ver «Limitaciones»). | Nada, salvo en esos móviles. |
| Arranque (`RECEIVE_BOOT_COMPLETED`) | Reprogramar el recordatorio opcional y re-mostrar notificaciones tras reiniciar. | — |
| Excluir del ahorro de batería (opcional, no declarado) | Solo si tu fabricante mata el servicio. | — |

El servicio de accesibilidad se declara con `canRetrieveWindowContent="false"` y solo escucha `typeWindowStateChanged`: **no puede leer texto, mensajes ni contenido de otras apps**, solo el nombre del paquete que pasa a primer plano. Declara `isAccessibilityTool="false"`: thinktwice **no** es una herramienta de accesibilidad para personas con discapacidad. Si algún día se publica en Google Play, el uso de `AccessibilityService` exige declaración en Play Console y es una política muy estricta; en esta entrega se distribuye por APK.

## Mecanismo de intervención y sus límites

1. `GuardService` (servicio de accesibilidad) recibe «la ventana X pasó a primer plano».
2. Consulta el estado local (`Logic.activeRestrictions` + pausa de apps controladas).
3. Si procede, abre `InterventionActivity` (una actividad normal, tarea propia, fuera de recientes) encima de la app. «Salir sin entrar» usa la acción global *Inicio*.

Esto es una **barrera de software, no un bloqueo del sistema**:
- El usuario puede desactivar el servicio en Ajustes de Android, desinstalar la app o usar la salida de emergencia. **thinktwice no promete impedir la desinstalación** en un móvil personal ni restringe los controles para revocar permisos. La pantalla de bloqueo lo dice textualmente.
- No se restringen nunca: la propia app, Ajustes, SystemUI, teléfono/marcador, emergencias ni el instalador de paquetes (`Apps.isEssential`). Tampoco se ofrecen en los selectores.
- **No detecta el scroll.** Solo usa «qué app está delante» y el tiempo.
- Android 10+ limita abrir actividades desde segundo plano. Un servicio de accesibilidad suele poder, pero **depende de versión y fabricante**: es lo primero que hay que verificar en tu móvil. Si en el tuyo la pausa no aparece encima, concede «Mostrar sobre otras apps» (Ajustes > Permisos y diagnóstico), que exime de esa restricción.
- Android 15/16 y las capas de los fabricantes (Xiaomi, Huawei, Samsung, OPPO…) pueden matar servicios en segundo plano. El diagnóstico de Ajustes distingue «habilitado» de «en ejecución».

### Reglas exactas de la pausa
- Se muestra al pasar una app **controlada** a primer plano si no tiene un acceso vigente. Por defecto 15 s de espera visible (anillo + cuenta atrás) y 5 min de acceso. Ambos configurables.
- **Continuar** (solo tras la espera) concede acceso hasta `ahora + intervalo` (marca de tiempo guardada). **Salir** (o el botón Atrás) manda a Inicio, no concede acceso: la próxima apertura vuelve a empezar la espera completa.
- Si sales y vuelves a entrar **dentro del intervalo**, no se repite la pausa. Al terminar el intervalo: si sigues dentro, se reaplica justo al terminar; si no, en la próxima apertura.
- Sin duplicados ni bucles: solo una intervención visible a la vez y 1,5 s de antirrebote por app.
- Un bloqueo (sesión o rutina) tiene prioridad sobre la pausa. Un bloqueo muestra el **motivo** y la **hora de fin**.
- Mensajes: 6 originales incluidos + los tuyos, guardados localmente, elegidos al azar. Sin lenguaje culpabilizador.

### Sesiones, rutinas y solapes
- Las sesiones guardan `inicio`/`fin` en milisegundos de reloj; el estado se calcula siempre comparando con la hora actual, no con un contador. Sobreviven a cerrar la app y a reiniciar el móvil.
- Las rutinas guardan hora de inicio, fin y días **en que empiezan**. Si `fin ≤ inicio` cruzan la medianoche (22:30–07:00 empezando el lunes sigue activa el martes a las 02:00). Se calculan con hora local de pared: un cambio de zona horaria o de hora (verano/invierno) se refleja en la siguiente comprobación sin reprogramar nada.
- **Solapes**: cada app queda restringida hasta el final de la restricción que termine más tarde. Terminar una sesión no libera una app que otra rutina sigue restringiendo.
- **Salida de emergencia** (con confirmación): termina la sesión, o suspende solo esa ocurrencia de la rutina (la siguiente noche vuelve a aplicarse). Queda registrada y no cuenta como sesión completada en los retos.
- Las rutinas **no usan alarmas exactas**: se comprueban al abrir una app vigilada y, mientras una está delante, cada 30 s. Así que una rutina que empieza con la app ya abierta puede tardar hasta ~30 s en aplicarse. No hay sondeo continuo (cuida la batería).
- Limitación conocida: usa el reloj del sistema. Si cambias la hora manualmente hacia delante, una sesión terminará antes; hacia atrás, durará más.

### Estadísticas
- Tiempo por app reconstruido con `UsageEvents` (reanudar/pausar). Android **solo conserva los eventos detallados unos días**, por eso al cerrar un día se guarda un resumen local (`daySnaps`) para el resumen mensual.
- Sin permiso o sin datos: estado vacío explícito. Nunca cifras inventadas, y el tiempo de bloqueo **no** se convierte en «tiempo recuperado».
- Clasificación útil/distractora/neutra **definida por ti** (toca la etiqueta de cada app). El anillo reparte el uso en verde (útil) / rojo rosado (distractora) / gris (neutra); además cada categoría lleva símbolo y texto, no solo color.
- Productividad = útil ÷ (útil + distractora); las neutras no cuentan; sin base suficiente, «Sin datos suficientes». Se explica en pantalla.
- Comparación mensual con **periodos equivalentes** (días 1…n de este mes frente a 1…n del anterior, solo días con datos guardados), indicando el periodo de referencia.
- Compartir resumen: hoja de compartir de Android, sin subir nada.

### Retos (24 h, 48 h, 7, 21, 30 días)
Reglas visibles: cumplir una rutina, completar una sesión diaria o respetar un objetivo de uso. El calendario distingue **cumplido (medido)**, **cumplido (confirmado a mano)**, **no cumplido (medido)**, **pendiente** y **sin datos suficientes**. Recordatorio diario opcional con alarma inexacta (Android puede retrasarlo). No se afirma que un hábito se forme en 21 días.

## Datos y privacidad
- Un único JSON en el almacenamiento privado de la app, escrito de forma atómica. Copias de seguridad en la nube desactivadas (`allowBackup=false`).
- Exportar/importar: el import valida el formato y la versión, recorta valores fuera de rango y **pide confirmación** (muestra qué contiene) antes de reemplazar. Si el archivo local se corrompe, se guarda una copia `.corrupt.json` y se arranca limpio.
- Eliminar todos los datos desde Ajustes. Los permisos del sistema se gestionan siempre desde Ajustes de Android.
- Tipografía Inter incluida en la app (funciona sin conexión). Licencia en `docs/Inter-LICENSE.txt`.

## Pruebas

### Realizadas (entorno de desarrollo, sin móvil)
- `LogicTest`: 20 pruebas que **pasan** (compiladas y ejecutadas con Kotlin 2.0.21 + JUnit 4.13.2 sobre `Model.kt` y `Logic.kt`): ventanas del mismo día (fin exclusivo), cruce de medianoche y día de inicio, instante de fin exacto, solapes de rutinas, sesión que termina con rutina vigente, salida de emergencia (sesión y ocurrencia de rutina), cambio de zona horaria, noche del cambio de hora de otoño (10 h), rutinas desactivadas/sin días, próximo inicio, caducidad de accesos, productividad, duración de retos y estados medido/manual/sin datos/pendiente.
- Revisión del manifiesto: sin `INTERNET`; servicio sin lectura de contenido.

### Escritas pero **no ejecutadas aquí**
- `BackupTest` (6): ida y vuelta del JSON, rechazo de archivos ajenos/versión desconocida/truncados, recorte de valores. Necesita `org.json` real; se ejecuta con `./gradlew testDebugUnitTest`.

### **Pendientes en dispositivo real** (lista para hacerlas)
1. Compila y se instala; no se cierra al abrir.
2. Navegación: Inicio ↔ Rutinas, botón «+» abre el panel inferior con desenfoque y al cerrarlo conserva la pantalla; botón Atrás.
3. **Pausa**: controla una app, ábrela → aparece la pantalla encima, espera visible, Continuar tras 15 s, Salir. Sal y vuelve dentro de 5 min: no debe repetirse. Espera 5 min dentro: debe reaparecer. ← *prueba clave del mecanismo*
4. **Bloqueo**: sesión de 15 min con una app, ábrela → motivo y hora de fin; salida de emergencia con confirmación; cuenta atrás en la notificación.
5. Cierra la app desde recientes con una sesión activa: el bloqueo y el tiempo restante se mantienen.
6. Reinicia el móvil con una sesión activa: sigue vigente y el servicio se reconecta.
7. Rutina que cruza la medianoche (p. ej. 23:50–00:10 hoy) y dos rutinas solapadas con una app común: al acabar la primera no se libera.
8. Cambia la zona horaria y la hora del sistema; revisa sesiones y rutinas.
9. Revoca el servicio de accesibilidad y el acceso a estadísticas: Inicio y Ajustes lo señalan, la sesión no se presenta como protegida.
10. Estadísticas con permiso (hoy, ayer, semana) y sin permiso (estado vacío).
11. Modo avión (todo debe funcionar igual), tamaño de fuente grande del sistema, TalkBack, giro de pantalla.
12. Exportar → eliminar datos → importar.
13. Consumo de batería tras un día de uso (no hay sondeo continuo, pero no está medido).

## Estructura del proyecto
```
app/src/main/
  AndroidManifest.xml
  java/com/thinktwice/app/
    Model.kt          modelos + (de)serialización JSON validada
    Logic.kt          lógica pura: ventanas, medianoche, solapes, retos (testeada)
    Store.kt          almacenamiento local atómico + registro de eventos
    System.kt         permisos, apps, UsageStats, notificaciones, arranque
    GuardService.kt   servicio de accesibilidad (intervención)
    InterventionActivity.kt  pantalla de pausa y de bloqueo
    MainActivity.kt   navegación (Rutinas · Inicio · «+») y barra inferior
    ui/               Theme (colores TT, tipografía Inter -85), Common, Home, Routines (+retos),
                      Stats, BlockSheet (panel inferior), Onboarding, Settings
  res/  logo exacto, iconos derivados, fuentes Inter, tema, config del servicio
app/src/test/         LogicTest, BackupTest
docs/IOS.md           requisitos para una futura versión iPhone (documento aparte)
```

## Ajustar la estética
Colores en `ui/Theme.kt` (`object TT`): fondo `#080909`, grafito `#222525`, verde `#80D7A0`, rojo rosado `#EB536F`; son tus aproximaciones. Tipografía Inter con interletreado −0,085 em (el «−85» de tu especificación) en títulos y nombre. Barra inferior simple, sin cápsula líquida ni gestos; panel inferior oscuro con tirador y desenfoque del fondo (el desenfoque requiere Android 12+; en versiones anteriores el panel se muestra sin él).

## Fuera de alcance de esta versión
Sin Dynamic Island simulada (Android no la tiene); la sesión se muestra con una notificación del sistema. Sin intento de impedir la desinstalación. iPhone: ver `docs/IOS.md`.
