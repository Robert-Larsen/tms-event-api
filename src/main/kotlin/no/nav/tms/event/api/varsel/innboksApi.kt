package no.nav.tms.event.api.varsel

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.event.api.config.doIfValidRequest

fun Route.innboksApi(varselReader: VarselReader) {
    val aktiveVarslerPath = "innboks/detaljert/aktive"
    val inaktiveVarslerPath = "innboks/detaljert/inaktive"
    val alleVarslerPath = "innboks/detaljert/alle"

    get("/innboks/aktive") {
        doIfValidRequest { fnr ->
            call.respond(HttpStatusCode.OK, varselReader.fetchVarsel(fnr, aktiveVarslerPath).toLegacyVarsler())
        }
    }

    get("/innboks/inaktive") {
        doIfValidRequest { fnr ->
            call.respond(HttpStatusCode.OK, varselReader.fetchVarsel(fnr, inaktiveVarslerPath).toLegacyVarsler())
        }
    }

    get("/innboks/all") {
        doIfValidRequest { fnr ->
            call.respond(HttpStatusCode.OK, varselReader.fetchVarsel(fnr, alleVarslerPath).toLegacyVarsler())
        }
    }
}
