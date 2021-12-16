package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val NAMESPACE = "syfosmoppgave"

val OPPRETT_OPPGAVE_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("opprett_oppgave_counter")
    .help("Registers a counter for each oppgave that is created")
    .register()

val RETRY_OPPGAVE_COUNTER: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("retry_oppgave_counter")
    .help("Registers a counter for each oppgave that is sent to the retry topic")
    .register()
