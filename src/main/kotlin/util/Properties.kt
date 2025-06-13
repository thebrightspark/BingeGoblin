package brightspark.util

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.kord.common.entity.Snowflake
import java.io.File
import kotlin.time.Duration.Companion.minutes

data class Properties(
	val discord: Discord,
	val twitch: Twitch
) {
	data class Discord(
		val token: String,
		val guildId: String
	) {
		val guildIdSnowflake = Snowflake(guildId)
	}

	data class Twitch(
		val clientId: String,
		val clientSecret: String,
		val pollIntervalMins: String
	) {
		val pollIntervalDuration = pollIntervalMins.toInt().minutes
	}

	companion object {
		val instance: Properties = YAMLMapper().registerKotlinModule()
			.readValue(File("config.yaml"), Properties::class.java)
	}
}
