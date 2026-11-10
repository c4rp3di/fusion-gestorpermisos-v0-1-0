package com.vigia.permisos

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.vigia.permisos.adb.ModoAvanzadoActivity
import com.vigia.permisos.adb.ShellManager
import com.vigia.permisos.core.ActividadBase
import com.vigia.permisos.core.Sistema
import com.vigia.permisos.core.Tema
import com.vigia.permisos.permisos.AppConPermiso
import com.vigia.permisos.permisos.CategoriaPermiso
import com.vigia.permisos.permisos.Categorias
import com.vigia.permisos.permisos.EscanerPermisos
import com.vigia.permisos.permisos.ResultadoRevocar
import com.vigia.permisos.permisos.Revocador
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Las apps que tienen concedido un permiso (una categoría), con «Revocar» en cada una y «Revocar todos» arriba.
 * Con el Modo Avanzado conectado se quita con `pm revoke` (vía ShellManager); sin él, «Revocar» abre los ajustes de la app.
 */
class CategoriaActivity : ActividadBase() {

    companion object {
        const val EXTRA_CATEGORIA = "categoria"
    }

    private lateinit var categoria: CategoriaPermiso
    private lateinit var contador: TextView
    private lateinit var avisoShell: TextView
    private lateinit var btnModo: MaterialButton
    private lateinit var btnTodos: MaterialButton
    private lateinit var panelProgreso: View
    private lateinit var textoProgreso: TextView
    private lateinit var barra: LinearProgressIndicator
    private lateinit var textoVacio: TextView
    private lateinit var adaptador: AppsAdapter

    private var apps: List<AppConPermiso> = emptyList()
    private var trabajo: Job? = null
    private var masivoActivo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cat = Categorias.porId(intent.getStringExtra(EXTRA_CATEGORIA))
        if (cat == null) {
            finish()
            return
        }
        categoria = cat
        setContentView(R.layout.activity_categoria)
        prepararPantalla(R.string.ayuda_categoria_titulo, R.string.ayuda_categoria)

        findViewById<TextView>(R.id.tituloCategoria).text = cat.icono + "  " + cat.titulo
        findViewById<TextView>(R.id.descCategoria).text = cat.descripcion
        contador = findViewById(R.id.contador)
        avisoShell = findViewById(R.id.avisoShell)
        btnModo = findViewById(R.id.btnModoAvanzado)
        btnTodos = findViewById(R.id.btnRevocarTodos)
        panelProgreso = findViewById(R.id.panelProgreso)
        textoProgreso = findViewById(R.id.textoProgreso)
        barra = findViewById(R.id.barraProgreso)
        textoVacio = findViewById(R.id.textoVacio)

        // «Revocar todos» es la acción destructiva de la pantalla: en rojo, como aviso.
        val sinEstadoActivo = intArrayOf(-android.R.attr.state_enabled)
        btnTodos.backgroundTintList = ColorStateList(arrayOf(sinEstadoActivo, intArrayOf()), intArrayOf(0x33FFFFFF, Tema.rojo(this)))
        btnTodos.setTextColor(ColorStateList(arrayOf(sinEstadoActivo, intArrayOf()), intArrayOf(0x61FFFFFF, Color.WHITE)))

        adaptador = AppsAdapter(cat, { revocar(it) }, { abrirFicha(it) })
        val lista = findViewById<RecyclerView>(R.id.listaApps)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adaptador

