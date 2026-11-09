package com.vigia.permisos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vigia.permisos.permisos.AppConPermiso
import com.vigia.permisos.permisos.CategoriaPermiso

/** Lista de apps con el permiso concedido: icono, nombre, qué permisos concretos tiene y el botón «Revocar». */
class AppsAdapter(
    private val categoria: CategoriaPermiso,
    private val alRevocar: (AppConPermiso) -> Unit,
    private val alAbrir: (AppConPermiso) -> Unit
) : RecyclerView.Adapter<AppsAdapter.Fila>() {

    private var datos: List<AppConPermiso> = emptyList()
    private val enCurso = HashSet<String>()

    /** Mientras corre «Revocar todos» se bloquean los botones de cada fila. */
    var bloqueado: Boolean = false
        set(valor) {
            field = valor
            notifyDataSetChanged()
        }

    fun poner(nuevos: List<AppConPermiso>) {
        datos = nuevos
        notifyDataSetChanged()
    }

    fun marcarEnCurso(paquete: String, activo: Boolean) {
        if (activo) enCurso.add(paquete) else enCurso.remove(paquete)
        notifyDataSetChanged()
    }

    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val icono: ImageView = vista.findViewById(R.id.appIcono)
        val nombre: TextView = vista.findViewById(R.id.appNombre)
        val permisos: TextView = vista.findViewById(R.id.appPermisos)
        val paquete: TextView = vista.findViewById(R.id.appPaquete)
        val boton: MaterialButton = vista.findViewById(R.id.appRevocar)
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Fila {
        val v = LayoutInflater.from(padre.context).inflate(R.layout.item_app, padre, false)
        return Fila(v)
    }

    override fun getItemCount(): Int = datos.size

    override fun onBindViewHolder(fila: Fila, posicion: Int) {
        val app = datos[posicion]
        val pm = fila.itemView.context.packageManager

        try {
            fila.icono.setImageDrawable(pm.getApplicationIcon(app.paquete))
        } catch (e: Exception) {
            fila.icono.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        val etiquetas = app.concedidos.joinToString(" · ") { categoria.permisos[it] ?: it.substringAfterLast('.') }
        val marca = when {
            app.esencial != null -> "  · Esencial"
            app.sistema -> "  · Sistema"
            else -> ""
        }
        fila.nombre.text = app.etiqueta
        fila.permisos.text = etiquetas + marca
        fila.paquete.text = app.paquete

        val ocupada = enCurso.contains(app.paquete)
        fila.boton.isEnabled = !bloqueado && !ocupada
        fila.boton.text = if (ocupada) "…" else fila.itemView.context.getString(R.string.revocar)
        fila.boton.setOnClickListener { alRevocar(app) }
        fila.itemView.setOnClickListener { alAbrir(app) }
    }
}
