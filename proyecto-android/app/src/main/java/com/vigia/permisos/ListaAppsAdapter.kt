package com.vigia.permisos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vigia.permisos.permisos.AppPermisos
import com.vigia.permisos.permisos.CategoriaPermiso

/** Lista de apps (vista por app): icono, nombre, qué categorías tiene concedidas y cuántos permisos están dados o sin dar. */
class ListaAppsAdapter(
    private val alAbrir: (AppPermisos) -> Unit
) : RecyclerView.Adapter<ListaAppsAdapter.Fila>() {

    private var datos: List<AppPermisos> = emptyList()
    private var categoria: CategoriaPermiso? = null

    /** categoria != null cuando el filtro de categoría está activo: los números y los iconos se refieren solo a ella. */
    fun poner(nuevos: List<AppPermisos>, categoria: CategoriaPermiso?) {
        datos = nuevos
        this.categoria = categoria
        notifyDataSetChanged()
    }

    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val icono: ImageView = vista.findViewById(R.id.listaIcono)
        val nombre: TextView = vista.findViewById(R.id.listaNombre)
        val iconos: TextView = vista.findViewById(R.id.listaIconos)
        val resumen: TextView = vista.findViewById(R.id.listaResumen)
        val paquete: TextView = vista.findViewById(R.id.listaPaquete)
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Fila {
        return Fila(LayoutInflater.from(padre.context).inflate(R.layout.item_app_lista, padre, false))
    }

    override fun getItemCount(): Int = datos.size

    override fun onBindViewHolder(fila: Fila, posicion: Int) {
        val app = datos[posicion]
        val contexto = fila.itemView.context

        try {
            fila.icono.setImageDrawable(contexto.packageManager.getApplicationIcon(app.paquete))
        } catch (e: Exception) {
            fila.icono.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        val cat = categoria
        val relevantes = if (cat != null) app.permisos.filter { it.categoria.id == cat.id } else app.permisos
        val concedidos = relevantes.filter { it.concedido }
        val iconos = concedidos.map { it.categoria.icono }.distinct().joinToString(" ")
        val marca = when {
            app.esencial != null -> "  · Esencial"
            app.sistema -> "  · Sistema"
            else -> ""
        }

        fila.nombre.text = app.etiqueta + marca
        fila.iconos.text = iconos
        fila.iconos.visibility = if (iconos.isEmpty()) View.GONE else View.VISIBLE
        fila.resumen.text = contexto.getString(R.string.resumen_app, concedidos.size, relevantes.size - concedidos.size)
        fila.paquete.text = app.paquete
        fila.itemView.setOnClickListener { alAbrir(app) }
    }
}
