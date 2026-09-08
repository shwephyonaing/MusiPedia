package team.ctrlv.musipedia

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import team.ctrlv.musipedia.innertube.InnertubeSession

class MusiumApplication : Application() {
    lateinit var recentStore: RecentStore
        private set
    lateinit var downloadStore: DownloadStore
        private set
    lateinit var favoriteStore: FavoriteStore
        private set
    lateinit var tasteStore: TasteStore
        private set
    lateinit var tasteCatalogStore: TasteCatalogStore
        private set
    lateinit var equalizerStore: EqualizerStore
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        recentStore = RecentStore(this)
        downloadStore = DownloadStore(this)
        favoriteStore = FavoriteStore(this)
        tasteStore = TasteStore(this)
        tasteCatalogStore = TasteCatalogStore(this)
        equalizerStore = EqualizerStore(this)
        OfflineDownloads.initialize(downloadStore, appScope, this)
        StreamResolver.initialize()
        InnertubeSession.init(this)
        // Pull latest verified artists from GitHub; falls back to bundled JSON offline.
        appScope.launch(Dispatchers.IO) {
            tasteCatalogStore.refresh(force = false)
        }
    }
}
