package dk.cachet.carp.webservices.common.notification.service

import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.exception.advices.ErrorContext
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel

/** The Interface [INotificationService]. */
interface INotificationService {
    /**
     * Posts [errorResponse] to the exceptions Teams channel, with the [context] (user, study, deployment, ...)
     * of the request that failed.
     */
    fun sendExceptionNotification(
        errorResponse: CarpErrorResponse,
        context: ErrorContext? = null,
    )

    /** The [sendAlertOrGeneralNotification] interface to notify specific alerts or notifications in slack channel. */
    fun sendAlertOrGeneralNotification(
        notification: String,
        channelToSendTo: TeamsChannel,
    )
}
