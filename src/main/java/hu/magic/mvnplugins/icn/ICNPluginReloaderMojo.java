package hu.magic.mvnplugins.icn;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ICN plugin reloader maven plugin
 * 
 * Inspired by https://github.com/gdelory/icn-plugin-loader
 * 
 * @author jmalyik
 *
 */
@Mojo(name = "icn-reload-plugin", defaultPhase = LifecyclePhase.PACKAGE)
public class ICNPluginReloaderMojo extends AbstractMojo {
	/**
	 * ICN desktop to use for the admin, any desktop can be used but hard coded
	 * admin will always work and make configuration easier for users than
	 * asking them for one
	 */
	private static final String DESKTOP = "admin";
	private static final String SAVE_URL = "jaxrs/admin/configuration";
	private static final String LOAD_URL = "jaxrs/admin/loadPlugin";
	private static final String LOGON_URL = "jaxrs/logon";
	private static final int DEFAULT_TIMEOUT = 5000;
	/**
	 * IBM Content Navigator base URL, e.g.: http://navigatorhost/navigator/
	 */
	@Parameter(property = "url", defaultValue = "http://navigatorhost/navigator/", required=true)
	private String url;
	/**
	 * The name of the ICN plugin jar file
	 */
	@Parameter(property = "jarfile", defaultValue = "plugin.jar", required=true)
	private String jarfile;
	/**
	 * Content Navigator administrator user who has the right to use the admin
	 * desktop
	 */
	@Parameter(property = "username", defaultValue = "cnadmin", required=true)
	private String username;
	/**
	 * the password of the administrator user
	 */
	@Parameter(property = "password", required=true)
	private String password;

	/**
	 * socket timeout in millisecond
	 */
	@Parameter(property = "timeout", defaultValue = "5000")
	private String timeout;

	@Override
	public void execute() throws MojoFailureException {
		HttpClient httpclient = new HttpClient();
		httpclient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
		httpclient.getParams().setSoTimeout(getTimeOut());
		// Logon, reload and save configuration
		String security_token = logon(httpclient);
		if (security_token != null) {
			JSONObject loadResult = reload(httpclient, security_token);
			if (loadResult != null) {
				save(httpclient, loadResult, security_token);
			}
		}
	}

	/**
	 * returns with the socket timeout used by the httpclient to connect to ICN
	 * @return
	 */
	private int getTimeOut() {
		try {
			return Integer.parseInt(timeout);
		} catch (Exception e) {
			getLog().warn("Can not parse the timeout parameter value '" + timeout
					+ "' to number! Using default value " + DEFAULT_TIMEOUT);
		}
		return DEFAULT_TIMEOUT;
	}

	/**
	 * Log on against ICN, this will store the needed cookies and return the
	 * security token header. Both are needed for a successful authentication.
	 * 
	 * @param httpClient
	 *            the {@link HttpClient} connection to use, this will have to be
	 *            used for all future calls since it gets the authentication
	 *            cookies.
	 * @param log
	 *            the logger as {@link PrintStream}
	 * @return the security token, <code>null</code> if anything went wrong.
	 *         Exception is already logged if <code>null</code> is returned.
	 * @throws MojoExecutionException
	 */
	private String logon(HttpClient httpClient) throws MojoFailureException {
		getLog().info("Connecting to ICN as " + username + "...");

		String res = null;

		PostMethod httpPost = new PostMethod(url + LOGON_URL);
		httpPost.addParameter(new NameValuePair("userid", username));
		httpPost.addParameter(new NameValuePair("password", password));
		httpPost.addParameter(new NameValuePair("desktop", DESKTOP));
		String json = null;
		try {
			httpClient.executeMethod(httpPost);
			getLog().debug(httpPost.getStatusLine().toString());
			json = new BufferedReader(new InputStreamReader(httpPost.getResponseBodyAsStream())).readLine();
			// Unsecure the json if prefix is activated in servlet

			if (json.startsWith("{}&&")) {
				json = json.substring(4);
			}

			JSONObject jsonObj = new JSONObject(json);

			if (!jsonObj.has("security_token")) {
				getLog().debug("ERROR: Exception while logging into ICN. Response was " + json);
			} else {
				res = (String) jsonObj.get("security_token");
				if (res != null && !"".equals(res)) {
					getLog().info("OK");
				} else {
					getLog().error("Logon failed.");
				}
			}

		} catch (Exception e) {
			throw new MojoFailureException("Logon failed", e);
		} finally {
			httpPost.releaseConnection();
		}
		return res;
	}

