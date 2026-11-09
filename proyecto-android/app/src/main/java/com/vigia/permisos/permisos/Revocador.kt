package com.vigia.permisos.permisos

import android.content.Context
import com.vigia.permisos.adb.ShellManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed class ResultadoRevocar {
    /** Todos los permisos pedidos han quedado revocados (comprobado en el sistema, no solo por lo que imprimió el shell). */
    object Ok : ResultadoRevocar()

    /** El shell funcionó pero Android no dejó quitar todo. En quedan están los permisos que siguen concedidos. */
    class Fallo(val motivo: String, val quedan: List<String>) : ResultadoRevocar()

    /** No hay conexión con el Modo Avanzado (nunca la hubo, se perdió o Android es anterior a la 11). */
    class SinShell(val motivo: String) : ResultadoRevocar()
}

/**
 * Quita permisos con `pm revoke` a través del ShellManager. Sin conexión devuelve SinShell y quien llama decide el
 * plan B (abrir los ajustes de la app para que la persona lo quite a mano).
 */
object Revocador {

    /** Los nombres de paquete y de permiso solo tienen letras, números, punto y guion bajo; se exige para no colar nada en el shell. */
    private val NOMBRE_SEGURO = Regex("^[A-Za-z0-9_.]+$")

    fun hayShell(): Boolean = ShellManager.estado() == ShellManager.Estado.CONECTADO

    /**
     * Revoca cada permiso de la lista a esa app. Las órdenes de una misma app van en una sola llamada al shell
     * («pm revoke app p1; pm revoke app p2»), porque cada llamada tarda al menos un segundo.
     */
    suspend fun revocar(contexto: Context, paquete: String, permisos: List<String>): ResultadoRevocar {
        if (permisos.isEmpty()) return ResultadoRevocar.Ok
        if (!NOMBRE_SEGURO.matches(paquete) || permisos.any { !NOMBRE_SEGURO.matches(it) }) {
            return ResultadoRevocar.Fallo("nombre de paquete o de permiso no válido", permisos)
        }
        // El acceso en segundo plano se quita antes que el principal.
        val ordenados = permisos.sortedByDescending { it.contains("BACKGROUND") }
        val orden = ordenados.joinToString("; ") { "pm revoke " + paquete + " " + it }

        val salida = try {
            ShellManager.ejecutar(orden)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ResultadoRevocar.SinShell(e.message ?: e.javaClass.simpleName)
        }

        delay(300L)
        val quedan = EscanerPermisos.vigentes(contexto, paquete, permisos)
        if (quedan.isEmpty()) return ResultadoRevocar.Ok
        return ResultadoRevocar.Fallo(motivoDe(salida), quedan)
    }

    /** `pm revoke` no imprime nada si va bien; si imprime algo, es el error. */
    private fun motivoDe(salida: String): String {
        val linea = salida.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return if (linea != null) linea.take(160)
        else "Android no lo permitió (puede estar gestionado por el sistema o por una política del dispositivo)"
    }
}
