package com.socramtv.calctaller

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Envoltorio nativo de "Calculadora Taller": una única WebView cargando el
 * HTML de la calculadora tal cual (app/src/main/assets/www/index.html).
 *
 * Todo lo que hace esta clase es resolver las pocas cosas que un navegador
 * normal da gratis pero un WebView no, sin tocar la lógica de la
 * calculadora:
 *  - localStorage (ajustes, trabajos, tema)            -> domStorageEnabled
 *  - confirm() al borrar un trabajo                    -> onJsConfirm
 *  - <input type=file> para importar ajustes JSON       -> onShowFileChooser
 *  - descargar el JSON de ajustes y los PDF (jsPDF)      -> AndroidDownloader
 *    (ver native-bridge.js: intercepta los <a download> de tipo blob:)
 *  - "Compartir por WhatsApp" (window.open a wa.me/api)  -> onCreateWindow
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileCallback
            pendingFileCallback = null
            if (callback == null) return@registerForActivityResult
            val data = result.data
            val uri = if (result.resultCode == Activity.RESULT_OK) data?.data else null
            callback.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15/16 dibuja el contenido por debajo de la barra de
        // estado y la de gestos por defecto (borde a borde, obligatorio a
        // partir de targetSdk 35). Se configura ANTES de montar la
        // pantalla (si no, la primera pasada de layout ya se hace "a la
        // antigua" y luego no se recalcula sola).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        setContentView(webView)

        // Metemos como padding lo que ocupen esas barras para que la
        // página entera se vea completa, en cualquier versión de Android.
        // requestApplyInsets fuerza un primer aviso ya con estos listeners
        // puestos: sin él, a veces se pierde el aviso inicial (llega antes
        // de registrarnos) y nunca se llega a aplicar el margen.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(webView)

        configureWebView(webView)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        webView.loadUrl("file:///android_asset/www/index.html")
    }

    private fun configureWebView(webView: WebView) {
        val s = webView.settings
        s.setJavaScriptEnabled(true)
        s.setDomStorageEnabled(true)
        s.setUseWideViewPort(true)
        s.setLoadWithOverviewMode(true)
        s.setSupportMultipleWindows(true)
        s.setJavaScriptCanOpenWindowsAutomatically(true)
        s.setTextZoom(100)

        webView.addJavascriptInterface(AndroidDownloader(), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Toda la app vive en el único fichero local index.html.
                // Cualquier navegación a http(s) (no debería darse salvo
                // que se añada algún enlace nuevo en el futuro) se manda
                // fuera, al navegador del sistema, en vez de cargarla aquí.
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") {
                    openExternal(request.url)
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                // window.open(...) -- lo usa la calculadora para "Compartir
                // por WhatsApp" -- no abre una ventana real dentro de la
                // app: se captura la URL en una WebView desechable y se
                // reenvía como Intent normal (WhatsApp si está instalado,
                // si no el navegador), igual que haría un navegador.
                val transportWebView = WebView(view.context)
                transportWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        openExternal(request.url)
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = transportWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onJsAlert(
                view: WebView,
                url: String,
                message: String,
                result: JsResult
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView,
                url: String,
                message: String,
                result: JsResult
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String,
                message: String,
                defaultValue: String?,
                result: JsPromptResult
            ): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // Aviso visible (no solo en Logcat, al que no tienes acceso
                // fácil desde el móvil) de cualquier error de JavaScript de
                // la página, para poder ver exactamente qué falla en vez de
                // adivinar. Si resulta molesto más adelante, se puede
                // quitar o dejar solo el Log.
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error en la página: ${consoleMessage.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Log.d(
                    "CalculadoraTaller",
                    "console (${consoleMessage.messageLevel()}): ${consoleMessage.message()}" +
                        " [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]"
                )
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // La calculadora no pide cámara/micro/etc.; se deniega
                // cualquier permiso web por defecto, por seguridad.
                request.deny()
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                pendingFileCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/json", "text/plain", "text/json")
                    )
                }
                return try {
                    getContentLauncher.launch(Intent.createChooser(intent, "Elegir archivo"))
                    true
                } catch (e: Exception) {
                    pendingFileCallback = null
                    false
                }
            }
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró una app para abrir ese enlace", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Puente JS -> nativo: recibe (desde native-bridge.js) el contenido en
     * base64 de un Blob que la web quería "descargar" (JSON de ajustes o
     * un PDF de jsPDF) y lo guarda de verdad en el dispositivo.
     */
    inner class AndroidDownloader {
        @JavascriptInterface
        fun saveBase64File(base64: String, filename: String, mime: String) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val uri = writeToDownloads(filename, mime, bytes)
                    Toast.makeText(
                        this@MainActivity,
                        "Guardado en Descargas: $filename",
                        Toast.LENGTH_LONG
                    ).show()
                    if (mime == "application/pdf" && uri != null) {
                        openSavedFile(uri, mime)
                    }
                } catch (e: Exception) {
                    Log.e("CalculadoraTaller", "Error guardando $filename", e)
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo guardar $filename",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openSavedFile(uri: Uri, mime: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // No hay ninguna app que abra PDFs instalada: no pasa nada,
            // el archivo ya ha quedado guardado en Descargas.
        }
    }

    /**
     * Guarda [bytes] como [filename] en la carpeta pública de Descargas.
     *
     * En Android 10+ (API 29+, la inmensa mayoría de dispositivos hoy) se
     * usa MediaStore: no hace falta pedir ningún permiso.
     *
     * En Android 8/9 (API 26-28) se guarda en el almacenamiento propio de
     * la app (Android/data/com.socramtv.calctaller/files/Download) para
     * evitar tener que pedir el permiso WRITE_EXTERNAL_STORAGE; ahí es
     * algo menos visible desde la app "Archivos", pero sigue abriéndose
     * bien desde el aviso ("Guardado en Descargas") que muestra la propia
     * app al terminar.
     */
    private fun writeToDownloads(filename: String, mime: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(bytes) }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
    }
}
