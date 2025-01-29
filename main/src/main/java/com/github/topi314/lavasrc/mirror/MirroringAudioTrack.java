package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public abstract class MirroringAudioTrack extends ExtendedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

	protected final MirroringAudioSourceManager sourceManager;

	public MirroringAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	protected abstract InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream inputStream);

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		if (this.isPreview) {
			if (this.previewUrl == null) {
				throw new FriendlyException("No preview url found", FriendlyException.Severity.COMMON, new IllegalArgumentException());
			}
			try (var httpInterface = this.sourceManager.getHttpInterface()) {
				try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
					processDelegate(createAudioTrack(this.trackInfo, stream), executor);
				}
			}
			return;
		}
		var track = this.sourceManager.getResolver().apply(this);

		if (track instanceof AudioPlaylist) {
			var tracks = ((AudioPlaylist) track).getTracks();
			if (tracks.isEmpty()) {
				throw new TrackNotFoundException("No tracks found in playlist or search result for track");
			}
			track = tracks.get(0);
		}
		if (track instanceof InternalAudioTrack) {
			var internalTrack = (InternalAudioTrack) track;
			log.debug("Loaded track mirror from {} {}({}) ", internalTrack.getSourceManager().getSourceName(), internalTrack.getInfo().title, internalTrack.getInfo().uri);
			processDelegate(internalTrack, executor);
			return;
		}
		throw new TrackNotFoundException("No mirror found for track");
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

	public AudioItem loadItem(String query) {
		var cf = new CompletableFuture<AudioItem>();
		this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {
				log.debug("Track loaded: {}", track.getIdentifier());
				cf.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				log.debug("Playlist loaded: {}", playlist.getName());
				cf.complete(playlist);
			}

			@Override
			public void noMatches() {
				log.debug("No matches found for: {}", query);
				cf.complete(AudioReference.NO_TRACK);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				log.debug("Failed to load: {}", query);
				cf.completeExceptionally(exception);
			}
		});
		return cf.join();
	}

}
