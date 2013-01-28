package com.almende.cape.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.almende.cape.handler.NotificationHandler;
import com.almende.cape.handler.StateChangeHandler;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.annotation.Name;
import com.almende.eve.agent.annotation.Required;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CapeClientAgent extends Agent {
	// TODO: do not hard-code the merlin agent url.
    private static String MERLIN_URL = "xmpp:merlin@openid.almende.org"; 
	
	/**
	 * Retrieve contacts
	 * @param contactFilter
	 * @return contacts
	 * @throws Exception 
	 */
	// TODO: replace arrayNode for List<Contact> and ObjectNode with java class Contact?
	public ArrayNode getContacts(
			@Name("contactFilter") @Required(false) ObjectNode filter) 
			throws Exception {
		String userId = getId();
		String dataType = "contacts";
		String contactAgentUrl = findDataSource(userId, dataType);
		if (contactAgentUrl == null) {
			throw new Exception(
					"No data source found containing contacts of user " + getId());
		}
		// TODO: cache the retrieved data source for some time
		
		String method = "getContacts";
		ObjectNode params = JOM.createObjectNode();
		String filterStr = (filter != null) ? JOM.getInstance().writeValueAsString(filter) : "";
		params.put("filter", filterStr);
		String contacts = send(contactAgentUrl, method, params, String.class);
		ArrayNode array = JOM.getInstance().readValue(contacts, ArrayNode.class);
		
		return array;
	}

	/**
	 * Send a notification to any user
	 * @param userId  can be null
	 * @param message
	 * @throws Exception 
	 */
	public void sendNotification(@Required(false) @Name("userId") String userId, 
			@Name("message") String message) throws Exception {
		if (userId == null) {
			userId = getId();
		}
		String dataType = "dialog";
		
		// find an agent which can handle a dialog with the user
		String notificationAgentUrl = findDataSource(userId, dataType);
		if (notificationAgentUrl == null) {
			throw new Exception(
					"No data source found supporting a dialog with user " + userId);
		}
		
		// send the notification
		String method = "onNotification";
		ObjectNode params = JOM.createObjectNode();
		params.put("message", message);
		send(notificationAgentUrl, method, params);
	}

	/**
	 * Attach a notification handler to the agent.
	 * The notification handler will be available as long as the agent is 
	 * instantiated, but is not persisted. 
	 * @param notificationHandler
	 * @throws Exception 
	 */
	public void setNotificationHandler(NotificationHandler handler) 
			throws Exception {
		notificationHandlers.put(getId(), handler);
		
		logger.info("Registering dialog support at the MerlinAgent");
		
		String method = "register";
		ObjectNode params = JOM.createObjectNode();
		ObjectNode dataSource = JOM.createObjectNode();
		dataSource.put("userId", getId());
		dataSource.put("agentUrl", getXmppUrl());
		dataSource.put("dataType", "dialog");
		params.put("dataSource", dataSource);
		send(MERLIN_URL, method, params);

		logger.info("Registered dialog support at the MerlinAgent");
	}

	/**
	 * Remove the currently attached notification handler.
	 * @throws Exception 
	 */
	public void removeNotificationHandler() throws Exception {
		if (notificationHandlers.containsKey(getId())) {
			logger.info("Unregistering dialog support at the MerlinAgent");
			
			String method = "unregister";
			ObjectNode params = JOM.createObjectNode();
			ObjectNode dataSource = JOM.createObjectNode();
			dataSource.put("userId", getId());
			dataSource.put("agentUrl", getXmppUrl());
			dataSource.put("dataType", "dialog");
			params.put("dataSource", dataSource);
			send(MERLIN_URL, method, params);

			logger.info("Unregistered dialog support at the MerlinAgent");			

			notificationHandlers.remove(getId());
		}
	}
	
	/**
	 * Receive a notification and dispatch it to the attached notification 
	 * handler. If no notification handler is attached, nothing happens.
	 * @param message
	 */
	public void onNotification(@Name("message") String message) {
		NotificationHandler handler = notificationHandlers.get(getId());
		if (handler != null) {
			handler.onNotification(message);			
		}
	}
	
	/**
	 * Receive a notification and dispatch it to the attached notification 
	 * handler. If no notification handler is attached, nothing happens.
	 * @param message
	 * @throws Exception 
	 */
	public void addStateChangeHandler(String userId, String state, 
			StateChangeHandler handler) throws Exception {
		if (userId == null) {
			userId = getId();
		}

		logger.info("Registering state handler for userId=" + userId + ", state=" + state);
		String agentUrl = findDataSource(userId, "state");

		// TODO: actually subscribe to the provided state (for example "location")
		
		logger.info("Found agent providing this state, url=" + agentUrl + ". subscribing...");
		subscribe(agentUrl, "change", "onStateChange");
		
		stateChangeHandlers.put(agentUrl, handler);

		logger.info("Registered state handler");
	}
	
	/**
	 * Remove a registered state change handler
	 * @param handler
	 * @throws Exception 
	 */
	public void removeStateChangeHandler(StateChangeHandler handler) throws Exception {
		for (String agentUrl : stateChangeHandlers.keySet()) {
			if (stateChangeHandlers.get(agentUrl) == handler) {
				unsubscribe(agentUrl, "change", "onStateChange");
				stateChangeHandlers.remove(agentUrl);
			}
		}
	}
	
	/**
	 * Remove all registered state change handlers
	 * @throws Exception 
	 */
	public void removeStateChangeHandlers() throws Exception {
		for (String agentUrl : stateChangeHandlers.keySet()) {
			unsubscribe(agentUrl, "change", "onStateChange");
			stateChangeHandlers.remove(agentUrl);
		}
	}
	
	/**
	 * Handle an state change trigger
	 * @param agent
	 * @param event
	 * @param params
	 */
	public void onStateChange(@Name("agent") String agent,
	        @Name("event") String event, 
	        @Required(false) @Name("params") ObjectNode params) {
		StateChangeHandler handler = stateChangeHandlers.get(agent);
		if (handler != null) {
			handler.onChange(params);
		}
	}
	
	/**
	 * Find a datasource for a specific userid and datatype
	 * @param userId
	 * @param dataType
	 * @return contactAgentUrl
	 * @throws Exception
	 */
	private String findDataSource(String userId, String dataType) throws Exception {
		String method = "find";
		ObjectNode params = JOM.createObjectNode();
		params.put("userId", userId);
		params.put("dataType", dataType);

		logger.info("Requesting the MerlinAgent for a dataSource with userId=" + 
				userId + " and dataType=" + dataType);

		ArrayNode contactSources = send(MERLIN_URL, method, params, ArrayNode.class);
		if (contactSources.size() > 0) {
			ObjectNode contactSource = (ObjectNode) contactSources.get(0);

			logger.info("Retrieved dataSource from MerlinAgent: " + contactSource);

			return contactSource.get("agentUrl").asText();
		}
		return null;
	}

	/**
	 * Get the xmpp url of this agent. Will return null if there is no xmpp url.
	 * @return url
	 */
	private String getXmppUrl() {
		for (String url : getUrls()) {
			if (url.startsWith("xmpp:")) {
				return url;
			}
		}
		return null;
	}
	
	@Override
	public String getDescription() {
		return "CAPE Client Agent";
	}

	@Override
	public String getVersion() {
		return "0.1";
	}

	// TODO: the notification handler and stateChange handlers are a singleton 
	//       per user right now, as we cannot have a single, continuously 
	//       instantiated agent: the AgentFactory  will instantiate a new 
	//       instance of our agent on incoming calls.
	private static Map<String, NotificationHandler> notificationHandlers = 
			new ConcurrentHashMap<String, NotificationHandler>();
	private static Map<String, StateChangeHandler> stateChangeHandlers = 
			new ConcurrentHashMap<String, StateChangeHandler>();
	
	private Logger logger = Logger.getLogger(this.getClass().getSimpleName());
}