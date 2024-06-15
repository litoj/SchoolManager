plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.jetbrainsKotlinAndroid)
	alias(libs.plugins.jetbrainsKotlinSerialization)
	alias(libs.plugins.googleDevtoolsKsp)
	alias(libs.plugins.gms)
	alias(libs.plugins.firebaseCrashlytics)
}

android {
	namespace = "cz.cvut.fit.litosjos"
	compileSdk = 34

	defaultConfig {
		applicationId = "cz.cvut.fit.litosjos"
		minSdk = 24
		targetSdk = 34
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		vectorDrawables {
			useSupportLibrary = true
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
		debug {
			isMinifyEnabled = false
			isShrinkResources = false
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs += listOf(
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
		)
	}
	buildFeatures {
		compose = true
	}
	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.13"
	}
	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

dependencies {

	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)
	implementation(libs.retrofit)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.retrofit.serialization)
	implementation(libs.coil.compose)

	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(libs.androidx.datastore.preferences)

	implementation(platform(libs.koin.bom))
	implementation(libs.koin.android)
	implementation(libs.koin.compose)

	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.crashlytics)
	implementation(libs.firebase.analytics)

	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	debugImplementation(libs.androidx.ui.tooling)
	debugImplementation(libs.androidx.ui.test.manifest)
}