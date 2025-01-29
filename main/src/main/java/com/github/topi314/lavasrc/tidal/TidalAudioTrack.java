package com.github.topi314.lavasrc.tidal;

import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

public class TidalAudioTrack extends MirroringAudioTrack {

	public TidalAudioTrack(
		AudioTrackInfo trackInfo,
		TidalSourceManager sourceManager
	) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public TidalAudioTrack(
		AudioTrackInfo trackInfo,
		String albumName,
		String albumUrl,
		String artistUrl,
		String artistArtworkUrl,
		String previewUrl,
		boolean isPreview,
		TidalSourceManager sourceManager
	) {
		super(
			trackInfo,
			albumName,
			albumUrl,
			artistUrl,
			artistArtworkUrl,
			previewUrl,
			isPreview,
			sourceManager
		);
	}

	@Override
	protected InternalAudioTrack createAudioTrack(
		AudioTrackInfo trackInfo,
		SeekableInputStream stream
	) {
		return new Mp3AudioTrack(trackInfo, stream);
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new TidalAudioTrack(
			this.trackInfo,
			(TidalSourceManager) this.sourceManager
		);
	}
}