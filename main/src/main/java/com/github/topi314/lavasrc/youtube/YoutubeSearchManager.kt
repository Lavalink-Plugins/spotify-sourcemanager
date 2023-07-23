package com.github.topi314.lavasrc.youtube

import com.github.topi314.lavasearch.SearchSourceManager
import com.github.topi314.lavasearch.protocol.*
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer
import com.github.topi314.lavasrc.youtube.innertube.requestMusicAutoComplete
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer.NavigationEndpoint.BrowseEndpoint.Configs.Config.Type as PageType

private fun MusicResponsiveListItemRenderer.NavigationEndpoint.toUrl() = when {
    browseEndpoint != null -> when (browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType) {
        PageType.MUSIC_PAGE_TYPE_ALBUM -> "https://music.youtube.com/browse/${browseEndpoint.browseId}"
        PageType.MUSIC_PAGE_TYPE_ARTIST -> "https://music.youtube.com/channel/${browseEndpoint.browseId}"
    }

    watchEndpoint != null -> "https://music.youtube.com/watch?v${watchEndpoint.videoId}"
    else -> error("Unknown endpoint: $this")
}

class YoutubeSearchManager : SearchSourceManager {
    companion object {
        const val SEARCH_PREFIX = "ytsearch:"
        const val MUSIC_SEARCH_PREFIX = "ytmsearch:"
        val SEARCH_TYPES = setOf(SearchType.ALBUM, SearchType.ARTIST, SearchType.TRACK, SearchType.TEXT)
    }

    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    override fun getSourceName(): String = "youtube"

    override fun loadSearch(query: String, types: Set<SearchType>): SearchResult? {
        if (!query.startsWith(MUSIC_SEARCH_PREFIX)) return null

        val result = httpInterfaceManager.`interface`.use {
            it.requestMusicAutoComplete(query.removePrefix(MUSIC_SEARCH_PREFIX))
        }

        val items = result.contents.flatMap {
            it.searchSuggestionsSectionRenderer.contents.mapNotNull {
                if (it.searchSuggestionRenderer != null) {
                    SearchText(it.searchSuggestionRenderer.suggestion.joinRuns())
                } else if (it.musicResponsiveListItemRenderer != null) {
                    val item = it.musicResponsiveListItemRenderer
                    val thumbnail = item.thumbnail.musicThumbnailRenderer
                        .thumbnail.thumbnails.first().url
                    val url = item.navigationEndpoint.toUrl()
                    val artist = item.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.joinRuns() ?: "Unknown Author"
                    if (item.navigationEndpoint.watchEndpoint != null) {
                        SearchTrack(
                            item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns(),
                            artist,
                            -1,
                            item.navigationEndpoint.watchEndpoint.videoId,
                            false,
                            url,
                            thumbnail,
                            null
                        )
                    } else if (item.navigationEndpoint.browseEndpoint != null) {
                        val type =
                            item.navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType
                        val identifier = item.navigationEndpoint.browseEndpoint.browseId
                        val name = item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns()
                        when (type) {
                            PageType.MUSIC_PAGE_TYPE_ALBUM -> SearchAlbum(
                                identifier,
                                name,
                                artist,
                                url,
                                -1,
                                thumbnail,
                                null
                            )

                            PageType.MUSIC_PAGE_TYPE_ARTIST -> SearchArtist(
                                identifier, name, url, thumbnail
                            )
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
        val finalTypes = types.ifEmpty { SEARCH_TYPES }
        return SearchResult(
            items.filter<SearchAlbum>(SearchType.ALBUM in finalTypes),
            items.filter<SearchArtist>(SearchType.ARTIST in finalTypes),
            emptyList(),
            items.filter<SearchTrack>(SearchType.TRACK in finalTypes),
            items.filter<SearchText>(SearchType.TEXT in finalTypes),
        )
    }

    override fun shutdown() = httpInterfaceManager.close()
}

private inline fun <reified T : Any> List<Any>.filter(enabled: Boolean) =
    if (enabled) filterIsInstance<T>() else emptyList()
