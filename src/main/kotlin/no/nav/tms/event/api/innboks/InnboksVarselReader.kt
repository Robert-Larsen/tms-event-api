package no.nav.tms.event.api.innboks

import io.ktor.client.HttpClient
import no.nav.tms.event.api.common.AzureTokenFetcher
import no.nav.tms.event.api.common.retryOnConnectionLost
import no.nav.tms.event.api.config.getWithAzureAndFnr
import java.net.URL

class InnboksVarselReader(
    private val azureTokenFetcher: AzureTokenFetcher,
    private val client: HttpClient,
    eventHandlerBaseURL: String
) {

    private val activeEventsEndpoint = URL("$eventHandlerBaseURL/fetch/modia/innboks/aktive")
    private val inactiveEventsEndpoint = URL("$eventHandlerBaseURL/fetch/modia/innboks/inaktive")
    private val allEventsEndpoint = URL("$eventHandlerBaseURL/fetch/modia/innboks/all")

    suspend fun aktiveVarsler(fnr: String): List<InnboksDTO> = getExternalEvents(fnr, activeEventsEndpoint)
    suspend fun inaktiveVarsler(fnr: String): List<InnboksDTO> = getExternalEvents(fnr, inactiveEventsEndpoint)
    suspend fun alleVarsler(fnr: String): List<InnboksDTO> = getExternalEvents(fnr, allEventsEndpoint)

    private suspend fun getExternalEvents(
        fnr: String,
        completePathToEndpoint: URL
    ): List<InnboksDTO> {
        val azureToken = azureTokenFetcher.fetchTokenForEventHandler()
        return retryOnConnectionLost<List<Innboks>> {
            client.getWithAzureAndFnr(completePathToEndpoint, azureToken, fnr)
        }.map { innboks -> innboks.toDTO() }
    }
}
