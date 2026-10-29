package brightspark

import brightspark.extension.TwitchStreamListenerExtension
import brightspark.util.Properties
import dev.kordex.core.ExtensibleBot

suspend fun main(): Unit = ExtensibleBot(Properties.instance.discord.token) {
	intents(addDefaultIntents = false, addExtensionIntents = true) {}
	applicationCommands {
		defaultScope.limitToGuild(Properties.instance.discord.guildIdSnowflake)
	}
	extensions {
		add(::TwitchStreamListenerExtension)
	}
}.start()
