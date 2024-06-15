package cz.cvut.fit.litosjos.features.settings.data.data_preview_api

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.features.chapter.domain.Chapter
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.settings.domain.PreviewData
import cz.cvut.fit.litosjos.features.subject.domain.Subject
import cz.cvut.fit.litosjos.features.word.domain.Word
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApiDescription {
	@GET("users/{user}")
	suspend fun getUser(@Path("user") user: String): UserResponse

	@Serializable
	data class UserResponse(
		val type: String,
		@SerialName("avatar_url") val avatarUrl: String,
		val name: String,
		val bio: String,
		val login: String,
		val location: String,
	) {
		fun toPreviewData() = PreviewData(
			parent = Subject(Item(name = login, description = bio)),
			chapter = Chapter(Item(name = location, passedTests = 1)),
			picture = Picture(Item(name = "avatar", passedTests = 1, failedTests = 1), avatarUrl),
			word = Word(Item(name = type, description = bio, failedTests = 1), name),
		)
	}
}