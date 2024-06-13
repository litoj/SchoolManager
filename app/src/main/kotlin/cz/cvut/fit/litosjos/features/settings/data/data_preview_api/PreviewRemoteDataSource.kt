package cz.cvut.fit.litosjos.features.settings.data.data_preview_api

class PreviewRemoteDataSource(private val api: GithubApiDescription) {

	suspend fun getUser(user: String) = try {
		api.getUser(user).toPreviewData()
	} catch (e: Exception) {
		GithubApiDescription.UserResponse(
			type = "404",
			avatarUrl = "https://cdn.pixabay.com/photo/2017/03/09/12/31/error-2129569_1280.jpg",
			bio = e.message ?: "unknown error",
			login = user,
			name = "Page Not Found",
			location = "Error"
		).toPreviewData()
	}
}