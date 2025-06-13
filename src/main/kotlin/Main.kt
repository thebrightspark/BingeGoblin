package brightspark

import brightspark.extension.TwitchStreamListener
import brightspark.util.Properties
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.checks.inGuild

suspend fun main(): Unit = ExtensibleBot(Properties.instance.discord.token) {
	intents(addDefaultIntents = false, addExtensionIntents = true) {}
	applicationCommands {
		slashCommandCheck { inGuild(Properties.instance.discord.guildIdSnowflake) }
	}
	extensions {
		add { TwitchStreamListener }
	}
}.start()
