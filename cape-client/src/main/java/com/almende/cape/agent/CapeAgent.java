package com.almende.cape.agent;

import com.almende.eve.agent.Agent;
import com.almende.eve.agent.AgentFactory;
import com.almende.eve.context.Context;
import com.almende.eve.agent.annotation.Name;
import com.almende.eve.agent.annotation.Required;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.eve.transport.xmpp.XmppService;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The abstract class CapeServiceAgent contains methods to set an xmpp account 
 * and to register itself with the merlin agent.
 */
public abstract class CapeAgent extends Agent {
	// TODO: do not hard-code the merlin agent url.
    protected static String MERLIN_URL = "xmpp:merlin@openid.almende.org"; 

    /**
	 * Set xmpp account for the agent.
	 * Only applicable when no account has been set yet (in that case, use
	 * changeAccount instead).
	 * @param username
	 * @param password
	 * @param resource
	 * @throws Exception
	 */
	public void setAccount(
			@Name("username") String username, 
			@Name("password") String password,
			@Name("resource") String resource) throws Exception {
		changeAccount(null, null, username, password, resource);
	}
	
	// TODO: remove this method getEverything!!!
	public Object getEverything() {
		return getContext();
	}
	
	/**
	 * Update the current xmpp account
	 * @param oldUsername
	 * @param newUsername
	 * @param oldPassword
	 * @param newPassword
	 * @throws Exception
	 */
	public void changeAccount(
			@Name("oldUsername") @Required(false) String oldUsername, 
			@Name("oldPassword") @Required(false) String oldPassword,
			@Name("newUsername") String newUsername, 
			@Name("newPassword") String newPassword,
			@Name("newResource") String newResource) throws Exception {
		// TODO: do not store password! (at least not plain text)
		
		if (verifyAccount(oldUsername, oldPassword)) {
			disconnect();
			
			Context context = getContext();
			context.put("xmppUsername", newUsername);
			context.put("xmppPassword", newPassword);
			if (newResource != null) {
				context.put("xmppResource", newResource);
			}
			else {
				context.remove("xmppResource");
			}
		}
		else {
			throw new Exception("Cannot change account: " +
					"old username or password does not match");
		}
	}
	
	/**
	 * Remove current xmpp account
	 * @param username
	 * @param password
	 * @throws Exception
	 */
	public void removeAccount(@Name("username") String username, 
			@Name("password") String password) {
		if (verifyAccount(username, password)) {
			try {
				disconnect();
			}
			catch (Exception e) {}
			
			Context context = getContext();
			context.remove("xmppUsername");
			context.remove("xmppPassword");
			context.remove("xmppResource");
		}
	}
		
	/**
	 * Check whether given username and password match the stored username and
	 * password. Returns true when no username and password have been saved.
	 * @param username
	 * @param password
	 */
	protected boolean verifyAccount(String username, String password) {
		Context context = getContext();
		String currentUsername = (String) context.get("xmppUsername");
		String currentPassword = (String) context.get("xmppPassword");
		
		return  (currentUsername == null || currentUsername.equals(username)) &&
				(currentPassword == null || currentPassword.equals(password));
	}
	
	/**
	 * Connect the agent to the xmpp server
	 * @throws Exception
	 */
	public void connect() throws Exception {
		AgentFactory factory = getAgentFactory();
		XmppService service = (XmppService) factory.getTransportService("xmpp");
		if (service != null) {
			Context context = getContext();
			String username = (String) context.get("xmppUsername");
			String password = (String) context.get("xmppPassword");
			String resource = (String) context.get("xmppResource");
			if (username == null) {
				throw new Exception(
					"Cannot connect: no username set. " +
					"Please set a username and password using the method setAccount first.");
			}
			if (password == null) {
				throw new Exception(
					"Cannot connect: no password set for user '" + username + "'. " +
					"Please set a username and password using the method setAccount first.");
			}
			service.connect(getId(), username, password, resource);
		}
		else {
			throw new Exception("No XMPP service registered");
		}
	}
	
	/**
	 * Disconnect the agent from the xmpp server
	 * @throws Exception
	 */
	public void disconnect() throws Exception {
		AgentFactory factory = getAgentFactory();
		XmppService service = (XmppService) factory.getTransportService("xmpp");
		if (service != null) {
			service.disconnect(getId());
		}
		else {
			throw new Exception("No XMPP service registered");
		}
	}
	
	/**
	 * Register the agent as providing data information of a specific type
	 * @param dataType
	 * @throws Exception
	 */
	protected void register(@Name("dataType") String dataType) throws Exception {
		String username = getUsername();
		if (username == null) {
			throw new IllegalArgumentException("No username set. " +
					"Set username and password first using setAccount().");
		}
		
		String method = "register";
		ObjectNode params = JOM.createObjectNode();
		ObjectNode dataSource = JOM.createObjectNode();
		dataSource.put("userId", username);
		dataSource.put("agentUrl", getXmppUrl());
		dataSource.put("dataType", dataType);
		params.put("dataSource", dataSource);
		// TODO: replace ObjectNode with DataSource
		send(MERLIN_URL, method, params);		
	}

	protected String getUsername() {
		return (String) getContext().get("xmppUsername");
	}
	
	/**
	 * Unregister the agent as providing data information of a specific type
	 * @param dataType
	 * @throws Exception
	 */
	protected void unregister(@Name("dataType") String dataType) throws Exception {
		String username = getUsername();
		
		if (username != null) {
			String method = "unregister";
			ObjectNode params = JOM.createObjectNode();
			ObjectNode dataSource = JOM.createObjectNode();
			dataSource.put("userId", username);
			dataSource.put("agentUrl", getXmppUrl());
			dataSource.put("dataType", dataType);
			params.put("dataSource", dataSource);
			// TODO: replace ObjectNode with DataSource			
			send(MERLIN_URL, method, params);
		}
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
}
