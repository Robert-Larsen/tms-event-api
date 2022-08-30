package no.nav.tms.event.api

import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import no.nav.tms.event.api.config.AzureTokenFetcher

import no.nav.tms.event.api.varsel.VarselReader
import no.nav.tms.token.support.authentication.installer.mock.installMockedAuthenticators
import no.nav.tms.token.support.tokenx.validation.mock.SecurityLevel

fun mockApi(
    authConfig: Application.() -> Unit = mockAuthBuilder(),
    httpClient: HttpClient = mockk(relaxed = true),
    azureTokenFetcher: AzureTokenFetcher
): Application.() -> Unit {
    return fun Application.() {
        api(
            authConfig = authConfig,
            httpClient = httpClient, varselReader = VarselReader(
                azureTokenFetcher = azureTokenFetcher,
                client = httpClient,
                eventHandlerBaseURL = "https://test.noe"
            )
        )
    }
}

fun mockAuthBuilder(): Application.() -> Unit = {
    installMockedAuthenticators {
        installTokenXAuthMock {
            setAsDefault = true

            alwaysAuthenticated = true
            staticUserPid = "123"
            staticSecurityLevel = SecurityLevel.LEVEL_4
        }
        installAzureAuthMock { }
    }
}

fun mockClient(aktivMockContent: String,inaktivMockContent:String, alleMockContent: String) = HttpClient(
    MockEngine() {
        when {
            it.url.encodedPath.contains("/aktiv") -> respond(
                content = aktivMockContent,
                status = HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )

            it.url.encodedPath.contains("/inaktiv") -> respond(
                content = inaktivMockContent,
                status = HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )

            it.url.encodedPath.contains("/all") -> respond(
                content = alleMockContent,
                status = HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )

            else -> respond("", HttpStatusCode.NotFound)
        }
    }
) {
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
    install(HttpTimeout)
}

internal fun <T> T.createListFromObject(size: Int): List<T> = mutableListOf<T>().also { list ->
    for (i in 1..size) {
        list.add(this)
    }
}
