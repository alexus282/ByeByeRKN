package com.dostup.app.vpn

/** Where traffic leaves Cloudflare, parsed from a cdn-cgi/trace response. */
data class ExitInfo(val colo: String, val country: String, val ip: String, val warp: Boolean) {
    val city: String? get() = CITIES[colo]

    /**
     * Traffic leaving Cloudflare here crosses Russian networks and DPI again, which is why
     * Telegram over IPv4 dies on such routes. Measured by [colo], not [country]: Cloudflare
     * reports loc=RU for Russian users even when the exit is in Helsinki.
     */
    val inRussia: Boolean get() = colo in RUSSIAN_COLOS

    /** "Варшава, PL" or just the code when the city is unknown. */
    val label: String get() = listOfNotNull(city ?: colo, country.takeIf { it.isNotBlank() }).joinToString(", ")

    companion object {
        fun parse(body: String): ExitInfo? {
            val fields = body.lineSequence()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim() } }
                .toMap()
            val colo = fields["colo"] ?: return null
            return ExitInfo(
                colo = colo,
                country = fields["loc"].orEmpty(),
                ip = fields["ip"].orEmpty(),
                warp = fields["warp"] == "on" || fields["warp"] == "plus",
            )
        }

        val RUSSIAN_COLOS = setOf("DME", "SVO", "VKO", "LED", "KJA", "SVX", "OVB", "KZN", "ROV", "AER", "KHV", "VVO")

        /** Cloudflare data centres reachable from Russia and nearby, by IATA code. */
        val CITIES = mapOf(
            "WAW" to "Варшава", "FRA" to "Франкфурт", "HEL" to "Хельсинки", "ARN" to "Стокгольм",
            "AMS" to "Амстердам", "VIE" to "Вена", "PRG" to "Прага", "BUD" to "Будапешт",
            "RIX" to "Рига", "TLL" to "Таллин", "VNO" to "Вильнюс", "DME" to "Москва",
            "LED" to "Санкт-Петербург", "KJA" to "Красноярск", "SVX" to "Екатеринбург", "OVB" to "Новосибирск",
            "OTP" to "Бухарест", "SOF" to "София", "BEG" to "Белград", "KIV" to "Кишинёв",
            "DUS" to "Дюссельдорф", "HAM" to "Гамбург", "MUC" to "Мюнхен", "BER" to "Берлин", "TXL" to "Берлин",
            "CPH" to "Копенгаген", "OSL" to "Осло", "LHR" to "Лондон", "MAN" to "Манчестер", "CDG" to "Париж",
            "MRS" to "Марсель", "MXP" to "Милан", "FCO" to "Рим", "ZRH" to "Цюрих", "GVA" to "Женева",
            "IST" to "Стамбул", "EVN" to "Ереван", "TBS" to "Тбилиси", "GYD" to "Баку", "ALA" to "Алматы",
            "TAS" to "Ташкент", "KBP" to "Киев", "MSQ" to "Минск", "ATH" to "Афины", "LIS" to "Лиссабон",
            "MAD" to "Мадрид", "BCN" to "Барселона", "BRU" to "Брюссель", "DUB" to "Дублин",
        )
    }
}
