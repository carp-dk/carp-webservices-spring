package dk.cachet.carp.webservices.common.authorization

import dk.cachet.carp.common.application.services.ApplicationService
import dk.cachet.carp.common.infrastructure.services.ApplicationServiceRequest
import dk.cachet.carp.common.infrastructure.services.Command
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import kotlinx.coroutines.withContext

interface ApplicationServiceAuthorizer<
    TService : ApplicationService<TService, *>,
    TRequest : ApplicationServiceRequest<TService, *>,
    > {
    suspend fun TRequest.authorize()

    suspend fun TRequest.changeClaimsOnSuccess(result: Any?)

    suspend fun authorizeRequest(request: TRequest) = request.authorize()

    suspend fun grantClaimsOnSuccessfulRequest(
        request: TRequest,
        result: Any?,
    ) = request.changeClaimsOnSuccess(result)
}

class ApplicationServiceRequestAuthorizer<
    TService : ApplicationService<TService, *>,
    TRequest : ApplicationServiceRequest<TService, *>,
    >(
    private val serviceAuthorizer: ApplicationServiceAuthorizer<TService, TRequest>,
    private val decoratee: Command<TRequest>,
) : Command<TRequest> {
    override suspend fun invoke(request: TRequest): Any? =
        withContext(SecurityCoroutineContext()) {
            serviceAuthorizer.authorizeRequest(request)

            val result = decoratee.invoke(request)

            serviceAuthorizer.grantClaimsOnSuccessfulRequest(request, result)

            return@withContext result
        }
}
