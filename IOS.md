# thinktwice para iPhone: requisitos (documento aparte, no implementado)

Estado: **solo documentación.** Esta entrega no contiene código iOS. Lo que sigue es una guía de requisitos basada en las APIs de Screen Time de Apple; conviene verificar los detalles actuales en la documentación oficial antes de empezar, porque Apple cambia requisitos y disponibilidad entre versiones de iOS.

## Diferencias de fondo respecto a Android
- iOS **no permite** que una app observe qué app está en primer plano ni superponga pantallas sobre otras. El control de otras apps solo es posible con el marco de **Screen Time (Family Controls)**, que usa «escudos» del sistema.
- Las apps se eligen con un selector del sistema (`FamilyActivityPicker`) que devuelve **tokens opacos**: la app no ve los nombres ni los identificadores de las apps elegidas (solo puede mostrarlas con vistas del sistema como `Label(token)`).
- El tiempo de uso detallado **no se puede leer** desde la app principal: solo se muestra mediante una extensión de informes (`DeviceActivityReport`) que se ejecuta en un entorno aislado y no devuelve datos a la app. Esto limita las «estadísticas propias» (historial local, resumen mensual, productividad por clasificación) respecto a la versión Android.
- La pantalla de pausa propia al abrir una app **no es posible tal cual**: lo que se muestra es el escudo del sistema, personalizable (título, subtítulo, iconos, botones) con una extensión.

## Capacidades y extensiones necesarias
| Pieza | Para qué |
|---|---|
| **Family Controls** (capability + autorización `AuthorizationCenter.shared.requestAuthorization(for: .individual)`) | Permiso para usar Screen Time sobre uno mismo. Requiere el *entitlement* `com.apple.developer.family-controls`, que en distribución (TestFlight/App Store) hay que **solicitar a Apple**; en desarrollo funciona con cuenta de desarrollador. |
| **Managed Settings** (`ManagedSettingsStore`) | Aplicar y quitar «escudos» sobre las apps/categorías elegidas (bloqueos temporales y rutinas). |
| **Device Activity** (`DeviceActivityMonitor` extension, `DeviceActivitySchedule`, `DeviceActivityCenter`) | Ejecutar horarios (rutinas, sesiones con fin programado) y umbrales de uso en segundo plano, aunque la app esté cerrada. Los horarios no cruzan bien intervalos mínimos muy cortos y tienen límites de número de actividades: comprobar límites vigentes. |
| **Shield Configuration** extension (`ShieldConfigurationDataSource`) | Diseñar la pantalla del escudo con el logo, mensaje y colores de thinktwice (dentro de lo que permite la plantilla del sistema). |
| **Shield Action** extension (`ShieldActionDelegate`) | Responder a los botones del escudo («Continuar», «Salir»); permite aplicar el patrón de «esperar y decidir» de forma aproximada (por ejemplo, retirar el escudo un intervalo y reponerlo con un `DeviceActivity` al terminar). La espera visible de 15 s dentro del escudo no es controlable como en Android. |
| **Device Activity Report** extension | Única vía para mostrar gráficas de uso reales. |
| **App Groups** | Compartir preferencias, reglas y registros entre la app y las extensiones (cada extensión es un proceso aparte con límites de memoria). |
| **Notificaciones locales** (`UserNotifications`) | Recordatorios de retos. |
| **Live Activities** (ActivityKit, iOS 16.1+) | Mostrar el tiempo restante de una sesión en pantalla de bloqueo. La **Dynamic Island** solo existe en iPhones que la incorporan (iPhone 14 Pro y posteriores) y solo se ofrece donde sea compatible; no se simula en otros modelos. |

## Equivalencias y recortes frente a Android
| Función | Android | iPhone |
|---|---|---|
| Pausa con espera propia | Pantalla propia encima de la app | Escudo del sistema personalizado; espera aproximada |
| Bloqueo temporal | Barrera de software evitable | Escudo del sistema (más difícil de saltar, pero el usuario puede revocar Screen Time en Ajustes) |
| Rutinas | Comprobación al abrir apps | `DeviceActivitySchedule` |
| Estadísticas reales por app | `UsageStatsManager` | Solo vía extensión de informes; sin historial propio |
| Clasificación útil/distractora | Por paquete | Por token opaco (sin nombres) |
| Retos medidos | Posible con eventos propios | Muy limitado; mayormente confirmación manual |

## Riesgos y revisión de App Store
- El *entitlement* de Family Controls requiere aprobación de Apple para distribuir.
- Descripción honesta: herramienta de bienestar digital; sin promesas de curación.
- El usuario siempre puede desactivar Screen Time o desinstalar; no se debe prometer lo contrario.
- No se recopilan datos ni hay servidor, lo que simplifica la ficha de privacidad.

## Orden de trabajo sugerido
1. Proyecto SwiftUI + capability Family Controls + autorización + `FamilyActivityPicker`.
2. `ManagedSettingsStore` con escudo manual (bloqueo temporal).
3. `DeviceActivityMonitor` para rutinas y fin de sesiones.
4. Shield Configuration y Shield Action con la identidad de thinktwice.
5. App Group + almacenamiento compartido.
6. Report extension y Live Activity.
