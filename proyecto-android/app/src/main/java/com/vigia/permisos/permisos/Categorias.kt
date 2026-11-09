package com.vigia.permisos.permisos

/**
 * Una categoría de permisos peligrosos (en tiempo de ejecución) tal y como la entiende una persona: «Ubicación» agrupa los
 * permisos precisa, aproximada y en segundo plano. Quitar una categoría a una app es quitarle todos los permisos concedidos
 * de esta lista.
 */
class CategoriaPermiso(
    val id: String,
    val icono: String,
    val titulo: String,
    val descripcion: String,
    /** Lo que sigue a «apps» en el contador: «con acceso a tu ubicación». */
    val acceso: String,
    /** Nombre completo del permiso de Android → etiqueta corta para la lista. */
    val permisos: Map<String, String>
)

object Categorias {
    private const val P = "android.permission."

    val TODAS: List<CategoriaPermiso> = listOf(
        CategoriaPermiso(
            "camara", "📷", "Cámara",
            "Hacer fotos y grabar vídeo en cualquier momento.",
            "con acceso a tu cámara",
            mapOf(P + "CAMERA" to "Cámara")
        ),
        CategoriaPermiso(
            "microfono", "🎙", "Micrófono",
            "Grabar el sonido que hay a tu alrededor.",
            "con acceso a tu micrófono",
            mapOf(P + "RECORD_AUDIO" to "Micrófono")
        ),
        CategoriaPermiso(
            "ubicacion", "📍", "Ubicación",
            "Saber dónde estás, también con la app cerrada si tiene el acceso en segundo plano.",
            "con acceso a tu ubicación",
            mapOf(
                P + "ACCESS_FINE_LOCATION" to "Precisa",
                P + "ACCESS_COARSE_LOCATION" to "Aproximada",
                P + "ACCESS_BACKGROUND_LOCATION" to "Segundo plano"
            )
        ),
        CategoriaPermiso(
            "contactos", "👤", "Contactos",
            "Leer o modificar tu agenda y las cuentas del teléfono.",
            "con acceso a tus contactos",
            mapOf(
                P + "READ_CONTACTS" to "Leer",
                P + "WRITE_CONTACTS" to "Modificar",
                P + "GET_ACCOUNTS" to "Cuentas"
            )
        ),
        CategoriaPermiso(
            "sms", "💬", "SMS",
            "Leer, recibir y enviar mensajes de texto (que pueden costar dinero).",
            "con acceso a tus SMS",
            mapOf(
                P + "READ_SMS" to "Leer",
                P + "RECEIVE_SMS" to "Recibir",
                P + "SEND_SMS" to "Enviar",
                P + "RECEIVE_MMS" to "MMS",
                P + "RECEIVE_WAP_PUSH" to "WAP push"
            )
        ),
        CategoriaPermiso(
            "telefono", "📞", "Teléfono",
            "Ver el estado y el número de tu línea, y hacer o contestar llamadas.",
            "con acceso a tu teléfono",
            mapOf(
                P + "READ_PHONE_STATE" to "Estado",
                P + "READ_PHONE_NUMBERS" to "Números",
                P + "CALL_PHONE" to "Llamar",
                P + "ANSWER_PHONE_CALLS" to "Contestar",
                P + "ADD_VOICEMAIL" to "Buzón de voz",
                P + "USE_SIP" to "SIP",
                P + "ACCEPT_HANDOVER" to "Traspaso"
            )
        ),
        CategoriaPermiso(
            "llamadas", "🕘", "Registro de llamadas",
            "Leer o modificar la lista de llamadas hechas, recibidas y perdidas.",
            "con acceso a tu registro de llamadas",
            mapOf(
                P + "READ_CALL_LOG" to "Leer",
                P + "WRITE_CALL_LOG" to "Modificar",
                P + "PROCESS_OUTGOING_CALLS" to "Llamadas salientes"
            )
        ),
        CategoriaPermiso(
            "calendario", "📅", "Calendario",
            "Leer o modificar tus eventos.",
            "con acceso a tu calendario",
            mapOf(
                P + "READ_CALENDAR" to "Leer",
                P + "WRITE_CALENDAR" to "Modificar"
            )
        ),
        CategoriaPermiso(
            "archivos", "🖼", "Fotos, vídeos y archivos",
            "Leer (o escribir en) tu almacenamiento y tu galería.",
            "con acceso a tus fotos y archivos",
            mapOf(
                P + "READ_EXTERNAL_STORAGE" to "Leer almacenamiento",
                P + "WRITE_EXTERNAL_STORAGE" to "Escribir almacenamiento",
                P + "READ_MEDIA_IMAGES" to "Fotos",
                P + "READ_MEDIA_VIDEO" to "Vídeos",
                P + "READ_MEDIA_AUDIO" to "Audio",
                P + "READ_MEDIA_VISUAL_USER_SELECTED" to "Selección",
                P + "ACCESS_MEDIA_LOCATION" to "Ubicación de las fotos"
            )
        ),
        CategoriaPermiso(
            "sensores", "❤", "Sensores y actividad física",
            "Pulso y otros sensores corporales, y reconocimiento de tu actividad (andar, correr…).",
            "con acceso a tus sensores y actividad",
            mapOf(
                P + "BODY_SENSORS" to "Sensores corporales",
                P + "BODY_SENSORS_BACKGROUND" to "Segundo plano",
                P + "ACTIVITY_RECOGNITION" to "Actividad física"
            )
        ),
        CategoriaPermiso(
            "cercanos", "📡", "Dispositivos cercanos",
            "Buscar y conectarse a Bluetooth y a otros dispositivos de alrededor.",
            "con acceso a dispositivos cercanos",
            mapOf(
                P + "BLUETOOTH_CONNECT" to "Conectar",
                P + "BLUETOOTH_SCAN" to "Buscar",
                P + "BLUETOOTH_ADVERTISE" to "Anunciarse",
                P + "NEARBY_WIFI_DEVICES" to "Wi-Fi cercano",
                P + "UWB_RANGING" to "Banda ultraancha"
            )
        ),
        CategoriaPermiso(
            "notificaciones", "🔔", "Notificaciones",
            "Enviarte avisos (Android 13 o superior).",
            "con permiso para enviarte notificaciones",
            mapOf(P + "POST_NOTIFICATIONS" to "Notificaciones")
        )
    )

    private val porPermiso: Map<String, CategoriaPermiso> = TODAS.flatMap { c -> c.permisos.keys.map { it to c } }.toMap()

    fun porId(id: String?): CategoriaPermiso? = TODAS.firstOrNull { it.id == id }

    fun deLaPermiso(permiso: String): CategoriaPermiso? = porPermiso[permiso]

    /** «12 apps con acceso a tu ubicación», «1 app con acceso…», «Ninguna app con acceso…». */
    fun frase(c: CategoriaPermiso, n: Int): String = when (n) {
        0 -> "Ninguna app " + c.acceso
        1 -> "1 app " + c.acceso
        else -> n.toString() + " apps " + c.acceso
    }
}