	/**
	 * Reload the plugin from the given path.
	 * 
	 * @param httpClient
	 *            the {@link HttpClient} to use, it needs to have the
	 *            authentication cookies, brought by a call to the logon method.
	 * @param log
	 *            The {@link PrintStream} to print information to
	 * @param security_token
	 *            the security token to use as header. This is returned by the
	 *            logon method.
	 * @return the result of the call, will be needed to save the configuration
	 * @throws MojoExecutionException
	 */
	private JSONObject reload(HttpClient httpClient, String security_token) throws MojoFailureException {
		getLog().info("Reloading plugin " + jarfile + "...");

		JSONObject res = null;

		PostMethod httpPost = new PostMethod(url + LOAD_URL);
		httpPost.addParameter(new NameValuePair("fileName", jarfile));
		httpPost.addParameter(new NameValuePair("desktop", DESKTOP));

		httpPost.addRequestHeader("security_token", security_token);

		String json = null;
		try {
			httpClient.executeMethod(httpPost);
			if (httpPost.getStatusCode() != 200) {
				getLog().error("Reload failed.");
				getLog().error(LOAD_URL + " returned " + httpPost.getStatusLine());
			} else {
				json = new BufferedReader(new InputStreamReader(httpPost.getResponseBodyAsStream())).readLine();
				// Unsecure the json if prefix is activated in servlet
				if (json.startsWith("{}&&")) {
					json = json.substring(4);
				}
				res = new JSONObject(json);

				if (!res.has("name") || !res.has("id") || !res.has("version") || !res.has("configClass")) {
					getLog().error("Reload failed.");
					getLog().error("Response does not have correct attributes: " + json);
					getLog().error("It should contain the following attributes: name, id, version, configClass");
					res = null;
				} else {
					getLog().info("OK");
					getLog().info("Plug-in " + res.getString("name") + " (id: " + res.getString("id") + ")"
							+ " successfully reloaded.");
				}

			}
		} catch (Exception e) {
			throw new MojoFailureException("Reload failed", e);
		} finally {
			httpPost.releaseConnection();
		}
		return res;
	}

	/**
	 * Save the configuration pre-created by the load plugin call.
	 * 
	 * @param httpClient
	 *            the {@link HttpClient} to use, it needs to have the
	 *            authentication cookies, brought by a call to the logon method.
	 * @param log
	 *            the {@link PrintStream} to use to print information
	 * @param loadResult
	 *            the resulting {@link JSONObject} from the save operation
	 *            containing plugin information
	 * @param security_token
	 *            the security token to use as header. This is returned by the
	 *            logon method.
	 * @return <code>true</code> if the save is successful
	 * @throws MojoExecutionException
	 */
	private void save(HttpClient httpClient, JSONObject loadResult, String security_token) throws MojoFailureException {
		getLog().info("Saving configuration...");
		PostMethod httpPost = new PostMethod(url + SAVE_URL);
		httpPost.addParameter(new NameValuePair("action", "update"));
		httpPost.addParameter(new NameValuePair("configuration", "PluginConfig"));
		httpPost.addParameter(new NameValuePair("desktop", DESKTOP));
		try {
			httpPost.addParameter(new NameValuePair("id", loadResult.getString("id")));
			JSONObject json_post = new JSONObject();
			getLog().info("Passed parameters: ");
			json_post.put("enabled", true);
			getLog().info("id: " + loadResult.getString("id"));
			json_post.put("id", loadResult.getString("id"));
			getLog().info("name: " + loadResult.getString("name"));
			json_post.put("name", loadResult.getString("name"));
			getLog().info("configClass: " +  loadResult.getString("configClass"));
			json_post.put("configClass", loadResult.getString("configClass"));
			getLog().info("version: " + loadResult.getString("version"));
			json_post.put("version", loadResult.getString("version"));
			json_post.put("filename", jarfile);
			getLog().info("filename: " + jarfile);
			json_post.put("dependencies", new JSONArray());
			getLog().info("dependencies: " + new JSONArray());
			httpPost.addParameter(new NameValuePair("json_post", json_post.toString()));
			httpPost.addRequestHeader("security_token", security_token);
			httpClient.executeMethod(httpPost);
			if (httpPost.getStatusCode() != 200) {
				getLog().error("Save failed.");
				getLog().error(SAVE_URL + " returned " + httpPost.getStatusLine());
			} else {
				String json = new BufferedReader(new InputStreamReader(httpPost.getResponseBodyAsStream())).readLine();
				// Unsecure the json if prefix is activated in servlet
				if (json.startsWith("{}&&")) {
					json = json.substring(4);
				}
				JSONObject jsonObj = new JSONObject(json);
				getLog().debug("JSON conversion OK");
				JSONArray messages = jsonObj.getJSONArray("messages");
				getLog().debug("Returned message is:");
				for (int i = 0; i < messages.length(); i++) {
					getLog().debug(messages.getJSONObject(i).getString("text"));
				}
				getLog().info("ICN Plugin reloaded successfully on " + url);
			}
		} catch (Exception e) {
			throw new MojoFailureException("Save failed", e);
		} finally {
			httpPost.releaseConnection();
		}
	}
}
