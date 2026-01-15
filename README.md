Add it in your settings.gradle.kts at the end of repositories:

	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
Step 2. Add the dependency

	dependencies {
	        implementation("com.github.envydev001-web:android-ad-manager:Tag")
	}
