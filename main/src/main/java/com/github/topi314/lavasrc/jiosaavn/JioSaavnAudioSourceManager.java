package com.github.topi314.lavasrc.jiosaavn;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.proxy.ProxyConfig;
import com.github.topi314.lavasrc.proxy.ProxyManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JioSaavnAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable, AudioSearchManager {
	public static final Pattern URL_PATTERN = Pattern.compile(
		"https://www\\.jiosaavn\\.com/(?<type>album|featured|song|s/playlist|artist)/[^/]+/(?<id>[A-Za-z0-9_,\\-]+)"
	);
	private final String httpProxy;
	public static final String SEARCH_PREFIX = "jssearch:";
	public static final String RECOMMENDATIONS_PREFIX = "jsrec:";
	public static final String PREVIEW_PREFIX = "jsprev:";
	public static final long PREVIEW_LENGTH = 30000L;
	public static final String MEDIA_API_BASE = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0%3F_marker%3D0&_format=json&pids=";
	public static final String SEARCH_API_BASE = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&includeMetaTags=1&q=";
	public static final String METADATA_API_BASE = "https://www.jiosaavn.com/api.php?__call=webapi.get&api_version=4&_format=json&_marker=0&ctx=web6dot0&token=%s&type=%s";
	public static final String RECOS_STATION_API_BASE = "https://www.jiosaavn.com/api.php?__call=webradio.createEntityStation&api_version=4&_format=json&_marker=0&ctx=android&entity_id=%s&entity_type=queue";
	public static final String RECOS_API_BASE = "https://www.jiosaavn.com/api.php?__call=webradio.getSong&api_version=4&_format=json&_marker=0&ctx=android&stationid=%s&k=20";
	public static final String ARTIST_RECOS_API_BASE = "https://www.jiosaavn.com/api.php?__call=search.artistOtherTopSongs&api_version=4&_format=json&_marker=0&ctx=wap6dot0&artist_ids=%s&song_id=%s&language=unknown";

	private static final Logger log = LoggerFactory.getLogger(JioSaavnAudioSourceManager.class);
	private final HttpInterfaceManager httpInterfaceManager;
	private final ProxyManager proxyManager;
	private final JioSaavnDecryptionConfig decryptionConfig;


	public JioSaavnAudioSourceManager(ProxyConfig[] proxyConfigs, boolean useLocalNetwork, JioSaavnDecryptionConfig decryptionConfig) {
		this(null, new ProxyManager(proxyConfigs, useLocalNetwork), decryptionConfig);
	}

	public JioSaavnAudioSourceManager(JioSaavnDecryptionConfig decryptionConfig) {
		this(null, null, decryptionConfig);
	}

	public JioSaavnAudioSourceManager(@Nullable String httpProxy, JioSaavnDecryptionConfig decryptionConfig) {
		this(httpProxy, null, decryptionConfig);
	}

	public JioSaavnAudioSourceManager(ProxyManager proxyManager, JioSaavnDecryptionConfig decryptionConfig) {
		this(null, proxyManager, decryptionConfig);
	}

	public JioSaavnAudioSourceManager(@Nullable String httpProxy, ProxyManager proxyManager, JioSaavnDecryptionConfig decryptionConfig) {
		this.httpProxy = httpProxy;
		this.proxyManager = proxyManager;
		this.httpInterfaceManager = this.proxyManager != null ? this.proxyManager.getNextHttpInterfaceManager() : HttpClientTools.createCookielessThreadLocalManager();
		this.decryptionConfig = decryptionConfig;
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "jiosaavn";
	}

	private String buildSearchUrl(String query) {
		String searchQuery = SEARCH_API_BASE + URLEncoder.encode(query, StandardCharsets.UTF_8);
		if (httpProxy != null) {
			return httpProxy.replace("%LINK%", searchQuery);
		}
		return searchQuery;
	}

	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		ExtendedAudioSourceManager.ExtendedAudioTrackInfo extendedAudioTrackInfo = super.decodeTrack(input);
		return new JioSaavnAudioTrack(
			trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
			extendedAudioTrackInfo.isPreview,
			this
		);
	}

	@Nullable
	@Override
	public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		return null;
	}

	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		String identifier = reference.identifier;
		boolean isPreview = reference.identifier.startsWith(PREVIEW_PREFIX);
		return this.loadItem(isPreview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier, isPreview);
	}

	public AudioItem loadItem(String identifier, boolean preview) {
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return getSearch(identifier.substring(SEARCH_PREFIX.length()), preview);
			}

			if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()));
			}

			Matcher matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}

			String type = matcher.group("type");
			String id = matcher.group("id");

			switch (type) {
				case "s/playlist":
					String playlistId = identifier.substring(identifier.lastIndexOf("/") + 1);
					return getPlaylist(playlistId, preview);
				case "album":
					return getAlbum(id, preview);
				case "song":
					return getTrack(id, preview);
				case "featured":
					return getPlaylist(id, preview);
				case "artist":
					return getArtist(id, preview);
				default:
					return null;
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to load audio item: " + e.getMessage(), e);
		}
	}

	public JsonBrowser getJson(String uri) throws IOException {
		HttpGet request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		return LavaSrcTools.fetchResponseAsJson(this.getHttpInterface(this.httpProxy != null), request);
	}

	private List<AudioTrack> localParseTracks(JsonBrowser json, boolean preview, boolean metadataType) {
		ArrayList<AudioTrack> tracks = new ArrayList<>();

		for (JsonBrowser track : json.values()) {
			tracks.add(this.parseTrack(track, preview, metadataType));
		}

		return tracks;
	}

	private String cleanString(String text) {
		return text != null ? text.replace("&quot;", "").replace("&amp;", "") : null;
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview, boolean metadataType) {
		String identifier = json.get("id").text();
		String title = this.cleanString(metadataType ? json.get("title").text() : json.get("song").text());

		String artistArtworkUrl = null;
		String artistUrl = null;
		String author;

		if (metadataType) {
			JsonBrowser moreInfoJson = json.get("more_info");
			JsonBrowser artistMapJson = moreInfoJson.get("artistMap");

			List<JsonBrowser> primaryArtists = artistMapJson.get("primary_artists").values();
			if (primaryArtists.isEmpty()) primaryArtists = artistMapJson.get("artists").values();
			if (!primaryArtists.isEmpty()) {
				author = primaryArtists.stream()
					.map(artist -> artist.get("name").text())
					.filter(name -> name != null && !name.isEmpty())
					.collect(Collectors.joining(", "));

				artistUrl = primaryArtists.get(0).get("perma_url").text();
				artistArtworkUrl = primaryArtists.get(0).get("image").text();
			} else {
				author = this.cleanString(json.get("more_info").get("music").text());
			}

		} else {
			author = this.cleanString(json.get("primary_artists").text());
			if (author.isEmpty()) {
				author = this.cleanString(json.get("singers").text());
			}
		}

		String artworkUrl = json.get("image").isNull() ? null : json.get("image").text().replace("150x150", "500x500");
		long length = preview
			? PREVIEW_LENGTH
			: (metadataType ? json.get("more_info").get("duration").asLong(PREVIEW_LENGTH)
			: json.get("duration").asLong(PREVIEW_LENGTH)) * 1000L;

		String uri = json.get("perma_url").text();

		String albumName;
		String albumUrl;
		String previewUrl;

		if (!metadataType) {
			albumName = !json.get("album").isNull() ? this.cleanString(json.get("album").text()) : null;
			albumUrl = !json.get("album_url").isNull() ? json.get("album_url").text() : null;
			previewUrl = !json.get("media_preview_url").isNull() ? json.get("media_preview_url").text() : json.get("vlink").text();
		} else {
			JsonBrowser moreInfoJson = json.get("more_info");
			albumName = !moreInfoJson.get("album").isNull() ? this.cleanString(moreInfoJson.get("album").text()) : null;
			albumUrl = !moreInfoJson.get("album_url").isNull() ? moreInfoJson.get("album_url").text() : null;
			previewUrl = !moreInfoJson.get("vlink").isNull() ? moreInfoJson.get("vlink").text() : json.get("vlink").text();
		}

		if (artistArtworkUrl != null) {
			artistArtworkUrl = artistArtworkUrl.replace("150x150", "500x500");
		}

		AudioTrackInfo info = new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, null);
		return new JioSaavnAudioTrack(info, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, preview, this);
	}


	private AudioItem getRecommendations(String id) throws IOException {
		String encodedId = URLEncoder.encode(String.format("[\"%s\"]", id), StandardCharsets.UTF_8);
		JsonBrowser json = this.getJson(String.format(RECOS_STATION_API_BASE, encodedId));

		if (json != null && !json.get("stationid").isNull() && !json.get("stationid").text().isEmpty()) {
			String stationId = json.get("stationid").text();
			json = this.getJson(String.format(RECOS_API_BASE, URLEncoder.encode(stationId, StandardCharsets.UTF_8)));

			if (json != null && !json.values().isEmpty() && json.get("error").isNull()) {
				List<AudioTrack> stationTracks = json.values()
					.stream()
					.filter(JsonBrowser::isMap)
					.map(value -> value.get("song"))
					.map(value -> this.parseTrack(value, false, true))
					.collect(Collectors.toList());

				if (!stationTracks.isEmpty()) {
					return new JioSaavnAudioPlaylist(
						"JioSaavn Recommendations", stationTracks, ExtendedAudioPlaylist.Type.RECOMMENDATIONS, id, null, "Saavn Editor", stationTracks.size()
					);
				}
			}
		}

		JsonBrowser metadata = this.getJson(MEDIA_API_BASE + URLEncoder.encode(id, StandardCharsets.UTF_8));
		if (metadata == null || metadata.get(id).isNull()) {
			log.error("Failed to get metadata for id: " + id);
			return AudioReference.NO_TRACK;
		}

		String artistIdsJoined = metadata.get(id).get("primary_artists_id").text();
		json = this.getJson(String.format(ARTIST_RECOS_API_BASE,
			URLEncoder.encode(artistIdsJoined, StandardCharsets.UTF_8),
			URLEncoder.encode(id, StandardCharsets.UTF_8)));

		if (json != null && !json.values().isEmpty()) {
			List<AudioTrack> artistTracks = this.localParseTracks(json, false, true);
			if (!artistTracks.isEmpty()) {
				return new JioSaavnAudioPlaylist(
					"JioSaavn Recommendations", artistTracks, ExtendedAudioPlaylist.Type.RECOMMENDATIONS, id, null, "Saavn Editor", artistTracks.size()
				);
			}
		}

		return AudioReference.NO_TRACK;
	}

	private AudioItem getSearch(String query, boolean preview) throws IOException {
		log.debug("Searching text based query on JioSaavn : " + query);

		JsonBrowser json = this.getJson(this.buildSearchUrl(query));
		if (json == null || json.get("results").values().isEmpty()) {
			log.debug("Failed to get search results for query: " + query);
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("JioSaavn Search: " + query,
			this.localParseTracks(json.get("results"), preview, false), null, true);
	}


	private AudioItem getAlbum(String id, boolean preview) throws IOException {
		JsonBrowser json = this.getJson(String.format(METADATA_API_BASE, URLEncoder.encode(id, StandardCharsets.UTF_8), "album"));

		if (json == null || json.get("list").values().isEmpty()) {
			log.debug("Failed to get album for id: %s", id);
			return AudioReference.NO_TRACK;
		}

		String title = this.cleanString(json.get("title").text());
		String artworkUrl = json.get("image").text().replace("150x150", "500x500");
		String uri = json.get("perma_url").text();
		List<AudioTrack> tracks = this.localParseTracks(json.get("list"), preview, true);
		int trackCount = tracks.size();
		String author = tracks.get(0).getInfo().author;

		return new JioSaavnAudioPlaylist(title, tracks, ExtendedAudioPlaylist.Type.ALBUM, uri, artworkUrl, author, trackCount);
	}

	private AudioItem getTrack(String id, boolean preview) throws IOException {
		JsonBrowser json = this.getJson(String.format(METADATA_API_BASE, URLEncoder.encode(id, StandardCharsets.UTF_8), "song"));

		if (json == null || json.get("songs").values().isEmpty()) {
			log.debug("Failed to get track for id: %s", id);
			return AudioReference.NO_TRACK;
		}

		JsonBrowser track = json.get("songs").values().get(0);
		return this.parseTrack(track, preview, true);
	}

	private AudioItem getPlaylist(String id, boolean preview) throws IOException {

		JsonBrowser json = this.getJson(String.format(METADATA_API_BASE, URLEncoder.encode(id, StandardCharsets.UTF_8), "playlist") + "&n=10000");

		if (json == null || json.get("list").values().isEmpty()) {
			log.debug("Failed to get playlist for id: %s", id);
			return AudioReference.NO_TRACK;
		}

		String artworkUrl = null;
		if (!json.get("image").isNull()) {
			artworkUrl = json.get("image").text().replace("150x150", "500x500");
		}

		String author;
		if (!json.get("more_info").get("firstname").isNull() && !json.get("more_info").get("firstname").text().isEmpty()) {
			author = json.get("more_info").get("firstname").text();
		} else {
			author = json.get("more_info").get("username").text();
		}

		String title = this.cleanString(json.get("title").text());
		List<AudioTrack> tracks = this.localParseTracks(json.get("list"), preview, true);
		String url = json.get("perma_url").text();
		int trackCount = tracks.size();
		return new JioSaavnAudioPlaylist(title, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, url, artworkUrl, author, trackCount);

	}

	private AudioItem getArtist(String id, boolean preview) throws IOException {
		JsonBrowser json = this.getJson(String.format(METADATA_API_BASE, URLEncoder.encode(id, StandardCharsets.UTF_8), "artist"));

		if (json == null || json.get("topSongs").values().isEmpty()) {
			log.debug("Failed to get artist for id: %s", id);
			return AudioReference.NO_TRACK;
		}

		List<AudioTrack> tracks = this.localParseTracks(json.get("topSongs"), preview, true);
		int trackCount = tracks.size();
		String artworkUrl = null;
		if (!json.get("image").isNull()) {
			artworkUrl = json.get("image").text().replace("150x150", "500x500");
		}

		String uri = json.get("urls").get("overview").text();
		String author = this.cleanString(json.get("name").text());
		return new JioSaavnAudioPlaylist(author + "'s Top Tracks", tracks, ExtendedAudioPlaylist.Type.ARTIST, uri, artworkUrl, author, trackCount);
	}

	@Override
	public void shutdown() {
		try {
			if (proxyManager != null) httpInterfaceManager.close();
			if (this.httpInterfaceManager != null) httpInterfaceManager.close();
		} catch (IOException var2) {
			log.error("Failed to close HTTP interface manager", var2);
		}
	}

	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		if (proxyManager != null) httpInterfaceManager.configureRequests(configurator);
		if (this.httpInterfaceManager != null) httpInterfaceManager.configureRequests(configurator);
	}

	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		if (proxyManager != null) httpInterfaceManager.configureBuilder(configurator);
		if (this.httpInterfaceManager != null) httpInterfaceManager.configureBuilder(configurator);
	}

	public HttpInterface getHttpInterface(boolean useLocalNetwork) {
		if (this.proxyManager == null) {
			return this.httpInterfaceManager.getInterface();
		}
		return useLocalNetwork ? this.proxyManager.getLocalManager().getInterface() : this.proxyManager.getInterface();
	}

	public JioSaavnDecryptionConfig getDecryptionConfig() {
		return this.decryptionConfig;
	}
}
