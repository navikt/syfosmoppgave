package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

data class OpprettOppgave(
    val id: Long? = null,
    var tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String,
    val endretAvEnhetsnr: String,
    val journalpostId: String,
    val journalpostkilde: String,
    val behandlesAvApplikasjon: String,
    val tilordnetRessurs: String,
    val saksreferanse: String,
    val temagruppe: String,
    val beskrivelse: String,
    val tema: String,
    val behandlingstema: String,
    val oppgavetype: String,
    val behandlingstype: String,
    val versjon: Int?,
    val mappeId: Long?,
    val fristFerdigstillelse: LocalDate,
    val aktivDato: LocalDate,
    val opprettetTidspunkt: LocalDateTime,
    val endretTidspunkt: LocalDateTime,
    val ferdigstiltTidspunkt: LocalDateTime,
    val opprettetAv: String,
    val endretAv: String,
    val prioritet: String,
    val status: Oppgavestatus,
    val statuskategori: Oppgavestatuskategori,
    val metadata: Map<String, String>,
    val ident: Ident
)

data class OpprettOppgaveResponse(
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
    val metadata: Map<String, String>,

    val id: String,
    val opprettetTidspunkt: LocalDateTime,
    val opprettetAv: String,
    val endretAv: String,
    val ferdigstiltTidspunkt: LocalDateTime?,
    val endretTidspunkt: LocalDateTime?,
    val status: String
)

data class Ident(
    val identType: IdentType,
    val verdi: String
)

enum class IdentType {
    AKTOERID,
    ORGNR,
    SAMHANDLERNR,
    BNR
}

enum class Oppgavestatuskategori {
    AAPEN,
    AVSLUTTET
}

enum class Oppgavestatus {
    OPPRETTET,
    AAPNET,
    UNDER_BEHANDLING,
    FERDIGSTILT,
    FEILREGISTRERT
}