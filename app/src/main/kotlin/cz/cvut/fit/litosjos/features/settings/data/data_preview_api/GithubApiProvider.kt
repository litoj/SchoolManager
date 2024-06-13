package cz.cvut.fit.litosjos.features.settings.data.data_preview_api

import android.content.Context
import android.net.ConnectivityManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException

class NoInternetException : IOException()

class NetworkConnectionInterceptor(private val context: Context) : Interceptor {
	@Throws(IOException::class)
	override fun intercept(chain: Interceptor.Chain): Response {
		if (!isConnected) throw NoInternetException()
		return chain.proceed(chain.request())
	}

	val isConnected: Boolean
		get() {
			val connectivityManager =
				context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			return connectivityManager.activeNetwork != null
		}
}

object GithubApiProvider {
	private val json: Json = Json { ignoreUnknownKeys = true }

	@OptIn(ExperimentalSerializationApi::class)
	fun provide(context: Context): GithubApiDescription =
		Retrofit.Builder().baseUrl("https://api.github.com/")
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType())).client(
				OkHttpClient.Builder().addInterceptor(NetworkConnectionInterceptor(context)).build()
			).build().create(GithubApiDescription::class.java)
}
