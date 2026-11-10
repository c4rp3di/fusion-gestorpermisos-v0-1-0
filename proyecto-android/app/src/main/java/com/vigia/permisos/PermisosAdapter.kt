package com.vigia.permisos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.vigia.permisos.permisos.PermisoDeApp

/**
 * Los permisos sensibles de una app, cada uno con su interruptor. Tocar la fila pide el cambio (alCambiar recibe el estado
 * que se quiere); el interruptor solo se mueve cuando la lista se vuelve a pintar con el estado real del sistema.
 */
class PermisosAdapter(
    private val alCambiar: (PermisoDeApp, Boolean) -> Unit
) : RecyclerView.Adapter<PermisosAdapter.Fila>() {

    private var datos: List<PermisoDeApp> = emptyList()

    /** Mientras se hace un cambio se bloquean todas las filas. */
    var bloqueado: Boolean = false
        set(valor) {
            field = valor
            notifyDataSetChanged()
        }

    fun poner(nuevos: List<PermisoDeApp>) {
        datos = nuevos
        notifyDataSetChanged()
    }

    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val icono: TextView = vista.findViewById(R.id.permIcono)
        val titulo: TextView = vista.findViewById(R.id.permTitulo)
        val estado: TextView = vista.findViewById(R.id.permEstado)
        val nombre: TextView = vista.findViewById(R.id.permNombre)
        val interruptor: MaterialSwitch = vista.findViewById(R.id.permSwitch)
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Fila {
        return Fila(LayoutInflater.from(padre.context).inflate(R.layout.item_permiso, padre, false))
    }

    override fun getItemCount(): Int = datos.size

    override fun onBindViewHolder(fila: Fila, posicion: Int) {
        val p = datos[posicion]
        fila.icono.text = p.categoria.icono
        fila.titulo.text = p.categoria.titulo + " · " + p.etiqueta
        fila.estado.setText(if (p.concedido) R.string.estado_concedido else R.string.estado_sin_conceder)
        fila.nombre.text = p.nombre.substringAfterLast('.')
        fila.interruptor.isChecked = p.concedido
        fila.itemView.isEnabled = !bloqueado
        fila.itemView.alpha = if (bloqueado) 0.5f else 1f
        fila.itemView.setOnClickListener {
            if (!bloqueado) alCambiar(p, !p.concedido)
        }
    }
}
