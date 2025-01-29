package com.github.topi314.lavasrc.proxy;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

public class ProxyConfig {
	private String protocol = "http";
	private String host;
	private int port;
	private String user;
	private String password;

	public String getProtocol() {
		return this.protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public RequestConfig configureRequest(RequestConfig requestConfig) {
		var proxy = new HttpHost(this.getHost(), this.getPort(), this.getProtocol());

		return RequestConfig.copy(requestConfig)
			.setProxy(proxy)
			.build();
	}

	public void configureBuilder(HttpClientBuilder httpClientBuilder) {
		if(this.user == null || this.password == null) return;
		var credentialsProvider = new BasicCredentialsProvider();

		credentialsProvider.setCredentials(
			new AuthScope(this.getHost(), this.getPort()),
			new UsernamePasswordCredentials(this.getUser(), this.getPassword())
		);


		if (this.getUser() != null && !this.getUser().isBlank()) {
			httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
		}
	}

	public ProxyConfig() {
	}

	public ProxyConfig(String proxyProtocol, String host, int proxyPort, String proxyUser, String proxyPassword) {
		this.protocol = proxyProtocol;
		this.host = host;
		this.port = proxyPort;
		this.user = proxyUser;
		this.password = proxyPassword;
	}

	public ProxyConfig(String host, int proxyPort) {
		this.host = host;
		this.port = proxyPort;
	}
}
