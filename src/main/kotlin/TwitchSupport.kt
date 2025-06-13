package brightspark

import brightspark.util.Properties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.github.twitch4j.helix.TwitchHelix
import com.github.twitch4j.helix.TwitchHelixBuilder
import com.github.twitch4j.helix.domain.Game
import com.github.twitch4j.helix.domain.HelixPagination
import com.github.twitch4j.helix.domain.Stream
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

object TwitchSupport {
	private val log = KotlinLogging.logger {}
	private val httpClient = HttpClient(CIO) {
		install(ContentNegotiation) {
			jackson()
		}
	}
	private val twitchClient: TwitchHelix = TwitchHelixBuilder.builder()
		.withClientId(Properties.Companion.instance.twitch.clientId)
		.withClientSecret(Properties.Companion.instance.twitch.clientSecret)
		.build()
	private var token: String = ""
	private var tokenExpiry: Instant = Instant.Companion.DISTANT_PAST

	private suspend fun requestToken(): TokenResponse = httpClient.post("https://id.twitch.tv/oauth2/token") {
		setBody(FormDataContent(parameters {
			append("client_id", Properties.Companion.instance.twitch.clientId)
			append("client_secret", Properties.Companion.instance.twitch.clientSecret)
			append("grant_type", "client_credentials")
		}))
	}.let {
		if (it.status.isSuccess()) {
			it.body<TokenResponse>()
		} else {
			val errorMessage = "Failed to get Twitch token -> ${it.status}"
			log.error { errorMessage }
			error(errorMessage)
		}
	}

	private suspend fun getToken(): String {
		val now = Clock.System.now()
		if (now >= tokenExpiry) {
			log.info { "Requesting new Twitch token..." }
			val tokenResponse = requestToken()
			token = tokenResponse.accessToken
			val expiresIn = tokenResponse.expiresIn.seconds
			tokenExpiry = now + expiresIn
			log.info { "Got new Twitch token - expires in $expiresIn ($tokenExpiry)" }
		}
		return token
	}

	@Throws(Exception::class)
	suspend fun getGamesByName(gameName: String): List<Game> = handleRequest("getGamesByName") {
		twitchClient.getGames(getToken(), null, listOf(gameName), null).execute().games
	}

	@Throws(Exception::class)
	suspend fun getStreamsForGame(gameId: String): List<Stream> = getAll("getStreamsForGame") { paginationCursor ->
		twitchClient.getStreams(getToken(), paginationCursor, null, 100, listOf(gameId), null, null, null).execute()
			.let { ResultsContainer(it.streams, it.pagination) }
	}

	private suspend fun <T> getAll(
		name: String,
		getter: suspend (paginationCursor: String?) -> ResultsContainer<T>
	): List<T> {
		var results = handleRequest(name) { getter(null) }
		val resultsList = results.values.toMutableList()
		while (results.pagination?.cursor != null) {
			results = handleRequest(name) { getter(results.pagination!!.cursor) }
			resultsList.addAll(results.values)
		}
		return resultsList
	}

	private suspend fun <T> handleRequest(name: String, request: suspend () -> T): T {
		log.info { "Request $name" }
		if (log.isDebugEnabled()) {
			val result = measureTimedValue { request() }
			log.debug { "Finished request $name in ${result.duration}" }
			return result.value
		} else {
			return request()
		}
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
	private data class TokenResponse(val accessToken: String, val expiresIn: Long, val tokenType: String)

	private data class ResultsContainer<T>(val values: List<T>, val pagination: HelixPagination?)
}