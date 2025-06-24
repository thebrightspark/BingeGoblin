package brightspark.extension

import brightspark.TwitchSupport
import brightspark.util.Properties
import brightspark.util.Storage
import com.github.twitch4j.helix.domain.Stream
import dev.kord.common.Color
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
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.scheduling.Scheduler
import dev.kordex.core.utils.scheduling.Task
import i18n.Translations
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

object TwitchStreamListener : BaseExtension("twitch-stream-listener") {
	private val log = KotlinLogging.logger {}
	private val purpleColor = Color(0xac62ff)

	private val scheduler: Scheduler = Scheduler()
	private var pollStreamsTask: Task? = null
	private var firstRun = true
	private var liveStreams: HashSet<String> = HashSet()

	override suspend fun setup() {
		setupPollStreamsTask()

		publicSlashCommand(::EnabledArgs) {
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

		publicSlashCommand(::SetChannelArgs) {
			name = Translations.Command.SetChannel.name
			description = Translations.Command.SetChannel.desc
			userNeedsModeratorPermission()

			action {
				val channel = arguments.channel
				Storage.CHANNEL_ID.set(channel.id)
				respondSimple("Channel set to ${channel.mention}", DISCORD_GREEN)
			}
		}

		publicSlashCommand(::SetGameArgs) {
			name = Translations.Command.SetGame.name
			description = Translations.Command.SetGame.desc
			userNeedsModeratorPermission()

			action {
				val name = arguments.gameName
				val games = try {
					TwitchSupport.getGamesByName(name)
				} catch (e: Exception) {
					log.error(e) { "Error requesting games by name" }
					throw e
				}
				when (games.size) {
					0 -> respondSimple("No games found for the name `$name`", DISCORD_YELLOW)
					1 -> games.first().let {
						Storage.GAME_NAME.set(it.name)
						Storage.GAME_ID.set(it.id)
						firstRun = true
						liveStreams.clear()
						respondSimple("Game set to '${it.name}' (`${it.id}`)", DISCORD_GREEN)
					}

					else -> respondSimple(
						"Multiple games found for the name `$name` - please be more specific",
						DISCORD_YELLOW
					)
				}
			}
		}

		publicSlashCommand {
			name = Translations.Command.PollStreams.name
			description = Translations.Command.PollStreams.desc
			userNeedsModeratorPermission()

			action {
				pollStreams()
				respondSimple("Done")
			}
		}
	}

	private suspend fun setupPollStreamsTask() {
		log.info { "Setting up Twitch stream polling task..." }
		pollStreamsTask?.cancel()
		scheduler.schedule(
			delay = Properties.Companion.instance.twitch.pollIntervalDuration,
			repeat = true,
			callback = ::pollStreams
		)
	}

	private suspend fun pollStreams() {
		if (!Storage.TWITCH_ENABLED.get()) {
			log.debug { "Not finding Twitch streams - disabled" }
			return
		}
		if (Storage.CHANNEL_ID.get() == Snowflake.Companion.min) {
			log.debug { "Not finding Twitch streams - no channel set" }
			return
		}

		try {
			getStreams()?.let { postStreams(it) }
		} catch (e: Exception) {
			log.error(e) { "Error polling streams" }
		}
	}

	private suspend fun getStreams(): GetStreamsResult? {
		if (Storage.GAME_ID.get().isBlank()) {
			log.debug { "Not finding Twitch streams - no game set" }
			return null
		}
		log.info { "Finding Twitch streams..." }

		val streams = TwitchSupport.getStreamsForGame(Storage.GAME_ID.get()).distinctBy { it.id }

		if (firstRun) {
			firstRun = false
			log.info { "Found ${streams.size} Twitch streams - first run" }
			liveStreams = streams.map { it.id }.toHashSet()
			return GetStreamsResult(streams, true)
		} else {
			val newStreams = streams.toMutableList().apply { removeAll { liveStreams.contains(it.id) } }
			log.info { "Found ${streams.size} Twitch streams - ${newStreams.size} new" }
			liveStreams = streams.map { it.id }.toHashSet()
			return GetStreamsResult(newStreams, false)
		}
	}

	private suspend fun postStreams(result: GetStreamsResult) {
		val streams = result.streams
		val channel = getGuild().getChannel(Storage.CHANNEL_ID.get()).asChannelOf<TextChannel>()
		if (result.firstRun) {
			channel.createEmbed {
				description = "First poll of ${Storage.GAME_NAME.get()} streams: found ${streams.size}"
				color = DISCORD_GREEN
			}
		} else {
			streams.asSequence().let { sequence ->
				sequence.take(5).forEach { postStreamEmbed(channel, it) }

				val remainingStreamsSequence = sequence.drop(5)
				val remainingCount = remainingStreamsSequence.count()
				val sb = StringBuilder().append("+ ").append(remainingCount).append(" more streams:\n\n")
				if (remainingCount > 0) {
					fun Stream.toDiscordLink(): String = "[${this.userName}](${this.url})"

					sb.append(remainingStreamsSequence.first().toDiscordLink())
					if (remainingCount > 1) {
						var truncate = false
						remainingStreamsSequence
							.drop(1)
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
						description = sb.toString()
						color = purpleColor
					}
				}
			}
		}
	}

	private suspend fun postStreamEmbed(channel: TextChannel, stream: Stream) {
		val streamUrl = stream.url
		channel.createEmbed {
			title = stream.userName
			url = streamUrl
			description = """
				$streamUrl
				**Title:** ${stream.title}
				**Started:** ${stream.startedAtInstant.toShortTimeDiscordTimestamp()} (${stream.startedAtInstant.toRelativeDiscordTimestamp()})
				**Language:** ${Locale.of(stream.language).getDisplayLanguage(Locale.ENGLISH)}
				**Mature:** ${if (stream.isMature) "Yes" else "No"}
				**Tags:** ${stream.tags.joinToString(" ") { "`$it`" }}
			""".trimIndent()
			image = stream.getThumbnailUrl(854, 480)
			color = purpleColor
		}
	}

	private val Stream.url: String
		get() = "https://twitch.tv/${this.userLogin}"

	class EnabledArgs : Arguments() {
		val enabled: Boolean by boolean {
			name = Translations.Command.Enabled.Arg.Enabled.name
			description = Translations.Command.Enabled.Arg.Enabled.desc
		}
	}

	class SetChannelArgs : Arguments() {
		val channel: Channel by channel {
			name = Translations.Command.SetChannel.Arg.Channel.name
			description = Translations.Command.SetChannel.Arg.Channel.desc
		}
	}

	class SetGameArgs : Arguments() {
		val gameName: String by string {
			name = Translations.Command.SetGame.Arg.GameName.name
			description = Translations.Command.SetGame.Arg.GameName.desc
		}
	}

	class GetStreamsResult(val streams: Collection<Stream>, val firstRun: Boolean)
}