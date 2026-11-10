package com.vigia.permisos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.vigia.permisos.core.ActividadBase
import com.vigia.permisos.permisos.AppPermisos
import com.vigia.permisos.permisos.Categorias
import com.vigia.permisos.permisos.EscanerPermisos
import kotlinx.coroutines.launch

/** Vista por app: todas las apps que piden permisos sensibles, con buscador y filtros. Tocar una abre su ficha. */
class AppsActivity : ActividadBase() {

    private lateinit var etBuscar: EditText
    private lateinit var chips: ChipGroup
    private lateinit var spCategoria: Spinner
    private lateinit var chkConcedidos: CheckBox
    private lateinit var contador: TextView
    private lateinit var textoVacio: TextView
    private lateinit var adaptador: ListaAppsAdapter

    private var todas: List<AppPermisos> = emptyList()
    private var cargado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        prepararPantalla(R.string.ayuda_apps_titulo, R.string.ayuda_apps)

        etBuscar = findViewById(R.id.etBuscarApp)
        chips = findViewById(R.id.chipsOrigen)
        spCategoria = findViewById(R.id.spCategoria)
        chkConcedidos = findViewById(R.id.chkSoloConcedidos)
        contador = findViewById(R.id.contadorApps)
        textoVacio = findViewById(R.id.textoVacioApps)

        adaptador = ListaAppsAdapter { abrirFicha(it) }
        val lista = findViewById<RecyclerView>(R.id.listaTodasApps)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adaptador

        val nombres = ArrayList<String>()
        nombres.add(getString(R.string.cualquier_permiso))
        for (c in Categorias.TODAS) nombres.add(c.icono + "  " + c.titulo)
        spCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)

        spCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(padre: AdapterView<*>?, vista: View?, posicion: Int, id: Long) {
                aplicar()
            }

            override fun onNothingSelected(padre: AdapterView<*>?) {
            }
        }
        etBuscar.doAfterTextChanged { aplicar() }
        chips.setOnCheckedStateChangeListener { _, _ -> aplicar() }
        chkConcedidos.setOnCheckedChangeListener { _, _ -> aplicar() }

        // Se pinta al instante con la última lectura (si la hay) y luego se refresca.
        val previo = EscanerPermisos.ultimasApps
        if (previo != null) {
            todas = previo
            cargado = true
        }
        aplicar()
    }

    override fun onResume() {
        super.onResume()
        // Se vuelve a leer cada vez que se entra: los permisos pueden haber cambiado en la ficha o en los ajustes de Android.
        lifecycleScope.launch {
            try {
                todas = EscanerPermisos.escanearApps(applicationContext)
                cargado = true
            } catch (e: Exception) {
                aviso(getString(R.string.error_escaneo, e.message ?: e.javaClass.simpleName))
            }
            aplicar()
        }
    }

    private fun abrirFicha(app: AppPermisos) {
        startActivity(Intent(this, AppDetalleActivity::class.java).putExtra(AppDetalleActivity.EXTRA_PAQUETE, app.paquete))
    }

    private fun aplicar() {
        if (!::adaptador.isInitialized) return
        val texto = etBuscar.text.toString().trim().lowercase()
        val posicion = spCategoria.selectedItemPosition
        val categoria = if (posicion > 0) Categorias.TODAS.getOrNull(posicion - 1) else null
        val soloConcedidos = chkConcedidos.isChecked
        val origen = chips.checkedChipId

        val filtradas = todas.filter { app ->
            val porOrigen = when (origen) {
                R.id.chipMias -> !app.sistema
                R.id.chipSistema -> app.sistema
                else -> true
            }
            val porTexto = texto.isEmpty() || app.etiqueta.lowercase().contains(texto) || app.paquete.lowercase().contains(texto)
            val relevantes = if (categoria != null) app.permisos.filter { it.categoria.id == categoria.id } else app.permisos
            val porPermiso = if (soloConcedidos) relevantes.any { it.concedido } else relevantes.isNotEmpty()
            porOrigen && porTexto && porPermiso
        }

        adaptador.poner(filtradas, categoria)
        contador.text = if (cargado) getString(R.string.n_apps, filtradas.size, todas.size) else getString(R.string.analizando)
        textoVacio.visibility = if (cargado && filtradas.isEmpty()) View.VISIBLE else View.GONE
    }
}
