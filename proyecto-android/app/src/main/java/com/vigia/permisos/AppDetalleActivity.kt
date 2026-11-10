package com.vigia.permisos

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.vigia.permisos.adb.ModoAvanzadoActivity
import com.vigia.permisos.adb.ShellManager
import com.vigia.permisos.core.ActividadBase
import com.vigia.permisos.core.Sistema
import com.vigia.permisos.core.Tema
import com.vigia.permisos.permisos.AppPermisos
import com.vigia.permisos.permisos.EscanerPermisos
import com.vigia.permisos.permisos.PermisoDeApp
import com.vigia.permisos.permisos.ResultadoRevocar
import com.vigia.permisos.permisos.Revocador
import kotlinx.coroutines.launch

/**
 * Ficha de una app: todos sus permisos sensibles con un interruptor para quitarlos o volver a concederlos.
 * Con el Modo Avanzado conectado el cambio se hace con `pm revoke` / `pm grant`; sin él se abren los ajustes de la app.
 */
class AppDetalleActivity : ActividadBase() {

    companion object {
        const val EXTRA_PAQUETE = "paquete"
    }

    private lateinit var paquete: String
    private lateinit var icono: ImageView
    private lateinit var nombre: TextView
    private lateinit var paqueteTexto: TextView
    private lateinit var etiquetas: TextView
    private lateinit var resumen: TextView
    private lateinit var avisoShell: TextView
    private lateinit var btnModo: MaterialButton
    private lateinit var btnQuitarTodos: MaterialButton
    private lateinit var ocupadoBarra: LinearProgressIndicator
    private lateinit var adaptador: PermisosAdapter

    private var app: AppPermisos? = null
    private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paquete = intent.getStringExtra(EXTRA_PAQUETE) ?: run {
            finish()
            return
        }
        setContentView(R.layout.activity_app_detalle)
        prepararPantalla(R.string.ayuda_detalle_titulo, R.string.ayuda_detalle)

        icono = findViewById(R.id.detIcono)
        nombre = findViewById(R.id.detNombre)
        paqueteTexto = findViewById(R.id.detPaquete)
        etiquetas = findViewById(R.id.detEtiquetas)
        resumen = findViewById(R.id.detResumen)
        avisoShell = findViewById(R.id.detAvisoShell)
        btnModo = findViewById(R.id.detBtnModo)
        btnQuitarTodos = findViewById(R.id.detBtnQuitarTodos)
        ocupadoBarra = findViewById(R.id.detOcupado)

        // «Quitar todos» es la acción destructiva de la pantalla: en rojo, como en «Revocar todos».
        val sinEstadoActivo = intArrayOf(-android.R.attr.state_enabled)
        btnQuitarTodos.backgroundTintList = ColorStateList(arrayOf(sinEstadoActivo, intArrayOf()), intArrayOf(0x33FFFFFF, Tema.rojo(this)))
        btnQuitarTodos.setTextColor(ColorStateList(arrayOf(sinEstadoActivo, intArrayOf()), intArrayOf(0x61FFFFFF, Color.WHITE)))

        adaptador = PermisosAdapter { permiso, conceder -> cambiar(permiso, conceder) }
        val lista = findViewById<RecyclerView>(R.id.listaPermisos)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adaptador

        findViewById<MaterialButton>(R.id.detBtnAjustes).setOnClickListener { abrirAjustes(null) }
        btnQuitarTodos.setOnClickListener { quitarTodos() }
        btnModo.setOnClickListener { startActivity(Intent(this, ModoAvanzadoActivity::class.java)) }

