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
	       implementation("com.github.YOUR_USERNAME:android-ad-manager:v1.0.0")
	}




HOW TO USE 
# Ads Manager

# Summary of Available Functions

# InterstitialAdManager
# Function	Description
loadSplashAd()   =  	Loads splash-only inter ad

showAvailableAd()  =	Shows preloaded inter ad

loadRegularAd() =	Timer + click-based regular inter ad
# NativeAdManager
# Function	Description
preloadAd()=	Loads ad but doesn’t show

showPreloadedAd()=	Shows previously loaded ad

loadAndShowAd()	=Loads & shows instantly

registerContainer()	=Registers view for auto display 





# �� 1.Remote Config Setup

# adCanRequest
    adCanRequest = mFirebaseRemoteConfig!!.getBoolean("adCanRequest_inter")

# canRequestNativeAd

    canRequestNativeAd = mFirebaseRemoteConfig!!.getBoolean("adCanRequest_native")
# canRequestBannerAd

    canRequestBannerAd = mFirebaseRemoteConfig!!.getBoolean("adCanRequest_banner")


# Configure click + timer thresholds for interstitial ads:


    timeDiffFromRemote = mFirebaseRemoteConfig!!.getLong("timeDiffFromRemote")

    AdConfig.setAdShowTimer(timeDiffFromRemote)



    var clickthreshold = mFirebaseRemoteConfig?.getLong("timerAdClickCount")?.toInt()
    .takeIf { it!! > 0 } ?: 3



    val newConfig = AdConfig.getInstance()
    AdConfig.updatethreshold(clickthreshold - 1)

# �� 2. Splash Interstitial Ads

Call loadSplashAd() in Splash Screen

    Used inside your splash navigation function:

    InterstitialAdManager.getInstance(this@SplashScreen).loadSplashAd(
    this@SplashScreen,
    adtorequestId,
    eventListener = object : AdEventListener {
        override fun onAdLoaded() {
            launchActivityAfterAd2()
        }
        override fun onAdFailed(error: String?) {
            launchActivityAfterAd2()
        }
    },
    showAd = false,
    isPremiumUser = getfromSharedPrefs(this@SplashScreen, "purchase", false) as Boolean,
    showProgress = false
)

# Show the loaded inter ad
    InterstitialAdManager.getInstance(this@SplashScreen).showAvailableAd(
    this@SplashScreen,
    getfromSharedPrefs(this@SplashScreen, "purchase", false) as Boolean,
    showAd,
    false,
    listener = object : AdEventListener {}
)

# �� 3. Native Ads – Preload 
Preload Native Ad (Example: Language Screen)

    NativeAdManager.getInstance(
        this@InAppScreen,
        getfromSharedPrefs(this, "purchase", false) as Boolean
    ).preloadAd(native_id_lang, canRequestNativeAd)

# Show Preloaded Native Ad
    NativeAdManager.getInstance(
    this@LanguagesActivity,
    getfromSharedPrefs(this, "purchase", false) as Boolean
).showPreloadedAd()

# �� 4.Show Interstitial Ads (like in InApp Screen)
Show Interstitial Ad when needed
    InterstitialAdManager.getInstance(this@InAppScreen)
    .showAvailableAd(
        this@InAppScreen,
        getfromSharedPrefs(this@InAppScreen, "purchase", false) as Boolean,
        showAd,
        false,
        listener = object : AdEventListener {}
    )

# �� 5. XML Setup for Native Ad Container

Add in your screen layout:

    <FrameLayout
    android:id="@+id/banner"
    android:layout_width="match_parent"
    android:layout_height="@dimen/_240sdp"
    android:layout_margin="@dimen/_1sdp"
    android:background="@drawable/ad_bg"
    android:padding="@dimen/_5sdp" />

    <include
    android:id="@+id/shimmer_native_ad"
    layout="@layout/item_native_ad_shimmer"
    android:layout_width="match_parent"
    android:layout_height="@dimen/_240sdp" />

# �� Register Native Ad Container (onResume)
    NativeAdManager.getInstance(
    this,
    getfromSharedPrefs(this@LanguagesActivity, "purchase", false) as Boolean
).registerContainer(
    container = banner!!,
    shimmer = shimmerNativeAd,
    layoutRes = R.layout.large_native,
    canRequestNativeAd = canRequestNativeAd
)

# �� Load & Show Native Ads
Load + Show Immediately

    NativeAdManager.getInstance(
    this,
    getfromSharedPrefs(this@LanguagesActivity, "purchase", false) as Boolean
).loadAndShowAd(native_id_lang, canRequestNativeAd)



# ➡️ 6. Interstitial Ads in Welcome Screen
    InterstitialAdManager.getInstance(this@WelcomeScreenActivity)
    .showAvailableAd(
        this@WelcomeScreenActivity,
        getfromSharedPrefs(this@WelcomeScreenActivity, "purchase", false) as Boolean,
        showAd = true,
        showProgress = false,
        listener = object : AdEventListener {
            override fun onAdDismissed() {
                InterstitialAdManager.getInstance(this@WelcomeScreenActivity)
                    .setCounters(AdConfig.getInstance().clickThreshold - 1, timeDiffFromRemote)
            }

            override fun onAdFailed(error: String?) {
                InterstitialAdManager.getInstance(this@WelcomeScreenActivity)
                    .setCounters(AdConfig.getInstance().clickThreshold - 1, timeDiffFromRemote)
            }
        }
    )

# �� 7. Regular Click + Timer Based Interstitial Ads
Call this in MainActivity (or anywhere):

    InterstitialAdManager.getInstance(this@MainActivity).loadRegularAd( this@MainActivity,
    InterstitialAd_id_main,
    showAd = true,adCanRequest = adCanRequest,
    eventListener = object :
    AdEventListener {
    override fun onAdLoaded() {
                        super.onAdLoaded()
                    }

                    override fun onAdShowed() {
                        super.onAdShowed()
                      
                    }

                    override fun onAdDismissed() {
                        super.onAdDismissed()
                        
                    }

                    override fun onAdFailed(error: String?) {
                        super.onAdFailed(error)
                        
                    }
                },
                getfromSharedPrefs(this@MainActivity, "purchase", false) as Boolean,
                showProgress = true
            )
