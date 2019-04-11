package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

data class OpprettOppgave(
    val id: Long?,
    val tildeltEnhetsnr: String,
    val opprettetAvEnhetsnr: String,
    val endretAvEnhetsnr: String? = null,
    val journalpostId: String? = null,
    val journalpostkilde: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String,
    val ident: Ident,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String,
    val temagruppe: String? = null,
    val tema: String,
    val behandlingstema: String,
    val oppgavetype: String,
    val behandlingstype: String,
    val versjon: Int?,
    val mappeId: Long,
    val fristFerdigstillelse: LocalDate,
    val aktivDato: LocalDate,
    val opprettetTidspunkt: LocalDateTime,
    val ferdigstiltTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val prioritet: Prioritet,
    val status: Oppgavestatus,
    val statuskategori: Oppgavestatuskategori,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val metadata: Map<String, String>
)

data class OpprettOppgaveResponse(
    val id: Long?,
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
    val prioritet: Prioritet,
    val status: Oppgavestatus,
    val statuskategori: Oppgavestatuskategori,
    val metadata: Map<MetadataKey, String>,
    val ident: Ident
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

enum class Prioritet {
    HOY,
    NORM,
    LAV
}

enum class MetadataKey {
    NORM_DATO,
    REVURDERINGSTYPE,
    SOKNAD_ID,
    KRAV_ID,
    MOTTATT_DATO,
    EKSTERN_HENVENDELSE_ID,
    SKANNET_DATO
}
