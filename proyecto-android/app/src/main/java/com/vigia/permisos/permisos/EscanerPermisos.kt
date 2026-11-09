package com.vigia.permisos.permisos

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.vigia.permisos.core.Esenciales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una app que tiene concedido, dentro de una categoría, al menos uno de sus permisos. */
class AppConPermiso(
    val paquete: String,
    val etiqueta: String,
    val sistema: Boolean,
    /** Por qué no se debe tocar (componente del sistema, tu teclado…), o null si se puede. Viene de core/Esenciales. */
    val esencial: String?,
    /** Permisos de la categoría que tiene concedidos ahora mismo (nombres completos de Android). */
    val concedidos: List<String>
)

class ResultadoEscaneo(val porCategoria: Map<String, List<AppConPermiso>>)

/** Recorre las apps instaladas y averigua cuáles tienen concedido cada permiso peligroso. Solo lee; no cambia nada. */
object EscanerPermisos {

    /** Último resultado, para que las pantallas se pinten al instante mientras se vuelve a leer. */
    @Volatile
    var ultimo: ResultadoEscaneo? = null
        private set

    suspend fun escanear(contexto: Context): ResultadoEscaneo = withContext(Dispatchers.Default) {
        val pm = contexto.packageManager
        val acumulado = HashMap<String, MutableList<AppConPermiso>>()

        for (info in aplicaciones(pm)) {
            val paquete = info.packageName
            val pedidos = permisosPedidos(pm, paquete) ?: continue

            // Estado real de cada permiso: checkPermission consulta el sistema en vivo, sin depender de datos en caché.
            val porCategoria = HashMap<String, MutableList<String>>()
            for (p in pedidos) {
                val cat = Categorias.deLaPermiso(p) ?: continue
                if (pm.checkPermission(p, paquete) == PackageManager.PERMISSION_GRANTED) {
                    porCategoria.getOrPut(cat.id) { ArrayList() }.add(p)
                }
            }
            if (porCategoria.isEmpty()) continue

            val etiqueta = try {
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                paquete
            }
            val sistema = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val esencial = Esenciales.motivo(contexto, paquete)
            for ((idCategoria, lista) in porCategoria) {
                acumulado.getOrPut(idCategoria) { ArrayList() }
                    .add(AppConPermiso(paquete, etiqueta, sistema, esencial, lista.sorted()))
            }
        }

        // Primero las apps que instaló la persona, después las del sistema; cada grupo por orden alfabético.
        val orden = compareBy<AppConPermiso>({ it.sistema }, { it.etiqueta.lowercase() })
        val resultado = ResultadoEscaneo(acumulado.mapValues { it.value.sortedWith(orden) })
        ultimo = resultado
        resultado
    }

    /** De los permisos dados, cuáles siguen concedidos ahora mismo. Sirve para comprobar que una revocación ha funcionado. */
    fun vigentes(contexto: Context, paquete: String, permisos: List<String>): List<String> {
        val pm = contexto.packageManager
        return permisos.filter { pm.checkPermission(it, paquete) == PackageManager.PERMISSION_GRANTED }
    }

    @Suppress("DEPRECATION")
    private fun aplicaciones(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }
    }

    /** Se pide app por app (no todas de golpe) para no pasar el límite de tamaño de las llamadas internas de Android. */
    @Suppress("DEPRECATION")
    private fun permisosPedidos(pm: PackageManager, paquete: String): Array<String>? {
        return try {
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(paquete, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                pm.getPackageInfo(paquete, PackageManager.GET_PERMISSIONS)
            }
            info.requestedPermissions
        } catch (e: Exception) {
            null
        }
    }
}
