package no.nav.helse.api

import com.fasterxml.jackson.core.util.ByteArrayBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.*
import no.nav.helse.io.IO
import no.nav.helse.vedtak.VedtakMediator
import no.nav.helse.vedtak.VedtakMessage
import no.nav.helse.vedtak.VedtakPdfPayload
import no.nav.helse.io.mockUtbetalinger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

import java.util.*
import kotlin.test.assertEquals

class AdminApiTest {

    private val mediator: VedtakMediator = mockk(relaxed = true)

    @Test
    fun `ende til ende`() {
        val format = Json { ignoreUnknownKeys = true }
        val pdfClient: PdfClient = mockk(relaxed = true)
        val joarkClient: JoarkClient = mockk(relaxed = true)
        val vedtakMediator = VedtakMediator(pdfClient, joarkClient)
        val slot = mutableListOf<VedtakPdfPayload>()

        coEvery { pdfClient.hentVedtakPdf(capture(slot)) } returns ""
        coEvery { joarkClient.opprettJournalpost(any(), any()) } returns true

        withTestApplication({
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            basicAuthentication("hunter2")
            routing { authenticate("admin") { adminGrensesnitt(vedtakMediator) } }
        }) {
            with(handleRequest(HttpMethod.Post, "/admin/utbetaling") {
                val userpass = Base64.getEncoder().encodeToString("admin:hunter2".toByteArray())
                addHeader(HttpHeaders.Authorization, "Basic $userpass")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.ContentEncoding, StandardCharsets.UTF_8.toString())
                val body = vedtakAsByteArray(mockUtbetalinger)
                println("bodyprint" + body)
                setBody(body)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())

                coVerify(exactly = mockUtbetalinger.size) {
                    joarkClient.opprettJournalpost(any(), any())
                    pdfClient.hentVedtakPdf(any())
                }

                mockUtbetalinger.forEachIndexed { index, it ->
                    val vedtak: IO.Vedtak = format.decodeFromString(it)
                    assertEquals(slot[index].fagsystemId, vedtak.utbetalt[0].fagsystemId)
                    assertEquals(
                        slot[index].linjer.getOrNull(0)?.fom,
                        vedtak.utbetalt.getOrNull(0)?.utbetalingslinjer?.getOrNull(0)?.fom
                    )
                }
            }
        }
    }

    @Test
    fun `oppslag på utbetaling`() {
        withTestApplication({
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            basicAuthentication("hunter2")
            routing { authenticate("admin") { adminGrensesnitt(mediator) } }
        }) {
            with(handleRequest(HttpMethod.Post, "/admin/utbetaling") {
                val userpass = Base64.getEncoder().encodeToString("admin:hunter2".toByteArray())
                addHeader(HttpHeaders.Authorization, "Basic $userpass")
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(vedtakAsByteArray(mockUtbetalinger))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())

                val format = Json { ignoreUnknownKeys = true }
                val vedtak: List<IO.Vedtak> = mockUtbetalinger.map(format::decodeFromString)
                verify(exactly = 1) {
                    mediator.opprettVedtak(VedtakMessage(vedtak[0]))
                }
            }
        }
    }

    @Test
    fun `unaut️horized uten autentisering`() {
        withTestApplication({
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            basicAuthentication("hunter2")
            routing { authenticate("admin") { adminGrensesnitt(mediator) } }
        }) {
            with(handleRequest(HttpMethod.Post, "/admin/utbetaling") {
                val userpass = Base64.getEncoder().encodeToString("admin:🅱️".toByteArray())
                addHeader(HttpHeaders.Authorization, "Basic $userpass")
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(vedtakAsByteArray(mockUtbetalinger))
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
                verify(exactly = 0) { mediator.opprettVedtak(any()) }
            }
        }
    }

    private fun vedtakAsByteArray(list: List<String>): String {
        val byteArray = ByteArrayBuilder()
        byteArray.write("[".toByteArray())
        list.forEachIndexed { index, it ->
            byteArray.write(it.toByteArray())
            if (index != list.size - 1) {
                byteArray.write(",".toByteArray())
            }
        }
        byteArray.write("]".toByteArray())
        return byteArray.toString()
    }

    @AfterEach
    fun setupAfterEach() {
        clearMocks(mediator)
    }
}
