package team.ctrlv.musipedia

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import team.ctrlv.musipedia.innertube.InnertubeSession

class MusiumApplication : Application() {
    lateinit var recentStore: RecentStore
        private set
    lateinit var downloadStore: DownloadStore
        private set
    lateinit var favoriteStore: FavoriteStore
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        recentStore = RecentStore(this)
        downloadStore = DownloadStore(this)
        favoriteStore = FavoriteStore(this)
        OfflineDownloads.initialize(downloadStore, appScope, this)
        StreamResolver.initialize()
        InnertubeSession.init(this)
    }
}
