package dk.cachet.carp.webservices.admin.service

import dk.cachet.carp.webservices.admin.dto.ServerInfoDto

interface ServerInfoService {
    suspend fun getServerInfo(): ServerInfoDto
}
