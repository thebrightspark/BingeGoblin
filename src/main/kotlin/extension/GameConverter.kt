package brightspark.extension

import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.entity.interaction.StringOptionValue
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kordex.core.annotations.converters.Converter
import dev.kordex.core.annotations.converters.ConverterType
import dev.kordex.core.commands.Argument
import dev.kordex.core.commands.CommandContext
import dev.kordex.core.commands.OptionWrapper
import dev.kordex.core.commands.converters.SingleConverter
import dev.kordex.core.commands.converters.Validator
import dev.kordex.core.commands.wrapOption
import dev.kordex.i18n.Key
import dev.kordex.parser.StringParser
import i18n.Translations

@Converter(
	names = ["game"],
	types = [ConverterType.SINGLE, ConverterType.CHOICE],
	builderConstructorArguments = ["val gameIdFinder: (String) -> String"]
)
class GameConverter(
	val gameIdFinder: (String) -> String?,
	override var validator: Validator<String> = null
) : SingleConverter<String>() {
	override val signatureType: Key = Translations.Converter.gameId

	override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
		val arg = named ?: parser?.parseNext()?.data ?: return false
		parsed = gameIdFinder(arg) ?: return false
		return true
	}

	override suspend fun toSlashOption(arg: Argument<*>): OptionWrapper<StringChoiceBuilder> =
		wrapOption(arg.displayName, arg.description) {
//			gameCache.values.forEach { choice(it.name, it.name) }
			required = true
		}

	override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
		val optionValue = (option as? StringOptionValue)?.value ?: return false
		parsed = gameIdFinder(optionValue) ?: return false
		return true
	}
}
