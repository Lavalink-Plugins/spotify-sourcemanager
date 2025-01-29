package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.tidal.TidalSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Pair;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringCompareMirroringAudioTrackResolver implements MirroringAudioTrackResolver {
	private static final Logger log = LoggerFactory.getLogger(StringCompareMirroringAudioTrackResolver.class);


	private final String[] sources;
	private String[] providers = {
		"ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
		"ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN
	};

	private final Float titleThreshold;
	private final Float authorThreshold;
	private final Float totalMatchThreshold;
	private final boolean skipSoundCloudGo;
	private final Float levelOnePenalty;
	private final Float levelTwoPenalty;
	private final Float levelThreePenalty;

	public StringCompareMirroringAudioTrackResolver(
		String[] sources,
		String[] providers,
		Float titleThreshold,
		Float authorThreshold,
		Float totalMatchThreshold,
		boolean skipSoundCloudGo,
		Float levelOnePenalty,
		Float levelTwoPenalty,
		Float levelThreePenalty
	) {
		this.sources = sources;
		this.titleThreshold = titleThreshold;
		this.authorThreshold = authorThreshold;
		this.totalMatchThreshold = totalMatchThreshold;
		this.skipSoundCloudGo = skipSoundCloudGo;
		this.levelOnePenalty = levelOnePenalty;
		this.levelTwoPenalty = levelTwoPenalty;
		this.levelThreePenalty = levelThreePenalty;

		if (providers != null && providers.length > 0) {
			this.providers = providers;
		}
	}

	@Override
	public AudioItem apply(MirroringAudioTrack mirroringAudioTrack) {
		for (String provider : providers) {
			if (!isValidSearchProvider(provider)) continue;

			String updatedProvider = prepareProvider(provider, mirroringAudioTrack);

			AudioItem item = loadAudioItem(updatedProvider, mirroringAudioTrack);

			if (isValidAudioItem(item)) {
				item = applyStringComparison(item, mirroringAudioTrack, updatedProvider);
				if (isValidAudioItem(item)) {
					return item;
				}
			}
		}
		return AudioReference.NO_TRACK;
	}

	private String prepareProvider(String provider, MirroringAudioTrack mirroringAudioTrack) {
		if (provider.contains(MirroringAudioSourceManager.ISRC_PATTERN)) {
			String isrc = mirroringAudioTrack.getInfo().isrc;
			if (isrc != null && !isrc.isEmpty()) {
				return provider.replace("%ISRC%", isrc);
			}
			log.debug("Skipping provider \"{}\" due to missing ISRC.", provider);
			return provider;
		}
		return provider.replace(MirroringAudioSourceManager.QUERY_PATTERN, getTrackTitle(mirroringAudioTrack));
	}

	private AudioItem loadAudioItem(String provider, MirroringAudioTrack mirroringAudioTrack) {
		try {
			return mirroringAudioTrack.loadItem(provider);
		} catch (Exception e) {
			log.error("Failed to load track from provider \"{}\"!", provider, e);
			return AudioReference.NO_TRACK;
		}
	}

	public int calculateMatchScore(String targetTitle, String targetAuthor, String candidateTitle, String candidateAuthor,
	                               long targetDuration, long candidateDuration) {

		targetTitle = normalizeString(targetTitle);
		targetAuthor = normalizeString(targetAuthor);
		candidateTitle = normalizeString(candidateTitle);
		candidateAuthor = normalizeString(candidateAuthor);

		int titleScore = FuzzySearch.tokenSortRatio(targetTitle, candidateTitle);
		int titleMatchRatio = FuzzySearch.partialRatio(targetTitle, candidateTitle);
		int authorScore = matchAuthors(targetAuthor, candidateAuthor);
		int durationScore = calculateDurationScore(targetDuration, candidateDuration);

		int totalScore = titleScore + authorScore + durationScore;

		if (titleScore < this.titleThreshold) {
			return 0;
		}

		float penalty = calculatePenalty(titleMatchRatio);
		totalScore = Math.max(0, (int) (totalScore - penalty));

		log.info("Track Scored: {} : {} with score : {} :: Title Score : {} :: Title Match Ratio : {} :: Author Score : {} :: Duration Score : {} :: Penalty : {}",
			candidateTitle, candidateAuthor, totalScore, titleScore, titleMatchRatio, authorScore, durationScore, penalty);

		return adjustFinalScore(totalScore, authorScore);
	}

	private String normalizeString(String input) {
		return input.toLowerCase().trim();
	}

	private int calculateDurationScore(long targetDuration, long candidateDuration) {
		return 100 - (int) Math.abs(targetDuration / 1000L - candidateDuration / 1000L);
	}

	private float calculatePenalty(int titleMatchRatio) {
		int penaltyThreshold = 100;
		if (titleMatchRatio >= 80) {
			return this.levelOnePenalty != null ? this.levelOnePenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		} else if (titleMatchRatio >= 65) {
			log.debug("Title Match Ratio is between 65 and 80, applying penalty");
			return this.levelTwoPenalty != null ? this.levelTwoPenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		} else {
			log.debug("Title Match Ratio is less than 65, applying penalty");
			return this.levelThreePenalty != null ? this.levelThreePenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		}
	}

	private int adjustFinalScore(int totalScore, int authorScore) {
		if (totalScore < this.totalMatchThreshold.intValue() - 1 || totalScore > this.totalMatchThreshold.intValue() + 40) {
			return totalScore;
		} else {
			return authorScore >= this.authorThreshold ? totalScore : this.totalMatchThreshold.intValue() - 2;
		}
	}

	public static int matchAuthors(String targetAuthor, String candidateAuthor) {
		String[] targetAuthors = targetAuthor.split(",\\s*");
		String[] candidateAuthors = candidateAuthor.split(",\\s*");
		int bestScore = 0;

		for (String tAuthor : targetAuthors) {
			for (String cAuthor : candidateAuthors) {
				int score = FuzzySearch.tokenSortRatio(tAuthor.trim(), cAuthor.trim());
				bestScore = Math.max(bestScore, score);
			}
		}

		return bestScore;
	}

	public AudioItem applyStringComparison(AudioItem item, MirroringAudioTrack track, String provider) {
		if (!(item instanceof AudioPlaylist)) {
			log.warn("Item is not an AudioPlaylist but a single track. Skipping advanced mirroring logic.");
			return item;
		}

		AudioPlaylist playlist = (AudioPlaylist) item;

		List<AudioTrack> tracks = new ArrayList<>(playlist.getTracks());
		int initialTrackCount = tracks.size();

		if (this.skipSoundCloudGo) {
			tracks = tracks.stream()
				.filter(t -> !isSoundCloudGoTrack(t))
				.collect(Collectors.toList());
			log.debug("Skipped {} SoundCloud Go tracks", initialTrackCount - tracks.size());
		}

		String[] sourcePrefix = provider.split(":");
		if (sourcePrefix.length > 0) {
			for (var source : sources) {
				if (sourcePrefix[0].equals(source)) {
					log.info("Applying Advanced Mirroring for {}", provider);
					List<Pair<AudioTrack, Integer>> scoredTracks = scoreTracks(tracks, track.getInfo());

					if (scoredTracks.isEmpty() || getMaxScore(scoredTracks) < this.totalMatchThreshold.intValue()) {
						log.debug("No match found for track: max score is below threshold.");
						return AudioReference.NO_TRACK;
					}

					List<AudioTrack> matchedTracks = scoredTracks.stream()
						.map(Pair::getFirst)
						.collect(Collectors.toList());

					return new BasicAudioPlaylist("Mirroring Search: " + provider, matchedTracks, null, true);
				}
			}
		}

		return item;
	}

	private boolean isSoundCloudGoTrack(AudioTrack track) {
		return track.getSourceManager() instanceof SoundCloudAudioSourceManager && track.getIdentifier().contains("/preview/");
	}

	private List<Pair<AudioTrack, Integer>> scoreTracks(List<AudioTrack> tracks, AudioTrackInfo audioTrackInfo) {
		List<Pair<AudioTrack, Integer>> scoredTracks = new ArrayList<>();
		for (AudioTrack track : tracks) {
			int score = this.calculateMatchScore(
				audioTrackInfo.title,
				audioTrackInfo.author,
				track.getInfo().title,
				track.getInfo().author,
				audioTrackInfo.length,
				track.getInfo().length
			);
			scoredTracks.add(new Pair<>(track, score));
		}
		return scoredTracks;
	}

	private int getMaxScore(List<Pair<AudioTrack, Integer>> scoredTracks) {
		return scoredTracks.stream()
			.map(Pair::getSecond)
			.max(Integer::compareTo)
			.orElse(0);
	}

	public String getTrackTitle(MirroringAudioTrack mirroringAudioTrack) {
		var query = mirroringAudioTrack.getInfo().title;
		if (!mirroringAudioTrack.getInfo().author.equals("unknown")) {
			query += " " + mirroringAudioTrack.getInfo().author;
		}
		return query;
	}

	private boolean isValidAudioItem(AudioItem item) {
		boolean isPlaylistWithTracks = (item instanceof AudioPlaylist) && !((AudioPlaylist) item).getTracks().isEmpty();
		return isPlaylistWithTracks || (item != AudioReference.NO_TRACK);
	}

	private boolean isValidSearchProvider(String provider) {
		if (provider.startsWith(SpotifySourceManager.SEARCH_PREFIX)) {
			log.warn("Can not use spotify search as search provider!");
			return false;
		}

		if (provider.startsWith(AppleMusicSourceManager.SEARCH_PREFIX)) {
			log.warn("Can not use apple music search as search provider!");
			return false;
		}

		if(provider.startsWith(TidalSourceManager.SEARCH_PREFIX)) {
			log.warn("Can not use tidal search as search provider!");
			return false;
		}

		return true;
	}

}
