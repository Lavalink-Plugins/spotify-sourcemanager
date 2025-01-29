package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.jiosaavn.JioSaavnDecryptionConfig;
import com.github.topi314.lavasrc.proxy.ProxyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.jiosaavn")
@Component
public class JioSaavnConfig {
	private ProxyConfig[] proxies;
	private String genericProxy;
	private boolean useLocalNetwork;
	private JioSaavnDecryptionConfig decryption;

	public ProxyConfig[] getProxies() {
		return this.proxies;
	}

	public void setProxies(ProxyConfig[] proxies) {
		this.proxies = proxies;
	}

	public String getGenericProxy() {
		return this.genericProxy;
	}

	public void setGenericProxy(String genericProxy) {
		this.genericProxy = genericProxy;
	}

	public boolean isUseLocalNetwork() {
		return useLocalNetwork;
	}

	public void setUseLocalNetwork(boolean useLocalNetwork) {
		this.useLocalNetwork = useLocalNetwork;
	}

	public JioSaavnDecryptionConfig getDecryption() {
		return decryption;
	}

	public void setDecryption(JioSaavnDecryptionConfig decryption) {
		this.decryption = decryption;
	}
}
