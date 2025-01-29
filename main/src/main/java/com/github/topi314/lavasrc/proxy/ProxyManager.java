package com.github.topi314.lavasrc.proxy;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;


public class ProxyManager implements HttpInterfaceManager {
	private static final Logger log = LoggerFactory.getLogger(ProxyManager.class);

	private final List<HttpInterfaceManager> interfaceManagers = new CopyOnWriteArrayList<>();
	private int currentManagerIndex = 0;
	private final HttpInterfaceManager localManager;

	public ProxyManager(ProxyConfig[] configs, boolean useLocalnetwork) {
		for (var config : configs) {
			var manager = HttpClientTools.createCookielessThreadLocalManager();
			manager.configureRequests(config::configureRequest);
			manager.configureBuilder(config::configureBuilder);
			this.interfaceManagers.add(manager);
		}

		this.localManager = HttpClientTools.createCookielessThreadLocalManager();
		if(useLocalnetwork) {
			this.interfaceManagers.add(localManager);
			log.debug("Created local proxy manager");
		}

		log.debug("Initialized {} proxy managers", interfaceManagers);
	}

	public synchronized HttpInterfaceManager getNextHttpInterfaceManager() {
		var manager = interfaceManagers.get(currentManagerIndex);
		log.debug("Using proxied interface manager number {}", currentManagerIndex);
		currentManagerIndex = (currentManagerIndex + 1) % interfaceManagers.size();
		return manager;
	}

	public HttpInterfaceManager getLocalManager() {
		log.debug("Using local proxy manager");
		return localManager;
	}

	public void close() throws IOException {
		for(var manager : interfaceManagers) {
			try {
				manager.close();
			} catch (IOException e) {
				log.error("Failed to close HTTP interface manager", e);
			}
		}
	}

	@Override
	public HttpInterface getInterface() {
		return getNextHttpInterfaceManager().getInterface();
	}

	@Override
	public void setHttpContextFilter(HttpContextFilter filter) {
		interfaceManagers.forEach(manager -> manager.setHttpContextFilter(filter));
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		interfaceManagers.forEach(manager -> manager.configureRequests(configurator));
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		interfaceManagers.forEach(manager -> manager.configureBuilder(configurator));

	}
}