        btnTodos.setOnClickListener { revocarTodos() }
        btnModo.setOnClickListener { startActivity(Intent(this, ModoAvanzadoActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { trabajo?.cancel() }

        // Si ya hay un escaneo reciente, se pinta al instante y luego se refresca.
        val previo = EscanerPermisos.ultimo
        if (previo != null) apps = previo.porCategoria[cat.id].orEmpty()
        pintar()
    }

    override fun onResume() {
        super.onResume()
        if (!::categoria.isInitialized) return
        pintarEstadoShell(ShellManager.estado())
        lifecycleScope.launch {
            pintarEstadoShell(ShellManager.verificar())
        }
        // Mientras corre «Revocar todos» no se vuelve a leer la lista: la gestiona el propio bucle.
        if (trabajo?.isActive != true) cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            try {
                apps = EscanerPermisos.escanear(applicationContext).porCategoria[categoria.id].orEmpty()
            } catch (e: Exception) {
                aviso(getString(R.string.error_escaneo, e.message ?: e.javaClass.simpleName))
            }
            pintar()
        }
    }

    private fun pintar() {
        contador.text = Categorias.frase(categoria, apps.size)
        adaptador.poner(apps)
        textoVacio.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        btnTodos.isEnabled = apps.isNotEmpty() && !masivoActivo
    }

    private fun pintarEstadoShell(e: ShellManager.Estado) {
        when (e) {
            ShellManager.Estado.CONECTADO -> {
                avisoShell.visibility = View.GONE
                btnModo.visibility = View.GONE
            }
            ShellManager.Estado.DESCONECTADO -> {
                avisoShell.setText(R.string.aviso_sin_shell)
                avisoShell.visibility = View.VISIBLE
                btnModo.visibility = View.VISIBLE
            }
            ShellManager.Estado.NO_DISPONIBLE -> {
                avisoShell.setText(R.string.aviso_shell_no_disponible)
                avisoShell.visibility = View.VISIBLE
                btnModo.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------- Revocar una app

    private fun revocar(app: AppConPermiso) {
        val motivo = app.esencial
        if (motivo == null) {
            revocarAhora(app)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.revocar_esencial_titulo)
            .setMessage(getString(R.string.revocar_esencial_msg, app.etiqueta, motivo))
            .setPositiveButton(R.string.revocar) { _, _ -> revocarAhora(app) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun revocarAhora(app: AppConPermiso) {
        // Sin ADB: la API nativa de Android, que abre los ajustes de la app para que la persona lo quite a mano.
        if (!Revocador.hayShell()) {
            abrirAjustes(app, true)
            return
        }
        adaptador.marcarEnCurso(app.paquete, true)
        lifecycleScope.launch {
            val r = Revocador.revocar(applicationContext, app.paquete, app.concedidos)
            adaptador.marcarEnCurso(app.paquete, false)
            when (r) {
                ResultadoRevocar.Ok -> {
                    quitar(app)
                    avisarConDeshacer(app)
                }
                is ResultadoRevocar.Fallo -> {
                    sustituir(app, r.quedan)
                    avisarFallo(app, r.motivo)
                }
                is ResultadoRevocar.SinShell -> {
                    pintarEstadoShell(ShellManager.estado())
                    aviso(R.string.perdida_conexion)
                    abrirAjustes(app, false)
                }
            }
        }
    }

    /** «Permiso quitado a X» con un botón para devolverlo si fue sin querer. */
    private fun avisarConDeshacer(app: AppConPermiso) {
        Snackbar.make(findViewById(android.R.id.content), getString(R.string.revocado_ok, app.etiqueta), Snackbar.LENGTH_LONG)
            .setAction(R.string.deshacer) { deshacer(app) }
            .show()
    }

    private fun deshacer(app: AppConPermiso) {
        lifecycleScope.launch {
            val r = Revocador.conceder(applicationContext, app.paquete, app.concedidos)
            if (r !is ResultadoRevocar.Ok) aviso(getString(R.string.deshacer_fallo, app.etiqueta))
            cargar()
        }
    }

    /** La ficha de la app: ahí se ven todos sus permisos y se puede quitar o volver a conceder cada uno. */
    private fun abrirFicha(app: AppConPermiso) {
        startActivity(Intent(this, AppDetalleActivity::class.java).putExtra(AppDetalleActivity.EXTRA_PAQUETE, app.paquete))
    }

    private fun avisarFallo(app: AppConPermiso, motivo: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.revocado_fallo_titulo)
            .setMessage(getString(R.string.revocado_fallo_msg, app.etiqueta, motivo))
            .setPositiveButton(R.string.abrir_ajustes_app) { _, _ -> abrirAjustes(app, true) }
            .setNegativeButton(R.string.cerrar, null)
            .show()
    }

    /** Abre los ajustes de la app (API nativa). conAviso añade la indicación de qué tocar allí. */
    private fun abrirAjustes(app: AppConPermiso, conAviso: Boolean) {
        if (!Sistema.abrirAjustesApp(this, app.paquete)) {
            aviso(R.string.no_se_pudo_abrir)
        } else if (conAviso) {
            aviso(getString(R.string.pasos_ajustes, categoria.titulo))
        }
    }

    private fun quitar(app: AppConPermiso) {
        apps = apps.filter { it.paquete != app.paquete }
        pintar()
    }

    /** La app sigue en la lista, pero solo con los permisos que no se pudieron quitar. */
    private fun sustituir(app: AppConPermiso, quedan: List<String>) {
        apps = if (quedan.isEmpty()) {
            apps.filter { it.paquete != app.paquete }
        } else {
            apps.map { if (it.paquete == app.paquete) AppConPermiso(it.paquete, it.etiqueta, it.sistema, it.esencial, quedan) else it }
        }
        pintar()
    }

    // ---------------------------------------------------------------- Revocar todos

    private fun revocarTodos() {
        if (apps.isEmpty() || masivoActivo) return
        if (!Revocador.hayShell()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.revocar_todos_titulo)
                .setMessage(R.string.requiere_modo_avanzado)
                .setPositiveButton(R.string.abrir_modo_avanzado) { _, _ ->
                    startActivity(Intent(this, ModoAvanzadoActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        // Los componentes esenciales (pantalla de inicio, teclado, teléfono, servicios del sistema…) no se tocan en bloque.
        val objetivo = apps.filter { it.esencial == null }
        val omitidas = apps.size - objetivo.size
        if (objetivo.isEmpty()) {
            aviso(R.string.solo_esenciales)
            return
        }
        var mensaje = getString(R.string.revocar_todos_msg, categoria.titulo, objetivo.size)
        if (omitidas > 0) mensaje += "\n\n" + getString(R.string.revocar_todos_omitidas, omitidas)
        AlertDialog.Builder(this)
            .setTitle(R.string.revocar_todos_titulo)
            .setMessage(mensaje)
            .setPositiveButton(R.string.revocar_todos) { _, _ -> iniciarMasivo(objetivo, omitidas) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ponerMasivo(activo: Boolean) {
        masivoActivo = activo
        adaptador.bloqueado = activo
        panelProgreso.visibility = if (activo) View.VISIBLE else View.GONE
        btnTodos.isEnabled = !activo && apps.isNotEmpty()
    }

    private fun iniciarMasivo(objetivo: List<AppConPermiso>, omitidas: Int) {
        ponerMasivo(true)
        barra.max = objetivo.size
        barra.progress = 0
        trabajo = lifecycleScope.launch {
            var quitadas = 0
            var procesadas = 0
            var perdida = false
            val fallos = ArrayList<String>()
            try {
                // Una app tras otra: cada pm revoke se confirma en el sistema antes de pasar a la siguiente.
                for (app in objetivo) {
                    textoProgreso.text = getString(R.string.progreso, procesadas + 1, objetivo.size, app.etiqueta)
                    barra.progress = procesadas
                    when (val r = Revocador.revocar(applicationContext, app.paquete, app.concedidos)) {
                        ResultadoRevocar.Ok -> {
                            quitadas++
                            quitar(app)
                        }
                        is ResultadoRevocar.Fallo -> {
                            fallos.add(app.etiqueta + " — " + r.motivo)
                            sustituir(app, r.quedan)
                        }
                        is ResultadoRevocar.SinShell -> {
                            perdida = true
                        }
                    }
                    if (perdida) break
                    procesadas++
                }
            } finally {
                val cancelado = !isActive
                ponerMasivo(false)
                mostrarResumen(quitadas, fallos, omitidas, objetivo.size - procesadas, perdida, cancelado)
                cargar()
            }
        }
    }

    private fun mostrarResumen(quitadas: Int, fallos: List<String>, omitidas: Int, pendientes: Int, perdida: Boolean, cancelado: Boolean) {
        if (isFinishing || isDestroyed) return
        val sb = StringBuilder()
        sb.append(getString(R.string.resumen_ok, quitadas))
        if (fallos.isNotEmpty()) {
            sb.append("\n\n").append(getString(R.string.resumen_fallos, fallos.size))
            for (f in fallos.take(8)) sb.append("\n• ").append(f)
            if (fallos.size > 8) sb.append("\n• …")
        }
        if (omitidas > 0) sb.append("\n\n").append(getString(R.string.resumen_omitidas, omitidas))
        if (perdida) {
            sb.append("\n\n").append(getString(R.string.resumen_perdida))
        } else if (cancelado && pendientes > 0) {
            sb.append("\n\n").append(getString(R.string.resumen_pendientes, pendientes))
        }
        val titulo = when {
            perdida -> R.string.resumen_titulo_perdida
            cancelado -> R.string.resumen_titulo_cancelado
            else -> R.string.resumen_titulo_ok
        }
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
