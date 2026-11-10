package com.vigia.permisos

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.vigia.permisos.adb.ModoAvanzadoActivity
import com.vigia.permisos.adb.ShellManager
import com.vigia.permisos.core.ActividadBase
import com.vigia.permisos.core.Ayuda
import com.vigia.permisos.core.Tema
import com.vigia.permisos.core.Ui
import com.vigia.permisos.permisos.Categorias
import com.vigia.permisos.permisos.EscanerPermisos
import com.vigia.permisos.permisos.ResultadoEscaneo
import kotlinx.coroutines.launch

/**
 * Pantalla principal: la entrada «Por app» y una tarjeta por categoría de permiso peligroso, con cuántas apps lo tienen
 * concedido.
 */
class MainActivity : ActividadBase() {

    /** Los dos textos que cambian en una tarjeta cuando llegan los datos: la frase y el número de la derecha. */
    private class Tarjeta(val frase: TextView, val numero: TextView)

    private lateinit var estado: TextView
    private lateinit var resumen: TextView
    private lateinit var lista: LinearLayout
    private lateinit var porApp: Tarjeta
    private val porCategoria = HashMap<String, Tarjeta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        estado = findViewById(R.id.estadoShell)
        resumen = findViewById(R.id.resumenTotal)
        lista = findViewById(R.id.listaCategorias)

        findViewById<View>(R.id.cardEstado).setOnClickListener {
            startActivity(Intent(this, ModoAvanzadoActivity::class.java))
        }
        val cabecera = findViewById<LinearLayout>(R.id.filaCabecera)
        cabecera.addView(Ayuda.enlace(this, "?  Ayuda") { Ayuda.mostrar(this, R.string.ayuda_principal_titulo, R.string.ayuda_principal) })

        construirTarjetas()
        pintarEstado(ShellManager.estado())
        val previo = EscanerPermisos.ultimo
        if (previo != null) pintarConteos(previo) else resumen.setText(R.string.analizando)
    }

    override fun onResume() {
        super.onResume()
        pintarEstado(ShellManager.estado())
        lifecycleScope.launch {
            pintarEstado(ShellManager.verificar())
        }
        // Se vuelve a leer cada vez que se entra: la persona puede haber cambiado permisos desde los ajustes de Android.
        lifecycleScope.launch {
            try {
                pintarConteos(EscanerPermisos.escanear(applicationContext))
            } catch (e: Exception) {
                resumen.text = getString(R.string.error_escaneo, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Una tarjeta con icono, título, frase y número a la derecha. Devuelve la tarjeta y los textos que se rellenan después. */
    private fun crearTarjeta(icono: String, titulo: String, frase: String): Pair<MaterialCardView, Tarjeta> {
        val tarjeta = Ui.tarjeta(this)
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        fila.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14))

        val iconoVista = Ui.texto(this, icono, 26f)
        iconoVista.gravity = Gravity.CENTER
        fila.addView(iconoVista, LinearLayout.LayoutParams(Ui.dp(this, 44), ViewGroup.LayoutParams.WRAP_CONTENT))

        val columna = LinearLayout(this)
        columna.orientation = LinearLayout.VERTICAL
        columna.addView(Ui.texto(this, titulo, 17f, true))
        val fraseVista = Ui.texto(this, frase, 13f)
        columna.addView(fraseVista)
        fila.addView(columna, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val numero = Ui.texto(this, "…", 24f, true)
        fila.addView(numero)

        tarjeta.addView(fila, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Pair(tarjeta, Tarjeta(fraseVista, numero))
    }

    private fun construirTarjetas() {
        val (tarjetaApp, textosApp) = crearTarjeta("📱", getString(R.string.por_app_titulo), getString(R.string.por_app_frase))
        tarjetaApp.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        lista.addView(tarjetaApp)
        porApp = textosApp

        lista.addView(Ui.seccion(this, getString(R.string.por_categoria)))

        for (cat in Categorias.TODAS) {
            val (tarjeta, textos) = crearTarjeta(cat.icono, cat.titulo, getString(R.string.analizando_corto))
            tarjeta.setOnClickListener {
                startActivity(Intent(this, CategoriaActivity::class.java).putExtra(CategoriaActivity.EXTRA_CATEGORIA, cat.id))
            }
            lista.addView(tarjeta)
            porCategoria[cat.id] = textos
        }
    }

    private fun pintarConteos(r: ResultadoEscaneo) {
        for (cat in Categorias.TODAS) {
            val n = r.porCategoria[cat.id]?.size ?: 0
            val textos = porCategoria[cat.id]
            if (textos != null) {
                textos.frase.text = Categorias.frase(cat, n)
                textos.numero.text = n.toString()
                textos.numero.setTextColor(if (n > 0) Tema.ambar(this) else Tema.verde(this))
            }
        }
        val distintas = r.porCategoria.values.flatten().map { it.paquete }.toSet().size
        porApp.numero.text = distintas.toString()
        porApp.numero.setTextColor(Tema.azul(this))
        resumen.text = if (distintas == 0) getString(R.string.resumen_total_cero) else getString(R.string.resumen_total, distintas)
    }

    private fun pintarEstado(e: ShellManager.Estado) {
        estado.setText(
            when (e) {
                ShellManager.Estado.CONECTADO -> R.string.estado_conectado
                ShellManager.Estado.DESCONECTADO -> R.string.estado_desconectado
                ShellManager.Estado.NO_DISPONIBLE -> R.string.estado_no_disponible
            }
        )
    }
}