        val previa = EscanerPermisos.ultimasApps?.firstOrNull { it.paquete == paquete }
        if (previa != null) app = previa
        pintar()
    }

    override fun onResume() {
        super.onResume()
        if (!::paquete.isInitialized) return
        pintarEstadoShell(ShellManager.estado())
        lifecycleScope.launch {
            pintarEstadoShell(ShellManager.verificar())
        }
        if (!ocupado) cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            val leida = try {
                EscanerPermisos.cargarApp(applicationContext, paquete)
            } catch (e: Exception) {
                null
            }
            if (leida == null) {
                // Se desinstaló, o ya no pide ningún permiso sensible.
                aviso(R.string.app_no_disponible)
                finish()
                return@launch
            }
            app = leida
            pintar()
        }
    }

    private fun pintar() {
        val a = app
        if (a == null) {
            nombre.text = paquete
            return
        }
        try {
            icono.setImageDrawable(packageManager.getApplicationIcon(a.paquete))
        } catch (e: Exception) {
            icono.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        nombre.text = a.etiqueta
        paqueteTexto.text = a.paquete
        etiquetas.text = when {
            a.esencial != null -> getString(R.string.etiqueta_esencial, a.esencial)
            a.sistema -> getString(R.string.etiqueta_sistema)
            else -> getString(R.string.etiqueta_usuario)
        }
        resumen.text = getString(R.string.resumen_app_largo, a.concedidos, a.permisos.size - a.concedidos)
        adaptador.poner(a.permisos)
        btnQuitarTodos.isEnabled = a.concedidos > 0 && !ocupado
    }

    private fun pintarEstadoShell(e: ShellManager.Estado) {
        when (e) {
            ShellManager.Estado.CONECTADO -> {
                avisoShell.visibility = View.GONE
                btnModo.visibility = View.GONE
            }
            ShellManager.Estado.DESCONECTADO -> {
                avisoShell.setText(R.string.aviso_detalle_sin_shell)
                avisoShell.visibility = View.VISIBLE
                btnModo.visibility = View.VISIBLE
            }
            ShellManager.Estado.NO_DISPONIBLE -> {
                avisoShell.setText(R.string.aviso_detalle_shell_no_disponible)
                avisoShell.visibility = View.VISIBLE
                btnModo.visibility = View.GONE
            }
        }
    }

    private fun ponerOcupado(activo: Boolean) {
        ocupado = activo
        adaptador.bloqueado = activo
        ocupadoBarra.visibility = if (activo) View.VISIBLE else View.INVISIBLE
        btnQuitarTodos.isEnabled = !activo && (app?.concedidos ?: 0) > 0
    }

    // ---------------------------------------------------------------- Cambiar un permiso

    private fun cambiar(permiso: PermisoDeApp, conceder: Boolean) {
        val a = app ?: return
        if (ocupado) return
        // Sin ADB: la API nativa de Android, que abre los ajustes de la app para que la persona lo cambie a mano.
        if (!Revocador.hayShell()) {
            abrirAjustes(permiso.categoria.titulo + " · " + permiso.etiqueta)
            return
        }
        val motivo = a.esencial
        if (!conceder && motivo != null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.revocar_esencial_titulo)
                .setMessage(getString(R.string.revocar_esencial_msg, a.etiqueta, motivo))
                .setPositiveButton(R.string.revocar) { _, _ -> ejecutar(listOf(permiso.nombre), false) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        ejecutar(listOf(permiso.nombre), conceder)
    }

    private fun ejecutar(nombres: List<String>, conceder: Boolean) {
        val a = app ?: return
        ponerOcupado(true)
        lifecycleScope.launch {
            val r = if (conceder) {
                Revocador.conceder(applicationContext, a.paquete, nombres)
            } else {
                Revocador.revocar(applicationContext, a.paquete, nombres)
            }
            ponerOcupado(false)
            when (r) {
                ResultadoRevocar.Ok -> aviso(if (conceder) R.string.permiso_concedido else R.string.permiso_quitado)
                is ResultadoRevocar.Fallo -> avisarFallo(a, r.motivo)
                is ResultadoRevocar.SinShell -> {
                    pintarEstadoShell(ShellManager.estado())
                    aviso(R.string.perdida_conexion)
                    abrirAjustes(null)
                }
            }
            cargar()
        }
    }

    private fun avisarFallo(a: AppPermisos, motivo: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.cambio_fallo_titulo)
            .setMessage(getString(R.string.revocado_fallo_msg, a.etiqueta, motivo))
            .setPositiveButton(R.string.abrir_ajustes_app) { _, _ -> abrirAjustes(null) }
            .setNegativeButton(R.string.cerrar, null)
            .show()
    }

    // ---------------------------------------------------------------- Quitar todos los de esta app

    private fun quitarTodos() {
        val a = app ?: return
        if (ocupado) return
        val concedidos = a.permisos.filter { it.concedido }.map { it.nombre }
        if (concedidos.isEmpty()) return
        if (!Revocador.hayShell()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.quitar_todos)
                .setMessage(R.string.requiere_modo_avanzado)
                .setPositiveButton(R.string.abrir_modo_avanzado) { _, _ ->
                    startActivity(Intent(this, ModoAvanzadoActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        var mensaje = getString(R.string.quitar_todos_msg, concedidos.size, a.etiqueta)
        val motivo = a.esencial
        if (motivo != null) mensaje += "\n\n" + getString(R.string.revocar_esencial_msg, a.etiqueta, motivo)
        AlertDialog.Builder(this)
            .setTitle(R.string.quitar_todos)
            .setMessage(mensaje)
            .setPositiveButton(R.string.quitar_todos) { _, _ -> ejecutar(concedidos, false) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Abre los ajustes de la app (API nativa). Con nombre de permiso añade la indicación de qué tocar allí. */
    private fun abrirAjustes(permiso: String?) {
        if (!Sistema.abrirAjustesApp(this, paquete)) {
            aviso(R.string.no_se_pudo_abrir)
        } else if (permiso != null) {
            aviso(getString(R.string.pasos_ajustes_cambiar, permiso))
        }
    }
}
