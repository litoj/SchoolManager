package cz.cvut.fit.litosjos.features.settings.domain

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
	val toggleAll: Boolean = false,
	val showTranslated: Boolean = true,
	val descriptionLineCount: Int = 0,
	val testSuccessColorIntensity: Int = 0x80,
	val testSuccessColorizeBackground: Boolean = false,
	val untestedItemColorize: Boolean = true,
)

fun settingsTypedKeys() = mapOf(
	"toggleAll" to Boolean::class,
	"showTranslated" to Boolean::class,
	"descriptionLineCount" to Int::class,
	"testSuccessColorIntensity" to Int::class,
	"testSuccessColorizeBackground" to Boolean::class,
	"untestedItemColorize" to Boolean::class
)

fun Map<String, String>.toSettingsOrDefault(default: Settings) = Settings(
	toggleAll = get("toggleAll")?.toBoolean() ?: default.toggleAll,
	showTranslated = get("showTranslated")?.toBoolean() ?: default.showTranslated,
	descriptionLineCount = get("descriptionLineCount")?.toInt() ?: default.descriptionLineCount,
	testSuccessColorIntensity = get("testSuccessColorIntensity")?.toInt()
		?: default.testSuccessColorIntensity,
	testSuccessColorizeBackground = get("testSuccessColorizeBackground")?.toBoolean()
		?: default.testSuccessColorizeBackground,
	untestedItemColorize = get("untestedItemColorize")?.toBoolean() ?: default.untestedItemColorize
)

fun Settings.toMap() = mapOf(
	"toggleAll" to toggleAll,
	"showTranslated" to showTranslated,
	"descriptionLineCount" to descriptionLineCount,
	"testSuccessColorIntensity" to testSuccessColorIntensity,
	"testSuccessColorizeBackground" to testSuccessColorizeBackground,
	"untestedItemColorize" to untestedItemColorize
)