package no.nav.syfo.model

import java.time.LocalDate

data class OpprettOppgave(
    val tildeltEnhetsnr: String,
    val opprettetAvEnhetsnr: String,
    val aktoerId: String,
    val journalpostId: String,
    val journalpostkilde: String,
    val behandlesAvApplikasjon: String,
    val saksreferanse: String,
    val orgnr: String,
    val bnr: String? = null,
    val samhandlernr: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String,
    val temagruppe: String,
    val tema: String,
    val behandlingstema: String,
    val oppgavetype: String,
    val behandlingstype: String,
    val mappeId: Int,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,
    val prioritet: String,
    val metadata: Map<String, String>
)
