package no.nav.tms.event.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.tms.event.api.config.AzureTokenFetcher
import no.nav.tms.event.api.varsel.Varsel
import org.amshove.kluent.internal.assertFalse
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val objectmapper = ObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiTest {
    private val tokenFetchMock = mockk<AzureTokenFetcher>(relaxed = true)
    private val azureToken = "TokenSmoken"

    private val dummyFnr = "12345678910"

    @BeforeAll
    fun setup() {
        coEvery {
            tokenFetchMock.fetchTokenForEventHandler()
        } returns azureToken
    }

    @ParameterizedTest
    @ValueSource(strings = ["beskjed", "oppgave", "innboks"])
    fun `Henter aktive varsler fra eventhandler`(type: String) {
        val endpoint = "/tms-event-api/$type/aktive"
        val (aktiveMockresponse, aktiveExpectedResult) = mockContent(
            førstBehandlet = ZonedDateTime.now().minusDays(1),
            sistOppdatert = ZonedDateTime.now(),
            synligFremTil = ZonedDateTime.now().plusDays(10),
            size = 5,
        )

        testApplication {
            mockApi(
                httpClient = mockClientWithEndpointValidation(
                    "/aktiv",
                    aktiveMockresponse,
                ),
                azureTokenFetcher = tokenFetchMock,
            )
            assertVarselApiCall(endpoint, dummyFnr, aktiveExpectedResult)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["beskjed", "oppgave", "innboks"])
    fun `Henter inaktive varsler fra eventhandler og gjør de om til DTO`(type: String) {
        val (inaktivMockresponse, inaktiveExpectedResult) = mockContent(
            førstBehandlet = ZonedDateTime.now().minusDays(1),
            sistOppdatert = ZonedDateTime.now(),
            synligFremTil = null,
            size = 2,
        )

        testApplication {
            mockApi(
                httpClient = mockClientWithEndpointValidation(
                    "inaktive",
                    inaktivMockresponse,
                ),
                azureTokenFetcher = tokenFetchMock,
            )
            assertVarselApiCall("/tms-event-api/$type/inaktive", dummyFnr, inaktiveExpectedResult)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["beskjed", "oppgave", "innboks"])
    fun `Henter alle varsler fra eventhandler og gjør de om til DTO`(type: String) {
        val (alleMockresponse, alleExpectedResult) = mockContent(
            førstBehandlet = ZonedDateTime.now().minusDays(1),
            sistOppdatert = ZonedDateTime.now(),
            synligFremTil = ZonedDateTime.now().plusDays(3),
            size = 6,
        )

        testApplication {
            mockApi(
                httpClient = mockClientWithEndpointValidation(
                    "all",
                    alleMockresponse,
                ),
                azureTokenFetcher = tokenFetchMock,
            )
            assertVarselApiCall("/tms-event-api/$type/all", dummyFnr, alleExpectedResult)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["beskjed", "oppgave", "innboks"])
    fun `bad request for ugyldig fødselsnummer i header`(varselType: String) {
        testApplication {
            mockApi(
                httpClient = mockClient(""),
                azureTokenFetcher = tokenFetchMock,
            )
            client.get("/tms-event-api/$varselType/aktive").status shouldBe HttpStatusCode.BadRequest
            client.get {
                url("/tms-event-api/$varselType/inaktive")
                header("fodselsnummer", "1234")
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }
}

private suspend fun ApplicationTestBuilder.assertVarselApiCall(
    endpoint: String,
    fnr: String,
    expectedResult: List<Varsel>,
) {
    client.get {
        url(endpoint)
        header("fodselsnummer", fnr)
    }.also {
        it.status shouldBeEqualTo HttpStatusCode.OK
        assertContent(it.bodyAsText(), expectedResult)
    }
}

private fun assertContent(content: String?, expectedResult: List<Varsel>) {
    val jsonObjects = objectmapper.readTree(content)
    jsonObjects.size() shouldBeEqualTo expectedResult.size
    val expectedObject = expectedResult.first()
    jsonObjects.first().also { resultObject ->
        resultObject["fodselsnummer"].textValue() shouldBeEqualTo expectedObject.fodselsnummer
        resultObject["grupperingsId"].textValue() shouldBeEqualTo expectedObject.grupperingsId
        resultObject["eventId"].textValue() shouldBeEqualTo expectedObject.eventId
        resultObject["produsent"].textValue() shouldBeEqualTo expectedObject.produsent
        resultObject["sikkerhetsnivaa"].asInt() shouldBeEqualTo expectedObject.sikkerhetsnivaa
        resultObject["tekst"].textValue() shouldBeEqualTo expectedObject.tekst
        resultObject["link"].textValue() shouldBeEqualTo expectedObject.link
        resultObject["aktiv"].asBoolean() shouldBeEqualTo expectedObject.aktiv
        assertZonedDateTime(resultObject, expectedObject.synligFremTil, "synligFremTil")
        assertZonedDateTime(resultObject, expectedObject.forstBehandlet, "forstBehandlet")
        assertZonedDateTime(resultObject, expectedObject.sistOppdatert, "sistOppdatert")
    }
}

private fun assertZonedDateTime(jsonNode: JsonNode?, expectedDate: ZonedDateTime?, key: String) {
    if (expectedDate != null) {
        val resultDate = ZonedDateTime.parse(jsonNode?.get(key)?.textValue()).truncatedTo(ChronoUnit.MINUTES)
        assertFalse(resultDate == null, "$key skal ikke være null")
        resultDate.toString() shouldBeEqualTo expectedDate.truncatedTo(ChronoUnit.MINUTES).toString()
    } else {
        jsonNode?.get(key)?.textValue() shouldBe null
    }
}

private fun mockContent(
    førstBehandlet: ZonedDateTime,
    sistOppdatert: ZonedDateTime,
    synligFremTil: ZonedDateTime? = null,
    size: Int,
): Pair<String, List<Varsel>> {
    val synligFremTilString = synligFremTil?.let {
        """"${synligFremTil.withFixedOffsetZone()}""""
    } ?: "null"

    return Pair(
        """  {
        "fodselsnummer": "123",
        "grupperingsId": "",
        "eventId": "",
        "forstBehandlet": "${førstBehandlet.withFixedOffsetZone()}",
        "produsent": "",
        "sikkerhetsnivaa": 0,
        "sistOppdatert": "${sistOppdatert.withFixedOffsetZone()}",
        "synligFremTil": $synligFremTilString
        "tekst": "Tadda vi tester",
        "link": "",
        "appnavn": "appappapp",
        "aktiv": false,
        "eksternVarslingSendt": true,
        "eksternVarslingKanaler":["SMS", "EPOST"]
      }""".jsonArray(size),
        Varsel(
            fodselsnummer = "123",
            grupperingsId = "",
            eventId = "",
            forstBehandlet = førstBehandlet.withFixedOffsetZone(),
            produsent = "",
            sikkerhetsnivaa = 0,
            sistOppdatert = sistOppdatert.withFixedOffsetZone(),
            tekst = "Tadda vi tester",
            link = "",
            aktiv = false,
            synligFremTil = synligFremTil?.withFixedOffsetZone(),
            eksternVarslingSendt = true,
            eksternVarslingKanaler = listOf("SMS", "EPOST"),
        ) * size,
    )
}

private fun String.jsonArray(size: Int): String =
    (1..size).joinToString(separator = ",", prefix = "[", postfix = "]") { this }
