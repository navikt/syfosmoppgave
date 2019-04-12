package no.nav.syfo.model

import java.time.LocalDate

data class OpprettOppgave(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val journalpostkilde: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val orgnr: String? = null,
    val bnr: String? = null,
    val samhandlernr: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val temagruppe: String? = null,
    val tema: String? = null,
    val behandlingstema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val mappeId: Long? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
    val metadata: Map<MetadataKey, String>? = null
)

data class OppgaveResponse(
    val id: Int
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
