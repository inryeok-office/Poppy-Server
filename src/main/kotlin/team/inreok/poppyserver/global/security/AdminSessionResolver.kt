package team.inreok.poppyserver.global.security

import java.util.UUID

interface AdminSessionResolver {
    fun resolveAdminSessionId(token: String): UUID?
}
