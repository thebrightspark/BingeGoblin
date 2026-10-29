package brightspark.extension

import brightspark.TwitchSupport
import brightspark.util.Properties
import brightspark.util.Storage
import com.github.twitch4j.helix.domain.Game
import com.github.twitch4j.helix.domain.Stream
import dev.kord.common.Color
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.DISCORD_GREEN
import dev.kordex.core.DISCORD_YELLOW
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.boolean
import dev.kordex.core.commands.converters.impl.channel
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.types.EphemeralInteractionContext
import dev.kordex.core.utils.scheduling.Scheduler
import dev.kordex.core.utils.scheduling.Task
import i18n.Translations
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class TwitchStreamListenerExtension : BaseExtension("twitch-stream-listener") {
	companion object {
		private val COLOUR_PURPLE = Color(0xac62ff)
	}

	private val log = KotlinLogging.logger {}

	private val scheduler = Scheduler()
	private var pollStreamsTask: Task? = null
	private var firstRun = true
	private val gameCache = mutableMapOf<String, GameInfo>()
	private val gameToChannel = mutableMapOf<String, Snowflake>()
	private var liveStreams: HashSet<String> = HashSet()

	private fun findGameId(value: String): String? = gameCache.values.find { it.name == value }?.id

	override suspend fun setup() {
		setupPollStreamsTask()
		gameToChannel.clear()
		gameToChannel.putAll(Storage.GAME_TO_CHANNEL_LIST.get())

		ephemeralSlashCommand(::EnabledArgs) {
			name = Translations.Command.Enabled.name
			description = Translations.Command.Enabled.desc
			userNeedsModeratorPermission()

			action {
				val enabled = arguments.enabled
				Storage.TWITCH_ENABLED.set(enabled)
				if (arguments.enabled)
					respondSimple("Enabled", DISCORD_GREEN)
				else
					respondSimple("Disabled", DISCORD_GREEN)
			}
		}

		ephemeralSlashCommand(::AddGameArgs) {
			name = Translations.Command.AddGame.name
			description = Translations.Command.AddGame.desc
			userNeedsModeratorPermission()

			action {
				val gameName = arguments.gameName
				val channel = arguments.channel
				getGame(gameName)?.let { game ->
					gameCache[game.id] = GameInfo(game.id, game.name, game.getBoxArtUrl(288, 384))
					gameToChannel[game.id] = channel.id
					Storage.GAME_TO_CHANNEL_LIST.set(gameToChannel)

					firstRun = true
					liveStreams.clear()
					respondSimple("Added ${game.name} streams to ${channel.mention}", DISCORD_GREEN)
				}
			}
		}

		ephemeralSlashCommand {
			name = Translations.Command.ClearGames.name
			description = Translations.Command.ClearGames.desc
			userNeedsModeratorPermission()

			action {
				gameCache.clear()
				gameToChannel.clear()
				Storage.GAME_TO_CHANNEL_LIST.set(emptyMap())

				firstRun = true
				liveStreams.clear()
				respondSimple("Cleared all games!", DISCORD_GREEN)
			}
		}

		ephemeralSlashCommand {
			name = Translations.Command.PollStreams.name
			description = Translations.Command.PollStreams.desc
			userNeedsModeratorPermission()

			action {
				pollStreams()
				respondSimple("Done")
			}
		}
	}

	private suspend fun EphemeralInteractionContext.getGame(gameName: String): Game? {
		val games = try {
			TwitchSupport.getGamesByName(gameName)
		} catch (e: Exception) {
			log.error(e) { "Error requesting games by name" }
			throw e
		}
		return when (games.size) {
			1 -> games.first()
			0 -> {
				respondSimple("No games found for the name `$gameName`", DISCORD_YELLOW)
				null
			}

			else -> {
				respondSimple(
					"Multiple games found for the name `$gameName` - please be more specific",
					DISCORD_YELLOW
				)
				null
			}
		}
	}

	private suspend fun setupPollStreamsTask() {
		log.info { "Setting up Twitch stream polling task..." }
		pollStreamsTask?.cancel()
		pollStreamsTask = scheduler.schedule(
			delay = Properties.instance.twitch.pollIntervalDuration,
			repeat = true,
			callback = ::pollStreams
		)
	}

	private suspend fun pollStreams() {
		if (!Storage.TWITCH_ENABLED.get()) {
			log.debug { "Not finding Twitch streams - disabled" }
			return
		}
		if (gameToChannel.isEmpty()) {
			log.debug { "Not finding Twitch streams - no games set" }
			return
		}

		try {
			postStreams(getStreams())
		} catch (e: Exception) {
			log.error(e) { "Error polling streams" }
		}
	}

	private suspend fun getStreams(): List<Stream> {
		log.info { "Finding Twitch streams..." }

		val streams = TwitchSupport.getStreamsForGames(gameToChannel.keys).distinctBy { it.id }

		if (firstRun) {
			firstRun = false
			log.info { "Found ${streams.size} Twitch streams - first run" }
			liveStreams = streams.map { it.id }.toHashSet()
			return streams
		} else {
			val newStreams = streams.toMutableList().apply { removeAll { liveStreams.contains(it.id) } }
			log.info { "Found ${streams.size} Twitch streams - ${newStreams.size} new" }
			liveStreams = streams.map { it.id }.toHashSet()
			return newStreams
		}
	}

	private suspend fun postStreams(streams: List<Stream>) {
		streams.groupBy { it.gameId }.forEach { (gameId, gameStreams) ->
			val channelId = gameToChannel[gameId] ?: run {
				log.warn { "No channel found for gameId $gameId" }
				return@forEach
			}
			val channel = getGuild().getChannel(channelId).asChannelOf<TextChannel>()
			val gameInfo = gameCache[gameId]!!
			if (gameStreams.size <= 5) {
				gameStreams.forEach { postStreamEmbed(channel, gameInfo, it) }
			} else {
				val gameSequence = gameStreams.asSequence()
				gameSequence.take(5).forEach { postStreamEmbed(channel, gameInfo, it) }
				postOverflowStreams(channel, gameInfo, gameSequence.drop(5))
			}
		}
	}

	private suspend fun postOverflowStreams(channel: TextChannel, gameInfo: GameInfo, streams: Sequence<Stream>) {
		val remainingCount = streams.count()
		fun Stream.toDiscordLink(): String = "[${this.userName}](${this.url})"

		val sb = StringBuilder().append(streams.first().toDiscordLink())
		if (remainingCount > 1) {
			var truncate = false
			streams.drop(1)
				.map { ", ${it.toDiscordLink()}" }
				.takeWhile {
					val tooLong = sb.length + it.length + 5 > EmbedBuilder.Limits.description
					truncate = truncate || tooLong
					return@takeWhile !truncate
				}
				.forEach { sb.append(it) }
			if (truncate)
				sb.append(", ...")
		}

		channel.createEmbed {
			title = "+ $remainingCount more ${gameInfo.name} streams:"
			description = sb.toString()
			thumbnail { url = gameInfo.boxArtUrl }
			color = COLOUR_PURPLE
		}
	}

	private suspend fun postStreamEmbed(channel: TextChannel, gameInfo: GameInfo, stream: Stream) {
		val streamUrl = stream.url
		channel.createEmbed {
			title = stream.userName
			url = streamUrl
			description = """
				$streamUrl
				**Title:** ${sanitiseTitle(stream.title)}
				**Game:** ${gameInfo.name}
				**Started:** ${stream.startedAtInstant.toShortTimeDiscordTimestamp()} (${stream.startedAtInstant.toRelativeDiscordTimestamp()})
				**Language:** ${Locale.of(stream.language).getDisplayLanguage(Locale.ENGLISH)}
				**Mature:** ${if (stream.isMature) "Yes \uD83D\uDD1E" else "No"}
				**Tags:** ${stream.tags.joinToString(" ") { "`$it`" }}
			""".trimIndent()
			thumbnail { url = gameInfo.boxArtUrl }
			image = stream.getThumbnailUrl(854, 480)
			color = COLOUR_PURPLE
		}
	}

	private fun sanitiseTitle(title: String): String =
		title.replace(Regex("[`*_~|]")) { "\\${it.value}" }

	private val Stream.url: String
		get() = "https://twitch.tv/${this.userLogin}"

	class EnabledArgs : Arguments() {
		val enabled: Boolean by boolean {
			name = Translations.Command.Enabled.Arg.Enabled.name
			description = Translations.Command.Enabled.Arg.Enabled.desc
		}
	}

	class AddGameArgs : Arguments() {
		val gameName: String by string {
			name = Translations.Command.AddGame.Arg.GameName.name
			description = Translations.Command.AddGame.Arg.GameName.desc
		}
		val channel: Channel by channel {
			name = Translations.Command.AddGame.Arg.Channel.name
			description = Translations.Command.AddGame.Arg.Channel.desc
			requireChannelType(ChannelType.GuildText)
		}
	}

	class GameInfo(val id: String, val name: String, val boxArtUrl: String)
}
