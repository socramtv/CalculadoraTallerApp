# Calculadora Taller

App Android nativa (Kotlin) que envuelve tu "Calculadora Taller" (el
`Perfiles.html` que ya tenías, el mismo que sirve `index.html` en
`socramtv/perfiles_app`) en una sola pantalla con una `WebView` a pantalla
completa. La calculadora en sí **no se ha tocado**: sigue siendo el mismo
HTML/CSS/JS de siempre, con sus mismas funciones y su mismo diseño.

Lo único que añade esta app nativa es lo que un navegador normal ya te
daba gratis pero una `WebView` no, para que nada de lo que ya hacía la
calculadora se quede a medias dentro de la app:

- **Guardar el JSON de ajustes** (exportar ajustes) y **los PDF** que genera
  jsPDF (ángulos de corte, presupuesto, reparto) → se guardan de verdad en
  la carpeta **Descargas** del teléfono, con un aviso ("Guardado en
  Descargas: ...") y el PDF se abre solo al terminar si tienes algún lector
  de PDF instalado.
- **Importar ajustes** (el botón que abre el selector de archivo `.json`) →
  abre el selector de archivos normal de Android.
- **"Eliminar trabajo"** (el `confirm()` antes de borrar) → muestra el
  diálogo nativo de Android, no se queda callado.
- **"Compartir por WhatsApp"** (nivel, reparto, presupuesto...) → abre
  WhatsApp si lo tienes instalado, o el navegador si no.
- **Generar PDF sin conexión**: las dos librerías que usa la calculadora
  (jsPDF y jspdf-autotable) se empaquetan dentro del APK (ver más abajo),
  así que esa parte funciona aunque el teléfono no tenga datos/wifi en ese
  momento. Las tipografías (Google Fonts) sí se siguen cargando por
  internet como en la web; si no hay conexión, se usa la fuente de
  reserva que ya llevaba el CSS — no rompe nada, solo cambia la letra.

## Icono

Ya viene listo: usa tal cual tu `icon-512.png` del repositorio
`socramtv/perfiles_app` (`app/src/main/res/drawable/ic_launcher_app.png`).
No hace falta que hagas nada con el icono.

## Compilar sin PC (GitHub Actions)

Igual que con Socram TV+: el proyecto incluye
`.github/workflows/build-apk.yml`, que compila el APK en la nube (no hace
falta Android Studio ni un PC) y además, como primer paso, **descarga las
dos librerías de PDF** (jsPDF y jspdf-autotable) dentro del propio proyecto
antes de compilar — por eso no hace falta que las lleves tú en el zip.

Pasos desde el móvil (Termux):

1. Crea un repositorio nuevo en GitHub, por ejemplo `CalculadoraTallerApp`
   (público o privado). No lo mezcles con `perfiles_app`: ese repo es la
   web/PWA; este es el proyecto Android, van por separado.
2. Si no lo tienes ya: instala **Termux** (desde F-Droid) y dentro
   `pkg install git`.
3. Extrae este zip en el almacenamiento del teléfono y entra a esa carpeta
   desde Termux (`termux-setup-storage` si hace falta acceso a la
   memoria).
4. Genera un token en GitHub (*Settings > Developer settings > Personal
   access tokens*, permiso "repo") si no tienes uno ya de antes.
5. Dentro de la carpeta del proyecto:
   ```
   git init
   git add .
   git commit -m "primer commit"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/CalculadoraTallerApp.git
   git push -u origin main
   ```
   Usuario = tu usuario de GitHub; contraseña = el token.
6. Pestaña **Actions** del repo: el workflow arranca solo. Al terminar,
   entra al run y descarga el artifact `calculadora-taller-debug-apk` — el
   `.apk` está dentro, listo para instalar (activa "instalar apps de
   origen desconocido" para el navegador o la app de Archivos).

Es un APK de **debug** (firma automática de depuración de Gradle): vale
para instalarlo en tu propio teléfono sin más.

## Si vuelves a editar la calculadora

Si en el futuro reemplazas `app/src/main/assets/www/index.html` por una
versión nueva de tu calculadora (copiando y pegando el HTML actualizado),
acuérdate de mantener estas tres líneas dentro de `<head>` (están justo
antes de `</head>` en la versión actual), o dejarán de funcionar el PDF
sin conexión, el `confirm()`, "compartir por WhatsApp", etc.:

```html
<script src="vendor/jspdf.umd.min.js"></script>
<script src="vendor/jspdf.plugin.autotable.min.js"></script>
<script src="native-bridge.js"></script>
```

`native-bridge.js` (el fichero que va al lado de `index.html`) tampoco hace
falta tocarlo: no depende de nada del contenido de la calculadora, solo
escucha los enlaces de descarga que ya crea jsPDF/el propio HTML.

## Estructura

```
app/src/main/
  assets/www/index.html        Tu calculadora, tal cual, + las 3 líneas de arriba
  assets/www/native-bridge.js  Puente descargas/blob -> guardado nativo
  assets/www/vendor/           jsPDF + autotable (los añade el workflow al compilar)
  java/.../MainActivity.kt     WebView + puente nativo (descargas, selector de
                                archivo, diálogos, WhatsApp)
```

## Limitaciones conocidas

- En Android 8/9 (API 26-28) los archivos se guardan en la carpeta propia
  de la app (`Android/data/com.socramtv.calctaller/files/Download`) en vez
  de en la Descargas pública, para no tener que pedir permisos de
  almacenamiento; en Android 10 en adelante (lo normal en 2026) van
  directos a la Descargas de siempre. En ambos casos el aviso "Guardado
  en Descargas: ..." y el abrir-el-PDF-solo funcionan igual.
- La app es una sola pantalla (toda la calculadora vive en ese único
  `index.html`), así que el botón atrás de Android simplemente cierra la
  app.
- Es debug, no release: si algún día quieres repartirla fuera de tu propio
  teléfono, hace falta configurar firma de release aparte.
