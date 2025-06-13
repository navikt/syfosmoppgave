package no.nav.syfo.model

import java.time.LocalDate

data class OpprettOppgave(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val behandlingstema: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
)

data class OpprettOppgaveResponse(
    val id: Int,
)

data class OppgaveResponse(
    val antallTreffTotalt: Int,
    val oppgaver: List<Oppgave>,
)

data class Bruker(
    val ident: String?,
    val type: String?,
)

data class Oppgave(
    val id: Int,
    val tildeltEnhetsnr: String?,
    val aktoerId: String?,
    val journalpostId: String?,
    val tema: String?,
    val oppgavetype: String?,
    val endretAvEnhetsnr: String?,
    val opprettetAvEnhetsnr: String?,
    val behandlesAvApplikasjon: String?,
    val orgnr: String?,
    val tilordnetRessurs: String?,
    val beskrivelse: String?,
    val behandlingstema: String?,
    val behandlingstype: String?,
    val versjon: Int?,
    val mappeId: Int?,
    val opprettetAv: String?,
    val endretAv: String?,
    val prioritet: String?,
    val status: String?,
    val fristFerdigstillelse: String?,
    val aktivDato: String?,
    val opprettetTidspunkt: String?,
    val ferdigstiltTidspunkt: String?,
    val endretTidspunkt: String?,
    val bruker: Bruker?
)

data class OppgaveResultat(
    val oppgaveId: Int,
    val duplikat: Boolean,
)
