package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(
	prefix = "plugins.lavasrc.advancedmirroring"
)
@Component
public class AdvancedMirrorConfig {
	private String[] sources;
	private Float titleThreshold = 50.0F;
	private Float authorThreshold = 70.0F;
	private Float totalMatchThreshold = 196.0F;
	private boolean skipSoundCloudGo = false;
	private Float levelOnePenalty = 1F;
	private Float levelTwoPenalty = 2F;
	private Float levelThreePenalty = 0.8F;

	public Float getTitleThreshold() {
		return this.titleThreshold;
	}

	public void setTitleThreshold(Float titleThreshold) {
		this.titleThreshold = titleThreshold;
	}

	public Float getAuthorThreshold() {
		return this.authorThreshold;
	}

	public void setAuthorThreshold(Float authorThreshold) {
		this.authorThreshold = authorThreshold;
	}

	public Float getTotalMatchThreshold() {
		return this.totalMatchThreshold;
	}

	public void setTotalMatchThreshold(Float totalMatchThreshold) {
		this.totalMatchThreshold = totalMatchThreshold;
	}

	public boolean isSkipSoundCloudGo() {
		return this.skipSoundCloudGo;
	}

	public void setSkipSoundCloudGo(boolean skipSoundCloudGo) {
		this.skipSoundCloudGo = skipSoundCloudGo;
	}

	public Float getLevelOnePenalty() {
		return this.levelOnePenalty;
	}

	public void setLevelOnePenalty(Float levelOnePenalty) {
		this.levelOnePenalty = levelOnePenalty;
	}

	public Float getLevelTwoPenalty() {
		return this.levelTwoPenalty;
	}

	public void setLevelTwoPenalty(Float levelTwoPenalty) {
		this.levelTwoPenalty = levelTwoPenalty;
	}

	public Float getLevelThreePenalty() {
		return this.levelThreePenalty;
	}

	public void setLevelThreePenalty(Float levelThreePenalty) {
		this.levelThreePenalty = levelThreePenalty;
	}

	public String[] getSources() {
		return this.sources;
	}

	public void setSources(String[] sources) {
		this.sources = sources;
	}
}
