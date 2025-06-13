package brightspark.extension

import brightspark.util.Properties
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.core.entity.Guild
import dev.kord.core.entity.interaction.followup.PublicFollowupMessage
import dev.kord.gateway.Intent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DISCORD_BLACK
import dev.kordex.core.annotations.NotTranslated
import dev.kordex.core.checks.guildFor
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.commands.application.ApplicationCommand
import dev.kordex.core.extensions.Extension
import dev.kordex.core.types.PublicInteractionContext
import dev.kordex.core.utils.hasPermissions
import i18n.Translations
import java.time.Instant

abstract class BaseExtension(override val name: String) : Extension() {
	protected fun intents(vararg intents: Intent) {
		this.intents.addAll(intents)
	}

	/**
	 * Fail if the bot does not have the permissions
	 */
	@OptIn(NotTranslated::class)
	protected suspend fun CheckContext<*>.botHasPermissions(vararg permissions: Permission) {
		failIfNot(Translations.Error.Bot.permission.withOrdinalPlaceholders(permissions, permissions.joinToString())) {
			guildFor(event)?.getMemberOrNull(kord.selfId)?.hasPermissions(*permissions) ?: false
		}
	}

	/**
	 * Sets the default required permission for this command to be [Permission.ManageMessages]
	 *
	 * We're assuming that [Permission.ManageMessages] is a suitable permission that moderators would have
	 */
	protected fun ApplicationCommand<*>.userNeedsModeratorPermission(): Unit =
		requirePermission(Permission.ManageMessages)

	protected suspend fun getGuild(): Guild = kord.getGuild(Properties.instance.discord.guildIdSnowflake)

	protected suspend fun PublicInteractionContext.respondSimple(
		message: String,
		color: Color = DISCORD_BLACK
	): PublicFollowupMessage = respond {
		embed {
			this.color = color
			description = message
		}
	}

	protected fun Instant.toShortTimeDiscordTimestamp(): String = "<t:${this.epochSecond}:t>"

	protected fun Instant.toRelativeDiscordTimestamp(): String = "<t:${this.epochSecond}:R>"
}
