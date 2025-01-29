package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavalyrics.LyricsManager;
import com.github.topi314.lavalyrics.api.LyricsManagerConfiguration;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioTrack;
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.StringCompareMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.plugin.config.*;
import com.github.topi314.lavasrc.protocol.Config;
import com.github.topi314.lavasrc.jiosaavn.JioSaavnAudioSourceManager;
import com.github.topi314.lavasrc.proxy.ProxyConfig;
import com.github.topi314.lavasrc.proxy.ProxyManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.tidal.TidalSourceManager;
import com.github.topi314.lavasrc.vkmusic.VkMusicSourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.youtube.YoutubeSearchManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@Service
@RestController
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration, LyricsManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

	private final SourcesConfig sourcesConfig;
	private final LyricsSourcesConfig lyricsSourcesConfig;
	private final AdvancedMirrorConfig advancedMirrorConfig;
	private AudioPlayerManager manager;
	private SpotifySourceManager spotify;
	private AppleMusicSourceManager appleMusic;
	private DeezerAudioSourceManager deezer;
	private YandexMusicSourceManager yandexMusic;
	private FloweryTTSSourceManager flowerytts;
	private YoutubeSearchManager youtube;
	private VkMusicSourceManager vkMusic;
	private JioSaavnAudioSourceManager jioSaavn;
	private TidalSourceManager tidal;

	public LavaSrcPlugin(
		LavaSrcConfig pluginConfig,
		SourcesConfig sourcesConfig,
		LyricsSourcesConfig lyricsSourcesConfig,
		AdvancedMirrorConfig advancedMirrorConfig,
		SpotifyConfig spotifyConfig,
		AppleMusicConfig appleMusicConfig,
		DeezerConfig deezerConfig,
		YandexMusicConfig yandexMusicConfig,
		FloweryTTSConfig floweryTTSConfig,
		YouTubeConfig youTubeConfig,
		VkMusicConfig vkMusicConfig,
		JioSaavnConfig jioSaavnConfig,
		TidalConfig tidalConfig) {

		log.info("Loading LavaSrc plugin...");
		this.sourcesConfig = sourcesConfig;
		this.lyricsSourcesConfig = lyricsSourcesConfig;
		this.advancedMirrorConfig = advancedMirrorConfig;

		MirroringAudioTrackResolver defaultresolver = new DefaultMirroringAudioTrackResolver(pluginConfig.getProviders());
		if (this.advancedMirrorConfig != null && this.advancedMirrorConfig.getSources() != null && this.advancedMirrorConfig.getSources().length > 0) {
			defaultresolver = new StringCompareMirroringAudioTrackResolver(
				this.advancedMirrorConfig.getSources(),
				pluginConfig.getProviders(),
				this.advancedMirrorConfig.getTitleThreshold(),
				this.advancedMirrorConfig.getAuthorThreshold(),
				this.advancedMirrorConfig.getTotalMatchThreshold(),
				this.advancedMirrorConfig.isSkipSoundCloudGo(),
				this.advancedMirrorConfig.getLevelOnePenalty(),
				this.advancedMirrorConfig.getLevelTwoPenalty(),
				this.advancedMirrorConfig.getLevelThreePenalty()
			);

			log.info("Advanced Mirroring resolver enabled for sources: " + Arrays.toString(this.advancedMirrorConfig.getSources()));
		}


		if (sourcesConfig.isSpotify() || lyricsSourcesConfig.isSpotify()) {
			this.spotify = new SpotifySourceManager(spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.getSpDc(),
				spotifyConfig.getCountryCode(), unused -> manager, defaultresolver);
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
			if (!spotifyConfig.isResolveArtistsInSearch()) {
				this.spotify.setResolveArtistsInSearch(spotifyConfig.isResolveArtistsInSearch());
			}
			if (spotifyConfig.isLocalFiles()) {
				this.spotify.setLocalFiles(spotifyConfig.isLocalFiles());
			}
		}
		if (sourcesConfig.isAppleMusic()) {
			this.appleMusic = new AppleMusicSourceManager(appleMusicConfig.getMediaAPIToken(), appleMusicConfig.getCountryCode(), unused -> manager, defaultresolver);
			if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusic.setPlaylistPageLimit(appleMusicConfig.getPlaylistLoadLimit());
			}
			if (appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusic.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
			}
		}

		if (sourcesConfig.isDeezer()) {
			ProxyConfig[] proxies = deezerConfig.getProxies();
			ProxyManager proxyManager = (proxies != null && proxies.length > 0) ? new ProxyManager(proxies, deezerConfig.isUseLocalNetwork()) : null;
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey(), deezerConfig.getArl(), deezerConfig.getFormats(), proxyManager);
		}

		if (sourcesConfig.isYandexMusic() || lyricsSourcesConfig.isYandexMusic()) {
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
			if (yandexMusicConfig.getPlaylistLoadLimit() > 0) {
				yandexMusic.setPlaylistLoadLimit(yandexMusicConfig.getPlaylistLoadLimit());
			}
			if (yandexMusicConfig.getAlbumLoadLimit() > 0) {
				yandexMusic.setAlbumLoadLimit(yandexMusicConfig.getAlbumLoadLimit());
			}
			if (yandexMusicConfig.getArtistLoadLimit() > 0) {
				yandexMusic.setArtistLoadLimit(yandexMusicConfig.getArtistLoadLimit());
			}
		}
		if (sourcesConfig.isFloweryTTS()) {
			this.flowerytts = new FloweryTTSSourceManager(floweryTTSConfig.getVoice());
			if (floweryTTSConfig.getTranslate()) {
				this.flowerytts.setTranslate(floweryTTSConfig.getTranslate());
			}
			if (floweryTTSConfig.getSilence() > 0) {
				this.flowerytts.setSilence(floweryTTSConfig.getSilence());
			}
			if (floweryTTSConfig.getSpeed() > 0) {
				this.flowerytts.setSpeed(floweryTTSConfig.getSpeed());
			}
			if (floweryTTSConfig.getAudioFormat() != null) {
				this.flowerytts.setAudioFormat(floweryTTSConfig.getAudioFormat());
			}
		}
		if (sourcesConfig.isYoutube() || lyricsSourcesConfig.isYoutube()) {
			if (hasNewYoutubeSource()) {
				log.info("Registering Youtube Source audio source manager...");
				this.youtube = new YoutubeSearchManager(() -> manager, youTubeConfig.getCountryCode());
			} else {
				throw new IllegalStateException("Youtube LavaSearch requires the new Youtube Source plugin to be enabled.");
			}
		}
		if (sourcesConfig.isVkMusic() || lyricsSourcesConfig.isVkMusic()) {
			this.vkMusic = new VkMusicSourceManager(vkMusicConfig.getUserToken());
			if (vkMusicConfig.getPlaylistLoadLimit() > 0) {
				vkMusic.setPlaylistLoadLimit(vkMusicConfig.getPlaylistLoadLimit());
			}
			if (vkMusicConfig.getArtistLoadLimit() > 0) {
				vkMusic.setArtistLoadLimit(vkMusicConfig.getArtistLoadLimit());
			}
			if (vkMusicConfig.getRecommendationLoadLimit() > 0) {
				vkMusic.setRecommendationsLoadLimit(vkMusicConfig.getRecommendationLoadLimit());
			}
		}

		if (sourcesConfig.isJiosaavn()) {
			if(jioSaavnConfig.getDecryption() == null || jioSaavnConfig.getDecryption().getSecretKey() == null) {
				log.warn("JioSaavn decryption config is missing, JioSaavn will not work properly");
			}
			ProxyConfig[] proxies = jioSaavnConfig.getProxies();
			ProxyManager proxyManager = (proxies != null && proxies.length > 0) ? new ProxyManager(proxies, jioSaavnConfig.isUseLocalNetwork()) : null;
			this.jioSaavn = new JioSaavnAudioSourceManager(jioSaavnConfig.getGenericProxy(), proxyManager, jioSaavnConfig.getDecryption());
		}

		if (sourcesConfig.isTidal()) {
			this.tidal = new TidalSourceManager(tidalConfig.getCountryCode(), unused -> this.manager, defaultresolver, tidalConfig.getToken());
			if (tidalConfig.getSearchLimit() > 0) {
				this.tidal.setSearchLimit(tidalConfig.getSearchLimit());
			}
		}
	}

	private boolean hasNewYoutubeSource() {
		try {
			Class.forName("dev.lavalink.youtube.YoutubeAudioSourceManager");
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
		this.manager = manager;
		if (this.spotify != null && this.sourcesConfig.isSpotify()) {
			log.info("Registering Spotify audio source manager...");
			manager.registerSourceManager(this.spotify);
		}
		if (this.appleMusic != null) {
			log.info("Registering Apple Music audio source manager...");
			manager.registerSourceManager(this.appleMusic);
		}
		if (this.deezer != null && this.sourcesConfig.isDeezer()) {
			log.info("Registering Deezer audio source manager...");
			manager.registerSourceManager(this.deezer);
		}
		if (this.yandexMusic != null) {
			log.info("Registering Yandex Music audio source manager...");
			manager.registerSourceManager(this.yandexMusic);
		}
		if (this.flowerytts != null) {
			log.info("Registering Flowery TTS audio source manager...");
			manager.registerSourceManager(this.flowerytts);
		}
		if (this.vkMusic != null) {
			log.info("Registering Vk Music audio source manager...");
			manager.registerSourceManager(this.vkMusic);
		}

		if (this.jioSaavn != null) {
			log.info("Registering JioSaavn audio source manager...");
			manager.registerSourceManager(this.jioSaavn);
		}

		if (this.tidal != null) {
			log.info("Registering Tidal audio source manager...");
			manager.registerSourceManager(this.tidal);
		}

		return manager;
	}

	@Override
	@NotNull
	public SearchManager configure(@NotNull SearchManager manager) {
		if (this.spotify != null && this.sourcesConfig.isSpotify()) {
			log.info("Registering Spotify search manager...");
			manager.registerSearchManager(this.spotify);
		}
		if (this.appleMusic != null && this.sourcesConfig.isAppleMusic()) {
			log.info("Registering Apple Music search manager...");
			manager.registerSearchManager(this.appleMusic);
		}
		if (this.deezer != null && this.sourcesConfig.isDeezer()) {
			log.info("Registering Deezer search manager...");
			manager.registerSearchManager(this.deezer);
		}
		if (this.youtube != null && this.sourcesConfig.isYoutube()) {
			log.info("Registering Youtube search manager...");
			manager.registerSearchManager(this.youtube);
		}
		if (this.yandexMusic != null && this.sourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music search manager...");
			manager.registerSearchManager(this.yandexMusic);
		}
		if (this.vkMusic != null && this.sourcesConfig.isVkMusic()) {
			log.info("Registering VK Music search manager...");
			manager.registerSearchManager(this.vkMusic);
		}


		if (this.jioSaavn != null && this.sourcesConfig.isJiosaavn()) {
			log.info("Registering Saavn search manager...");
			manager.registerSearchManager(this.jioSaavn);
		}

		if (this.tidal != null && this.sourcesConfig.isTidal()) {
			log.info("Registering Tidal search manager...");
			manager.registerSearchManager(this.tidal);
		}

		return manager;
	}

	@NotNull
	@Override
	public LyricsManager configure(@NotNull LyricsManager manager) {
		if (this.spotify != null && this.lyricsSourcesConfig.isSpotify()) {
			log.info("Registering Spotify lyrics manager...");
			manager.registerLyricsManager(this.spotify);
		}
		if (this.deezer != null && this.lyricsSourcesConfig.isDeezer()) {
			log.info("Registering Deezer lyrics manager...");
			manager.registerLyricsManager(this.deezer);
		}
		if (this.youtube != null && this.lyricsSourcesConfig.isYoutube()) {
			log.info("Registering YouTube lyrics manager...");
			manager.registerLyricsManager(this.youtube);
		}
		if (this.yandexMusic != null && this.lyricsSourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music lyrics manager");
			manager.registerLyricsManager(this.yandexMusic);
		}
		if (this.vkMusic != null && this.lyricsSourcesConfig.isVkMusic()) {
			log.info("Registering VK Music lyrics manager...");
			manager.registerLyricsManager(this.vkMusic);
		}

		return manager;
	}

	@PatchMapping("/v4/lavasrc/config")
	public void updateConfig(Config config) {
		var spotifyConfig = config.getSpotify();
		if (spotifyConfig != null && this.spotify != null) {
			if (spotifyConfig.getSpDc() != null) {
				this.spotify.setSpDc(spotifyConfig.getSpDc());
			}
			if (spotifyConfig.getClientId() != null && spotifyConfig.getClientSecret() != null) {
				this.spotify.setClientIDSecret(spotifyConfig.getClientId(), spotifyConfig.getClientSecret());
			}
		}

		var appleMusicConfig = config.getAppleMusic();
		if (appleMusicConfig != null && this.appleMusic != null && appleMusicConfig.getMediaAPIToken() != null) {
			this.appleMusic.setMediaAPIToken(appleMusicConfig.getMediaAPIToken());
		}

		var deezerConfig = config.getDeezer();
		if (deezerConfig != null && this.deezer != null) {
			if (deezerConfig.getArl() != null) {
				this.deezer.setArl(deezerConfig.getArl());
			}
			if (deezerConfig.getFormats() != null) {
				this.deezer.setFormats(deezerConfig.getFormats()
					.stream()
					.map(deezerTrackFormat -> DeezerAudioTrack.TrackFormat.from(deezerTrackFormat.name()))
					.toList()
					.toArray(new DeezerAudioTrack.TrackFormat[0])
				);
			}
		}

		var yandexMusicConfig = config.getYandexMusic();
		if (yandexMusicConfig != null && this.yandexMusic != null) {
			if (yandexMusicConfig.getAccessToken() != null) {
				this.yandexMusic.setAccessToken(yandexMusicConfig.getAccessToken());
			}
		}

		var vkMusicConfig = config.getVkMusic();
		if (vkMusicConfig != null && this.vkMusic != null) {
			if (vkMusicConfig.getUserToken() != null) {
				this.vkMusic.setUserToken(vkMusicConfig.getUserToken());
			}
		}
	}
}
