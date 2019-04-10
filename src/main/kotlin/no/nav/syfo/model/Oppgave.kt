package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Oppgave(
    val id: Long?,
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String,
    val endretAvEnhetsnr: String? = null,
    val journalpostId: String? = null,
    val journalpostkilde: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val saksreferanse: String,
    val temagruppe: String? = null,
    val beskrivelse: String,
    val tema: String,
    val behandlingstema: String,
    val oppgavetype: String,
    val behandlingstype: String,
    val versjon: Int?,
    val mappeId: Long? = null,
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
    val metadata: Map<String, String>? = null,
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
