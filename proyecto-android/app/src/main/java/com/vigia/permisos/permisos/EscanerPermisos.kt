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

/** Un permiso sensible que la app pide, con su estado actual: concedido o no. */
class PermisoDeApp(val nombre: String, val categoria: CategoriaPermiso, val concedido: Boolean) {
    val etiqueta: String get() = categoria.permisos[nombre] ?: nombre.substringAfterLast('.')
}

/** Todos los permisos sensibles que pide una app (concedidos y sin conceder), en el orden de las categorías. */
class AppPermisos(
    val paquete: String,
    val etiqueta: String,
    val sistema: Boolean,
    val esencial: String?,
    val permisos: List<PermisoDeApp>
) {
    val concedidos: Int get() = permisos.count { it.concedido }
}

/** Recorre las apps instaladas y averigua el estado de cada permiso peligroso. Solo lee; no cambia nada. */
object EscanerPermisos {

    /** Último resultado por categoría, para que las pantallas se pinten al instante mientras se vuelve a leer. */
    @Volatile
    var ultimo: ResultadoEscaneo? = null
        private set

    /** Último resultado por app. */
    @Volatile
    var ultimasApps: List<AppPermisos>? = null
        private set

    // Lo esencial de una app casi nunca cambia: se recuerda unos minutos para no repetir esas consultas en cada lectura.
    private val cacheEsencial = HashMap<String, String?>()
    private var cacheDesde = 0L

    /** Vista por categoría (solo los permisos concedidos). Sale del mismo recorrido que la vista por app. */
    suspend fun escanear(contexto: Context): ResultadoEscaneo {
        val apps = escanearApps(contexto)
        val acumulado = HashMap<String, MutableList<AppConPermiso>>()
        for (a in apps) {
            for (cat in Categorias.TODAS) {
                val concedidos = a.permisos.filter { it.categoria.id == cat.id && it.concedido }.map { it.nombre }
                if (concedidos.isNotEmpty()) {
                    acumulado.getOrPut(cat.id) { ArrayList() }.add(AppConPermiso(a.paquete, a.etiqueta, a.sistema, a.esencial, concedidos))
                }
            }
        }
        val resultado = ResultadoEscaneo(acumulado)
        ultimo = resultado
        return resultado
    }

    /** Vista por app: todas las que piden algún permiso sensible, primero las instaladas por la persona y luego las del sistema. */
    suspend fun escanearApps(contexto: Context): List<AppPermisos> = withContext(Dispatchers.Default) {
        val pm = contexto.packageManager
        val conocidos = HashMap<String, Boolean>()
        val lista = ArrayList<AppPermisos>()
        for (info in aplicaciones(pm)) {
            val app = leerApp(contexto, pm, info, conocidos)
            if (app != null) lista.add(app)
        }
        val resultado = lista.sortedWith(compareBy<AppPermisos>({ it.sistema }, { it.etiqueta.lowercase() }))
        ultimasApps = resultado
        resultado
    }

    /** Vuelve a leer una sola app (por ejemplo tras cambiarle un permiso). Devuelve null si ya no está o no pide nada sensible. */
    suspend fun cargarApp(contexto: Context, paquete: String): AppPermisos? = withContext(Dispatchers.Default) {
        val pm = contexto.packageManager
        val info = infoApp(pm, paquete) ?: return@withContext null
        leerApp(contexto, pm, info, HashMap())
    }

    /** De los permisos dados, cuáles siguen concedidos ahora mismo. Sirve para comprobar que un cambio ha funcionado. */
    fun vigentes(contexto: Context, paquete: String, permisos: List<String>): List<String> {
        val pm = contexto.packageManager
        return permisos.filter { pm.checkPermission(it, paquete) == PackageManager.PERMISSION_GRANTED }
    }

    private fun leerApp(contexto: Context, pm: PackageManager, info: ApplicationInfo, conocidos: MutableMap<String, Boolean>): AppPermisos? {
        val paquete = info.packageName
        val pedidos = permisosPedidos(pm, paquete) ?: return null
        val pedidosSet = pedidos.toHashSet()

        // Estado real de cada permiso: checkPermission consulta el sistema en vivo, sin depender de datos en caché.
        val permisos = ArrayList<PermisoDeApp>()
        for (cat in Categorias.TODAS) {
            for (p in cat.permisos.keys) {
                if (!pedidosSet.contains(p)) continue
                // Se ignoran los permisos que esta versión de Android no conoce (p. ej. los de fotos de Android 13 en un Android 12).
                val existe = conocidos.getOrPut(p) { existeEnSistema(pm, p) }
                if (!existe) continue
                permisos.add(PermisoDeApp(p, cat, pm.checkPermission(p, paquete) == PackageManager.PERMISSION_GRANTED))
            }
        }
        if (permisos.isEmpty()) return null

        val etiqueta = try {
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            paquete
        }
        val sistema = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return AppPermisos(paquete, etiqueta, sistema, esencialDe(contexto, paquete), permisos)
    }

    private fun esencialDe(contexto: Context, paquete: String): String? = synchronized(cacheEsencial) {
        val ahora = System.currentTimeMillis()
        if (ahora - cacheDesde > 300_000L) {
            cacheEsencial.clear()
            cacheDesde = ahora
        }
        if (cacheEsencial.containsKey(paquete)) {
            cacheEsencial[paquete]
        } else {
            val motivo = Esenciales.motivo(contexto, paquete)
            cacheEsencial[paquete] = motivo
            motivo
        }
    }

    @Suppress("DEPRECATION")
    private fun existeEnSistema(pm: PackageManager, permiso: String): Boolean {
        return try {
            pm.getPermissionInfo(permiso, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun aplicaciones(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun infoApp(pm: PackageManager, paquete: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(paquete, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                pm.getApplicationInfo(paquete, 0)
            }
        } catch (e: Exception) {
            null
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
