package com.github.topi314.lavasrc.jiosaavn;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.client.methods.HttpGet;

public class JioSaavnAudioTrack extends ExtendedAudioTrack {
	private final JioSaavnAudioSourceManager sourceManager;

	public JioSaavnAudioTrack(AudioTrackInfo trackInfo, JioSaavnAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public JioSaavnAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, JioSaavnAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	public static String decryptUrl(String url, JioSaavnDecryptionConfig decryptionConfig) {
		try {
			byte[] encryptedBytes = Base64.getDecoder().decode(url);
			SecretKeySpec keySpec = new SecretKeySpec(decryptionConfig.getSecretKey().getBytes(), decryptionConfig.getAlgorithm());
			Cipher cipher = Cipher.getInstance(decryptionConfig.getTransformation());
			cipher.init(Cipher.DECRYPT_MODE, keySpec);
			byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
			return new String(decryptedBytes);
		} catch (Exception e) {
			throw new FriendlyException("Failed to decrypt URL", Severity.COMMON, e);
		}
	}

	private URI getTrackMediaURI() throws IOException, URISyntaxException {
		String identifier = this.getIdentifier();
		String requestUrl = String.format("https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0&_format=json&pids=%s", identifier);
		HttpGet dataRequest = new HttpGet(requestUrl);

		JsonBrowser jsonResponse = LavaSrcTools.fetchResponseAsJson(this.sourceManager.getHttpInterface(true), dataRequest);
		checkResponse(jsonResponse, "Failed to get track details: ");

		JsonBrowser trackData = jsonResponse.get(identifier);
		String encryptedMediaUrl = trackData.get("encrypted_media_url").text();
		String playbackUrl = decryptUrl(encryptedMediaUrl, sourceManager.getDecryptionConfig());

		if (trackData.get("320kbps").asBoolean(false)) {
			playbackUrl = playbackUrl.replace("_96.mp4", "_320.mp4");
		}

		return new URI(playbackUrl);
	}

	private void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
		if (json == null) {
			throw new IllegalStateException(message + " No response");
		} else if (json.get(this.getIdentifier()).isNull()) {
			throw new IllegalStateException(message + " No track found");
		}
	}

	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (HttpInterface httpInterface = this.sourceManager.getHttpInterface(true)) {
			if (this.isPreview) {
				if (this.previewUrl == null) {
					throw new FriendlyException("No preview url found", Severity.COMMON, new IllegalArgumentException());
				}

				try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
					this.processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
				}
				return;
			}

			try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, this.getTrackMediaURI(), this.trackInfo.length)) {
				this.processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new JioSaavnAudioTrack(
			this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager
		);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}
}
