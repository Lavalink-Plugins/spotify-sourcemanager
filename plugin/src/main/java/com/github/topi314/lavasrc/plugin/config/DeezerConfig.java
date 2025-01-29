package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.proxy.ProxyConfig;
import com.github.topi314.lavasrc.deezer.DeezerAudioTrack;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {

	private String masterDecryptionKey;
	private String arl;
	private DeezerAudioTrack.TrackFormat[] formats;
	private ProxyConfig[] proxies;
	private boolean useLocalNetwork;

	public String getMasterDecryptionKey() {
		return this.masterDecryptionKey;
	}

	public void setMasterDecryptionKey(String masterDecryptionKey) {
		this.masterDecryptionKey = masterDecryptionKey;
	}

	public String getArl() {
		return this.arl;
	}

	public void setArl(String arl) {
		this.arl = arl;
	}

	public DeezerAudioTrack.TrackFormat[] getFormats() {
		return this.formats;
	}

	public void setFormats(DeezerAudioTrack.TrackFormat[] formats) {
		this.formats = formats;
	}

	public ProxyConfig[] getProxies() {
		return this.proxies;
	}

	public void setProxies(ProxyConfig[] proxies) {
		this.proxies = proxies;
	}

	public boolean isUseLocalNetwork() {
		return useLocalNetwork;
	}

	public void setUseLocalNetwork(boolean useLocalNetwork) {
		this.useLocalNetwork = useLocalNetwork;
	}

}
