package com.majidkabir.aulaplus.core

/** Endpoints and OIDC client used by the official Aula app (see nickknissen/aula docs). */
object AulaConfig {
    const val AUTH_BASE = "https://login.aula.dk"
    const val AUTHORIZE_URL = "$AUTH_BASE/simplesaml/module.php/oidc/authorize.php"
    const val TOKEN_URL = "$AUTH_BASE/simplesaml/module.php/oidc/token.php"
    const val REDIRECT_URI = "https://app-private.aula.dk"
    const val REDIRECT_HOST = "app-private.aula.dk"
    const val CLIENT_ID = "_99949a54b8b65423862aac1bf629599ed64231607a"
    const val SCOPE = "aula-sensitive"
    const val API_BASE = "https://www.aula.dk/api/v"
    const val DEFAULT_API_VERSION = 25
    const val CSRF_COOKIE = "Csrfp-Token"
    const val CSRF_HEADER = "csrfp-token"
    const val MIN_UDDANNELSE_API = "https://api.minuddannelse.net/aula"
    const val MEEBOOK_API = "https://app.meebook.com/aulaapi"

    const val WIDGET_MEEBOOK = "0004"
    const val WIDGET_MU_UGEPLAN = "0029"
    const val WIDGET_MU_SSO = "0023"
    const val WIDGET_MU_TASKS = "0030"
}
