package com.github.topi314.lavasrc.qobuz;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class QobuzAudioTrack extends ExtendedAudioTrack {

	private final QobuzAudioSourceManager sourceManager;

	public QobuzAudioTrack(AudioTrackInfo trackInfo, QobuzAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public QobuzAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
	                       String artistArtworkUrl, String previewUrl, boolean isPreview, QobuzAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	private URI getTrackMediaURI() throws Exception {
		long unixTs = System.currentTimeMillis() / 1000L;
		String rSig = String.format("trackgetFileUrlformat_id%dintentstream" +
			"track_id%d%d%s", 5, Integer.parseInt(this.getIdentifier()), unixTs, this.sourceManager.getAppSecret());

		String rSigHashed = getMd5Hash(rSig);
		Map<String, String> params = new HashMap<>();
		params.put("request_ts", String.valueOf(unixTs));
		params.put("request_sig", rSigHashed);
		params.put("track_id", String.valueOf(Integer.parseInt(this.getIdentifier())));
		params.put("format_id", String.valueOf(5));
		params.put("intent", "stream");

		String url = "https://www.qobuz.com/api.json/0.2/track/getFileUrl";
		String fullUrl = url + "?" + getQueryString(params);

		var json = this.sourceManager.getJson(fullUrl);
		if (json == null || json.get("url").isNull()) {
			throw new IllegalStateException("Failed to get track media URI");
		}
		if (!json.get("sample").isNull() && json.get("sample").asBoolean(true) == true) {
			throw new IllegalStateException("Premium account required to play the whole track");
		}
		return new URI(json.get("url").text());
	}

	private static String getMd5Hash(String input) throws Exception {
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] messageDigest = md.digest(input.getBytes());
		StringBuilder hexString = new StringBuilder();
		for (byte b : messageDigest) {
			String hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private static String getQueryString(Map<String, String> params) {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (result.length() > 0) {
				result.append("&");
			}
			result.append(entry.getKey());
			result.append("=");
			result.append(entry.getValue());
		}
		return result.toString();
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			if (this.isPreview) {
				if (this.previewUrl == null) {
					throw new FriendlyException("No preview url found", FriendlyException.Severity.COMMON,
						new IllegalArgumentException());
				}
				try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl),
					this.trackInfo.length)) {
					processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
				}
			} else {
				try (var stream = new PersistentHttpStream(httpInterface, this.getTrackMediaURI(),
					this.trackInfo.length)) {
					processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
				}
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new QobuzAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl,
			this.previewUrl, this.isPreview, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

}