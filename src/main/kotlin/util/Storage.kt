package brightspark.util

import dev.kord.common.entity.Snowflake
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

object Storage {
	private val log = KotlinLogging.logger {}

	private val path = Paths.get("store.properties")
	private val keys: MutableMap<String, StoreKey<out Any>> = mutableMapOf()
	private val store: MutableMap<StoreKey<out Any>, String> = mutableMapOf()

	val TWITCH_ENABLED = StoreKey("TwitchEnabled", BoolStoreType, false)
	val CHANNEL_ID = StoreKey("ChannelId", SnowflakeStoreType, Snowflake.min)
	val GAME_NAME = StoreKey("TwitchGameName", StringStoreType, "")
	val GAME_ID = StoreKey("TwitchGameId", StringStoreType, "")

	init {
		readStore()
	}

	data class StoreKey<T : Any>(val name: String, val type: StoreType<T>, val initial: T) {
		val initialString: String = type.serialiser(initial)

		init {
			keys[name] = this
		}

		fun get(): T =
			this.type.deserialiser(store.getValue(this))

		fun set(value: T) {
			val stringValue = this.type.serialiser(value)
			log.info { "Setting store $name to '$stringValue'" }
			store[this] = stringValue
			writeStore()
		}
	}

	sealed class StoreType<T : Any>(val serialiser: (T) -> String, val deserialiser: (String) -> T)
	object BoolStoreType : StoreType<Boolean>({ it.toString() }, { it.toBoolean() })
	object IntStoreType : StoreType<Int>({ it.toString() }, { it.toInt() })
	object StringStoreType : StoreType<String>({ it }, { it })
	object SnowflakeStoreType : StoreType<Snowflake>({ it.toString() }, { Snowflake(it) })

	private fun readStore() {
		log.debug { "Reading store..." }
		if (path.exists()) {
			Files.lines(path).forEach {
				val (key, value) = it.split(Regex("\\s+=\\s+"), limit = 2)
				val storeKey = keys.getValue(key)
				store[storeKey] = value
			}
		}

		val keysSequence = keys.values.asSequence().filter { !store.containsKey(it) }
		val count = keysSequence.count()
		if (count > 0) {
			log.debug { "$count unset keys found" }
			keysSequence.forEach {
				log.debug { "Setting store ${it.name} to initial value '${it.initialString}'" }
				store[it] = it.initialString
			}
			writeStore()
		}
	}

	private fun writeStore() {
		log.debug { "Writing store..." }
		val string = store.entries.joinToString("\n") { "${it.key.name} = ${it.value}" }
		Files.writeString(path, string)
	}
}
