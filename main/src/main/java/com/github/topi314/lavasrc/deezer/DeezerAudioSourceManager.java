package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeezerAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable, AudioSearchManager, AudioLyricsManager {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?deezer\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>track|album|playlist|artist)/(?<identifier>[0-9]+)");
	public static final String SEARCH_PREFIX = "dzsearch:";
	public static final String ISRC_PREFIX = "dzisrc:";
	public static final String PREVIEW_PREFIX = "dzprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final String SHARE_URL = "https://deezer.page.link/";
	public static final String PUBLIC_API_BASE = "https://api.deezer.com/2.0";
	public static final String PRIVATE_API_BASE = "https://www.deezer.com/ajax/gw-light.php";
	public static final String MEDIA_BASE = "https://media.deezer.com/v1";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);
	private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

	private final String masterDecryptionKey;
	private String arl;
	private DeezerAudioTrack.TrackFormat[] formats;
	private final HttpInterfaceManager httpInterfaceManager;
	private Tokens tokens;

	public DeezerAudioSourceManager(String masterDecryptionKey) {
		this(masterDecryptionKey, null);
	}

	public DeezerAudioSourceManager(String masterDecryptionKey, @Nullable String arl) {
		this(masterDecryptionKey, arl, null);
	}

	public DeezerAudioSourceManager(String masterDecryptionKey, @Nullable String arl, @Nullable DeezerAudioTrack.TrackFormat[] formats) {
		if (masterDecryptionKey == null || masterDecryptionKey.isEmpty()) {
			throw new IllegalArgumentException("Deezer master key must be set");
		}

		this.masterDecryptionKey = masterDecryptionKey;
		this.arl = arl != null && arl.isEmpty() ? null : arl;
		this.formats = formats != null && formats.length > 0 ? formats : DeezerAudioTrack.TrackFormat.DEFAULT_FORMATS;
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	public void setFormats(DeezerAudioTrack.TrackFormat[] formats) {
		if (formats.length == 0) {
			throw new IllegalArgumentException("Deezer track formats must not be empty");
		}
		this.formats = formats;
	}

	public void setArl(String arl) {
		this.arl = arl;
	}

	static void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
		if (json == null) {
			throw new IllegalStateException(message + "No response");
		}
		var errors = json.get("data").index(0).get("errors").values();
		if (!errors.isEmpty()) {
			var errorsStr = errors.stream().map(error -> error.get("code").text() + ": " + error.get("message").text()).collect(Collectors.joining(", "));
			throw new IllegalStateException(message + errorsStr);
		}
	}

	private void refreshSession() throws IOException {
		var getSessionID = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.ping&input=3&api_version=1.0&api_token=");
		var json = LavaSrcTools.fetchResponseAsJson(this.getHttpInterface(), getSessionID);

		checkResponse(json, "Failed to get session ID: ");
		var sessionID = json.get("results").get("SESSION").text();

		var getUserToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");
		getUserToken.setHeader("Cookie", "sid=" + sessionID);
		json = LavaSrcTools.fetchResponseAsJson(this.getHttpInterface(), getUserToken);

		checkResponse(json, "Failed to get user token: ");
		this.tokens = new Tokens(
			json.get("results").get("checkForm").text(),
			json.get("results").get("USER").get("OPTIONS").get("license_token").text(),
			Instant.now().plus(3600, ChronoUnit.SECONDS)
		);
	}

	public Tokens getTokens() throws IOException {
		if (this.tokens == null || Instant.now().isAfter(this.tokens.expireAt)) {
			this.refreshSession();
		}
		return this.tokens;
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "deezer";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new DeezerAudioTrack(trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
			extendedAudioTrackInfo.isPreview,
			this
		);
	}

	@Override
	@Nullable
	public AudioLyrics loadLyrics(@NotNull AudioTrack audioTrack) {
		var deezerTackId = "";
		if (audioTrack instanceof DeezerAudioTrack) {
			deezerTackId = audioTrack.getIdentifier();
		}

		if (deezerTackId.isEmpty()) {
			AudioItem item = AudioReference.NO_TRACK;
			try {
				if (audioTrack.getInfo().isrc != null && !audioTrack.getInfo().isrc.isEmpty()) {
					item = this.getTrackByISRC(audioTrack.getInfo().isrc, false);
				}
				if (item == AudioReference.NO_TRACK) {
					item = this.getSearch(String.format("%s %s", audioTrack.getInfo().title, audioTrack.getInfo().author), false);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (item == AudioReference.NO_TRACK) {
				return null;
			}
			if (item instanceof AudioTrack) {
				deezerTackId = ((AudioTrack) item).getIdentifier();
			} else if (item instanceof AudioPlaylist) {
				var playlist = (AudioPlaylist) item;
				if (!playlist.getTracks().isEmpty()) {
					deezerTackId = playlist.getTracks().get(0).getIdentifier();
				}
			}
		}

		try {
			return this.getLyrics(deezerTackId);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public AudioLyrics getLyrics(String id) throws IOException {
		var json = this.getJson(PRIVATE_API_BASE + "?method=song.getLyrics&api_version=1.0&api_token=" + this.getTokens().api + "&sng_id=" + id);
		if (json == null || json.get("results").values().isEmpty()) {
			return null;
		}

		var results = json.get("results");
		var lyricsText = results.get("LYRICS_TEXT").text();
		var lyrics = new ArrayList<AudioLyrics.Line>();
		for (var line : results.get("LYRICS_SYNC_JSON").values()) {
			lyrics.add(new BasicAudioLyrics.BasicLine(
				Duration.ofMillis(line.get("milliseconds").asLong(0)),
				Duration.ofMillis(line.get("duration").asLong(0)),
				line.get("line").text()
			));
		}

		return new BasicAudioLyrics("deezer", "LyricFind", lyricsText, lyrics);
	}

	@Override
	@Nullable
	public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return this.getAutocomplete(query.substring(SEARCH_PREFIX.length()), types);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		var identifier = reference.identifier;
		var preview = reference.identifier.startsWith(PREVIEW_PREFIX);
		return this.loadItem(preview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier, preview);
	}

	public AudioItem loadItem(String identifier, boolean preview) {
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()), preview);
			}

			if (identifier.startsWith(ISRC_PREFIX)) {
				return this.getTrackByISRC(identifier.substring(ISRC_PREFIX.length()), preview);
			}

			// If the identifier is a share URL, we need to follow the redirect to find out the real url behind it
			if (identifier.startsWith(SHARE_URL)) {
				var request = new HttpGet(identifier);
				request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
				try (var response = this.httpInterfaceManager.getInterface().execute(request)) {
					if (response.getStatusLine().getStatusCode() == 302) {
						var location = response.getFirstHeader("Location").getValue();
						if (location.startsWith("https://www.deezer.com/")) {
							return this.loadItem(location, preview);
						}
					}
					return null;
				}
			}

			var matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}

			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "album":
					return this.getAlbum(id, preview);

				case "track":
					return this.getTrack(id, preview);

				case "playlist":
					return this.getPlaylist(id, preview);

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
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("data").values()) {
			if (!track.get("type").text().equals("track")) {
				continue;
			}
			if (!track.get("readable").asBoolean(false)) {
				log.warn("Skipping track {} by {} because it is not readable. Available countries: {}", track.get("title").text(), track.get("artist").get("name").text(), track.get("available_countries").text());
				continue;
			}
			tracks.add(this.parseTrack(track, preview));
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview) {
		boolean readable = json.get("readable").asBoolean(false);
		if (!readable) {
			throw new FriendlyException("This track is not readable. Available countries: " + json.get("available_countries").text(), FriendlyException.Severity.COMMON, null);
		}
		var id = json.get("id").text();
		return new DeezerAudioTrack(
			new AudioTrackInfo(
				json.get("title").text(),
				json.get("artist").get("name").text(),
				preview ? PREVIEW_LENGTH : json.get("duration").asLong(0) * 1000,
				id,
				false,
				"https://deezer.com/track/" + id,
				json.get("album").get("cover_xl").text(),
				json.get("isrc").text()
			),
			json.get("album").get("title").text(),
			"https://www.deezer.com/album/" + json.get("album").get("id").text(),
			"https://www.deezer.com/artist/" + json.get("artist").get("id").text(),
			json.get("artist").get("picture_xl").text(),
			json.get("preview").text(),
			preview,
			this
		);
	}

	private AudioSearchResult getAutocomplete(String query, Set<AudioSearchResult.Type> types) throws IOException {
		if (types.isEmpty()) {
			types = SEARCH_TYPES;
		}
		var json = this.getJson(PUBLIC_API_BASE + "/search/autocomplete?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
		if (json == null) {
			return AudioSearchResult.EMPTY;
		}

		var albums = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.ALBUM)) {
			for (var album : json.get("albums").get("data").values()) {
				albums.add(new DeezerAudioPlaylist(
					album.get("title").text(),
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.ALBUM,
					album.get("link").text(),
					album.get("cover_xl").text(),
					album.get("artist").get("name").text(),
					(int) album.get("nb_tracks").asLong(0)
				));
			}
		}

		var artists = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.ARTIST)) {
			for (var artist : json.get("artists").get("data").values()) {
				artists.add(new DeezerAudioPlaylist(
					artist.get("name").text() + "'s Top Tracks",
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.ARTIST,
					artist.get("link").text(),
					artist.get("picture_xl").text(),
					artist.get("name").text(),
					null
				));
			}
		}

		var playlists = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
			for (var playlist : json.get("playlists").get("data").values()) {
				playlists.add(new DeezerAudioPlaylist(
					playlist.get("title").text(),
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.PLAYLIST,
					playlist.get("link").text(),
					playlist.get("picture_xl").text(),
					playlist.get("creator").get("name").text(),
					(int) playlist.get("nb_tracks").asLong(0)
				));
			}
		}

		var tracks = new ArrayList<AudioTrack>();
		if (types.contains(AudioSearchResult.Type.TRACK)) {
			tracks.addAll(this.parseTracks(json.get("tracks"), false));
		}

		return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
	}

	private AudioItem getTrackByISRC(String isrc, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/isrc:" + URLEncoder.encode(isrc, StandardCharsets.UTF_8));
		if (json == null || json.get("id").isNull()) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json, preview);
	}

	private AudioItem getSearch(String query, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Deezer Search: " + query, this.parseTracks(json, preview), null, true);
	}

	private AudioItem getAlbum(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/album/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("cover_xl").text();
		var author = json.get("contributors").values().get(0).get("name").text();

		var tracks = this.getJson(PUBLIC_API_BASE + "/album/" + id + "/tracks?limit=10000");

		for (var track : tracks.get("data").values()) {
			track.get("artist").put("picture_xl", json.get("artist").get("picture_xl"));
		}

		return new DeezerAudioPlaylist(json.get("title").text(),
			this.parseTracks(tracks, preview),
			DeezerAudioPlaylist.Type.ALBUM,
			json.get("link").text(),
			artworkUrl,
			author,
			(int) json.get("nb_tracks").asLong(0));
	}

	private AudioItem getTrack(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json, preview);
	}

	private AudioItem getPlaylist(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/playlist/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("picture_xl").text();
		var author = json.get("creator").get("name").text();

		// This endpoint returns tracks with ISRC, unlike the other REST call
		var tracks = this.getJson(PUBLIC_API_BASE + "/playlist/" + id + "/tracks?limit=10000");

		return new DeezerAudioPlaylist(json.get("title").text(),
			this.parseTracks(tracks, preview),
			DeezerAudioPlaylist.Type.PLAYLIST,
			json.get("link").text(),
			artworkUrl,
			author,
			(int) json.get("nb_tracks").asLong(0));
	}

	private AudioItem getArtist(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/artist/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksJson = this.getJson(PUBLIC_API_BASE + "/artist/" + id + "/top?limit=50");
		if (tracksJson == null || tracksJson.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		for (var track : tracksJson.get("data").values()) {
			track.get("artist").put("picture_xl", json.get("picture_xl"));
		}

		var artworkUrl = json.get("picture_xl").text();
		var author = json.get("name").text();
		var deezerTracks = this.parseTracks(tracksJson, preview);
		return new DeezerAudioPlaylist(author + "'s Top Tracks", deezerTracks, DeezerAudioPlaylist.Type.ARTIST, json.get("link").text(), artworkUrl, author, deezerTracks.size());
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

	public String getMasterDecryptionKey() {
		return this.masterDecryptionKey;
	}

	@Nullable
	public String getArl() {
		return this.arl;
	}

	public DeezerAudioTrack.TrackFormat[] getFormats() {
		return this.formats;
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}

	public static class Tokens {
		public String api;
		public String license;
		public Instant expireAt;

		public Tokens(String api, String license, Instant expireAt) {
			this.api = api;
			this.license = license;
			this.expireAt = expireAt;
		}
	}

}
