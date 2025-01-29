package com.github.topi314.lavasrc.qobuz;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class QobuzAudioSourceManager extends ExtendedAudioSourceManager
	implements HttpConfigurable, AudioSearchManager {

	private static final String WEB_PLAYER_BASE_URL = "https://play.qobuz.com";
	public static final String API_URL = "https://www.qobuz.com/api.json/0.2/";
	public static final Pattern URL_PATTERN = Pattern.compile(
		"https?://(?:www\\.|play\\.|open\\.)?qobuz\\.com/(?:(?:[a-z]{2}-[a-z]{2}/)?(?<type>album|playlist|track|artist)/(?:.+?/)?(?<id>[a-zA-Z0-9]+)|(?<type2>playlist)/(?<id2>\\d+))");
	private String appId;
	private String appSecret;
	private final String userOauthToken;

	public static final String SEARCH_PREFIX = "qbsearch:";
	public static final long PREVIEW_LENGTH = 30000;
	private static final int ALBUM_LOAD_LIMIT = 500;
	private static final int PLAYLIST_LOAD_LIMIT = 1000;
	private static final int ARTIST_LOAD_LIMIT = 500;

	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK,
		AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);
	private static final Logger log = LoggerFactory.getLogger(QobuzAudioSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;


	public QobuzAudioSourceManager(String userOauthToken, String appId, String appSecret) {
		if (userOauthToken == null || userOauthToken.isEmpty()) {
			throw new IllegalArgumentException("User Oauth token cannot be null or empty.");
		}
		this.userOauthToken = userOauthToken;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
		if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
			try {
				this.fetchAppInfo();
			} catch (IOException e) {
				log.error("Failed to fetch app info.", e);
			}

		} else {
			this.appId = appId;
			this.appSecret = appSecret;
		}


	}

	@NotNull
	@Override
	public String getSourceName() {
		return "qobuz";
	}

	public String getAppSecret() {
		return this.appSecret;
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new QobuzAudioTrack(trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
			extendedAudioTrackInfo.isPreview,
			this);
	}

	@Override
	@Nullable
	public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		// TODO
		return null;
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		var identifier = reference.identifier;
		return this.loadItem(identifier, false);
	}

	public AudioItem loadItem(String identifier, boolean preview) {

		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()), preview);
			}

			Matcher matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}

			String type = matcher.group("type");
			String id = matcher.group("id");

			if (type == null) {
				type = matcher.group("type2");
				id = matcher.group("id2");
			}

			switch (type) {

				case "playlist":
					return this.getPlaylist(id, preview);

				case "album":
					return this.getAlbum(id, preview);

				case "track":
					return this.getTrack(id, preview);

				case "artist":
					return this.getArtist(id, preview);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("x-app-id", this.appId);
		request.setHeader("x-user-auth-token", this.userOauthToken);
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) {
		var tracks = new ArrayList<AudioTrack>();

		for (var track : json.values()) {

			tracks.add(this.parseTrack(track, preview));

		}
		return tracks;
	}

	private void fetchAppInfo() throws IOException {
		try {
			if (this.appId == null || this.appSecret == null) {
				String bundleJsContent = fetchBundleString();
				this.appId = getWebPlayerAppId(bundleJsContent);
				this.appSecret = getWebPlayerAppSecret(bundleJsContent);
			}
		} catch (Exception e) {
			log.error("Failed to fetch app info falling back to hardcoded values.", e);
			this.appId = "950096963";
			this.appSecret = "979549437fcc4a3faad4867b5cd25dcb";
		}

	}

	public static String fetchBundleString() throws IOException {
		String loginPageUrl = WEB_PLAYER_BASE_URL + "/login";

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(loginPageUrl);
		HttpResponse response = httpClient.execute(httpGet);
		HttpEntity entity = response.getEntity();

		String loginPageHtml = EntityUtils.toString(entity);

		Pattern bundlePattern = Pattern
			.compile("<script src=\"(?<bundleJS>/resources/\\d+\\.\\d+\\.\\d+-[a-z]\\d{3}/bundle\\.js)\"");
		Matcher bundleMatcher = bundlePattern.matcher(loginPageHtml);

		if (!bundleMatcher.find()) {
			throw new IllegalStateException("Failed to extract bundle.js URL");
		}
		String bundleUrl = WEB_PLAYER_BASE_URL + bundleMatcher.group("bundleJS");

		HttpGet bundleRequest = new HttpGet(bundleUrl);
		response = httpClient.execute(bundleRequest);
		entity = response.getEntity();
		return EntityUtils.toString(entity);
	}

	public static String getWebPlayerAppId(String bundleJsContent) {

		Pattern appIdPattern = Pattern.compile("production:\\{api:\\{appId:\"(?<appID>.*?)\",appSecret:");
		Matcher appIdMatcher = appIdPattern.matcher(bundleJsContent);

		if (!appIdMatcher.find()) {
			throw new IllegalStateException("Failed to extract app_id from bundle.js");
		}
		return appIdMatcher.group("appID");
	}

	public static String getWebPlayerAppSecret(String bundleJsContent) {
		Pattern seedPattern = Pattern
			.compile("\\):[a-z]\\.initialSeed\\(\"(?<seed>.*?)\",window\\.utimezone\\.(?<timezone>[a-z]+)\\)");
		Matcher seedMatcher = seedPattern.matcher(bundleJsContent);

		if (!seedMatcher.find()) {
			throw new IllegalStateException("Failed to extract seed and timezone from bundle.js");
		}

		String seed = seedMatcher.group("seed");
		String productionTimezone = capitalize(seedMatcher.group("timezone"));

		String infoExtrasPattern = "timezones:\\[.*?name:.*?/" + productionTimezone
			+ "\",info:\"(?<info>.*?)\",extras:\"(?<extras>.*?)\"";
		Pattern infoExtrasRegex = Pattern.compile(infoExtrasPattern);
		Matcher infoExtrasMatcher = infoExtrasRegex.matcher(bundleJsContent);

		if (!infoExtrasMatcher.find()) {
			throw new IllegalStateException(
				"Failed to extract info and extras for timezone " + productionTimezone + " from bundle.js");
		}

		String info = infoExtrasMatcher.group("info");
		String extras = infoExtrasMatcher.group("extras");

		String base64EncodedAppSecret = seed + info + extras;
		base64EncodedAppSecret = base64EncodedAppSecret.substring(0, base64EncodedAppSecret.length() - 44);

		byte[] appSecretBytes = Base64.getDecoder().decode(base64EncodedAppSecret);

		return new String(appSecretBytes);
	}

	private static String capitalize(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview) {
		var identifier = json.get("id").text();
		var title = json.get("title").text();

		String author = null;
		String artistUrl = null;

		if (!json.get("artist").isNull() && json.get("artist").isMap()) {
			author = json.get("artist").get("name").get("display").text();
			artistUrl = "https://open.qobuz.com/artist/" + json.get("artist").get("id").text();
		} else {
			author = json.get("album").get("artist").get("name").text();
			artistUrl = "https://open.qobuz.com/artist/" + json.get("album").get("artist").get("id").text();
		}
		var artworkUrl = json.get("album").get("image").get("large").isNull() ? null
			: json.get("album").get("image").get("large").text();
		var length = preview ? PREVIEW_LENGTH : json.get("duration").asLong(0) * 1000;
		var uri = "https://open.qobuz.com/track/" + identifier;

		var albumName = json.get("album").get("title").isNull() ? null : json.get("album").get("title").text();
		var albumurl = json.get("album").get("id").isNull() ? null
			: "https://open.qobuz.com/album/" + json.get("album").get("id").text();

		String previewUrl = null;

		String artistArtworkUrl = null;
		if (!json.get("album").get("artist").isNull() && !json.get("album").get("artist").get("image").isNull()) {
			artistArtworkUrl = json.get("album").get("artist").get("image").text();
		}

		var isrc = json.get("isrc").isNull() ? null : json.get("isrc").text();

		var info = new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, isrc);
		return new QobuzAudioTrack(info, albumName, albumurl, artistUrl, artistArtworkUrl, previewUrl, preview, this);
	}

	private AudioItem getSearch(String query, boolean preview) throws IOException {
		System.out.println(this.appId + " " + this.appSecret + " " + this.userOauthToken);
		var json = this.getJson(
			API_URL + "catalog/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
				+ "&limit=15&type=tracks");

		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Qobuz Search: " + query,
			this.parseTracks(json.get("tracks").get("items"), preview), null, true);
	}

	private AudioItem getAlbum(String id, boolean preview) throws IOException {
		JsonBrowser json = this.getJson(API_URL + "album/get?album_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
			+ "&limit=" + ALBUM_LOAD_LIMIT + "&offset=0");

		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var title = json.get("title").text();
		var artworkUrl = json.get("image").get("large").text();
		var author = json.get("artist").get("name").text();

		var uri = "https://open.qobuz.com/album/" + id;

		var tracks = json.get("tracks").get("items");

		json.remove("tracks");

		var m = new ArrayList<AudioTrack>();
		for (var track : tracks.values()) {
			track.put("album", json);
			m.add(this.parseTrack(track, preview));
		}

		int trackCount = (tracks != null) ? tracks.values().size() : 0;

		return new ExtendedAudioPlaylist(json.get("title").text(),
			m,
			ExtendedAudioPlaylist.Type.ALBUM,
			uri,
			artworkUrl,
			author,
			trackCount);
	}

	private AudioItem getTrack(String id, boolean preview) throws IOException {

		JsonBrowser json = this
			.getJson(API_URL + "track/get?track_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));

		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		return this.parseTrack(json, preview);
	}

	private AudioItem getPlaylist(String id, boolean preview) throws IOException {

		JsonBrowser json = this
			.getJson(API_URL + "playlist/get?playlist_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
				+ "&limit=" + PLAYLIST_LOAD_LIMIT + "&offset=0" + "&extra=tracks");

		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		String artworkUrl = null;
		if (json.get("images300").values().size() > 0) {
			artworkUrl = json.get("images300").values().get(0).text();
		}
		var author = json.get("owner").get("name").text();
		var title = json.get("name").text();
		var tracks = json.get("tracks").get("items");
		var url = json.get("url").text();
		int trackCount = (tracks != null) ? tracks.values().size() : 0;

		return new ExtendedAudioPlaylist(title,
			this.parseTracks(tracks, preview),
			ExtendedAudioPlaylist.Type.PLAYLIST,
			url,
			artworkUrl,
			author,
			trackCount);
	}

	private AudioItem getArtist(String id, boolean preview) throws IOException {

		JsonBrowser json = this
			.getJson(API_URL + "artist/page?artist_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));

		if (json == null || json.get("top_tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracksJson = json.get("top_tracks");
		if (tracksJson == null || tracksJson.isNull()) {
			return AudioReference.NO_TRACK;
		}

		int trackCount = (tracksJson != null) ? tracksJson.values().size() : 0;

		String artworkUrl = null;
		if (!json.get("images").get("potrait").isNull()
			&& !json.get("images").get("potrait").get("hash").text().isEmpty()) {
			artworkUrl = "https://static.qobuz.com/images/artists/covers/large/"
				+ json.get("images").get("potrait").get("hash").text() + ".jpg";
		}

		var uri = "https://open.qobuz.com/artist/" + id;

		var author = json.get("name").get("display").text();

		var qobuzTracks = this.parseTracks(tracksJson, preview);
		return new ExtendedAudioPlaylist(author + "'s Top Tracks", qobuzTracks, ExtendedAudioPlaylist.Type.ARTIST,
			uri, artworkUrl, author, trackCount);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.httpInterfaceManager.configureBuilder(configurator);
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}

}