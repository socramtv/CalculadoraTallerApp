// Puente para que las descargas (JSON de ajustes, PDFs generados con
// jsPDF) funcionen dentro de la app Android igual que en un navegador
// normal. No toca nada de la calculadora. Si esto se abre en un navegador
// normal (no en la app), no hace nada.
(function () {
    // Compatibilidad jsPDF: la build UMD siempre deja window.jspdf.jsPDF,
    // pero parte de la calculadora usa "new jsPDF(...)" a secas (sin
    // "jspdf."). Nos aseguramos de que exista también esa forma, da igual
    // el orden de carga ni la build exacta. No hace nada si ya existe.
    if (window.jspdf && window.jspdf.jsPDF && !window.jsPDF) {
        window.jsPDF = window.jspdf.jsPDF;
    }

    if (!window.AndroidDownloader) return;

    function handleBlobDownload(url, filename) {
        fetch(url)
            .then(function (r) { return r.blob(); })
            .then(function (blob) {
                var reader = new FileReader();
                reader.onloadend = function () {
                    var dataUrl = reader.result || '';
                    var comma = dataUrl.indexOf(',');
                    var base64 = comma >= 0 ? dataUrl.slice(comma + 1) : '';
                    var mime = blob.type || 'application/octet-stream';
                    try {
                        AndroidDownloader.saveBase64File(base64, filename, mime);
                    } catch (err) {
                        console.error('AndroidDownloader falló', err);
                    }
                };
                reader.onerror = function (err) {
                    console.error('No se pudo leer el blob a descargar', err);
                };
                reader.readAsDataURL(blob);
            })
            .catch(function (err) {
                console.error('No se pudo obtener el blob a descargar', err);
            });
    }

    // Camino principal: tanto jsPDF (doc.save(...)) como exportarAjustes()
    // crean el <a download> y llaman directamente a su .click() SIN
    // insertarlo antes en la página (nunca hacen
    // document.body.appendChild(a)). Un elemento que no está en la página
    // no dispara eventos que lleguen a document.addEventListener('click',
    // ...) -- por eso antes no pasaba nada: hay que interceptarlo aquí, en
    // el propio método .click(), no solo escuchando clics a nivel de
    // documento.
    var originalClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        if (this.hasAttribute('download') && this.href && this.href.indexOf('blob:') === 0) {
            handleBlobDownload(this.href, this.getAttribute('download') || ('archivo-' + Date.now()));
            return;
        }
        return originalClick.apply(this, arguments);
    };

    // Red adicional, por si algún enlace <a download> sí llegara a estar
    // insertado en la página y se tocara directamente.
    document.addEventListener('click', function (ev) {
        var a = ev.target && ev.target.closest ? ev.target.closest('a[download]') : null;
        if (!a || !a.href || a.href.indexOf('blob:') !== 0) return;
        ev.preventDefault();
        handleBlobDownload(a.href, a.getAttribute('download') || ('archivo-' + Date.now()));
    }, true);
})();
