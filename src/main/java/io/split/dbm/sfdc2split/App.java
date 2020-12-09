package io.split.dbm.sfdc2split;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import io.split.client.SplitClient;
import io.split.client.SplitClientConfig;
import io.split.client.SplitFactoryBuilder;

/**
 * Hello world!
 *
 */
public class App 
{
	public static void main( String[] args ) throws Exception
	{
		new App().execute();
	}

	public static final String account_id = "Express Logistics and Transport";
	
	private void execute() throws Exception {
		OkHttpClient client = new OkHttpClient();

		String url = "https://login.salesforce.com/services/oauth2/token?"
				+ "grant_type=password"
				+ "&client_id=" + readFile("client_id.txt")
				+ "&client_secret=" + readFile("client_secret.txt")
				+ "&username=david.martin@gmail.com"
				+ "&password=" + readFile("password.txt");

		String json = "{}";

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(url)
				.post(body)
				.build();

		System.out.println("POST " + url);
		Response loginResponse = client.newCall(request).execute();
		System.out.println("success getting token?\t" + loginResponse.isSuccessful());

		JSONObject loginObject = new JSONObject(loginResponse.body().string());
		
		String token = loginObject.getString("access_token");
		
		String descUrl = "https://splitsoftware-dev-ed.my.salesforce.com/services/data/v50.0/sobjects/Account/describe/";
		Request descRequest = new Request.Builder()
				.url(descUrl)
				.header("Authorization", "Bearer " + token)
				.build();
		
		System.out.println("GET " + descUrl);
		Response descResponse = client.newCall(descRequest).execute();
		System.out.println("success getting description of Account?\t" + descResponse.isSuccessful());
		String desc = descResponse.body().string();
		JSONObject descObject = new JSONObject(desc);
		JSONArray fieldsArray = descObject.getJSONArray("fields");
		Set<String> names = new TreeSet<String>();
		for(int i = 0; i < fieldsArray.length(); i++) {
			JSONObject o = fieldsArray.getJSONObject(i);
			names.add(o.getString("name"));
		}
		String oNames = "";
		for(String name : names) {
			//System.out.println(name);
			oNames += name + ","; 
		}
		oNames = oNames.substring(0, oNames.lastIndexOf(","));
		
		String queryUrl = "https://splitsoftware-dev-ed.my.salesforce.com/services/data/v50.0/query/?q=SELECT+" + oNames + "+from+Account+WHERE+name='" + account_id + "'";
		Request accountRequest = new Request.Builder()
				.url(queryUrl)
				.header("Authorization", "Bearer " + token)
				.build();

		System.out.println("GET " + queryUrl);
		Response queryResponse = client.newCall(accountRequest).execute();
		System.out.println("success getting accounts by revenue?\t" + queryResponse.isSuccessful());
		JSONObject accountsObject = new JSONObject(queryResponse.body().string());
		//System.out.println(accountsObject.toString(2));

		JSONObject recordsObject = accountsObject.getJSONArray("records").getJSONObject(0);

		Map<String, Object> attributes = new TreeMap<String, Object>();
		flattenIntoAttributes(attributes, recordsObject, "");
		
		for(Entry<String, Object> entry : attributes.entrySet()) {
			System.out.println("ATTRIBUTES: " + entry.getKey() + " : " + entry.getValue());
		}
		
		SplitClientConfig config = SplitClientConfig.builder()
				.setBlockUntilReadyTimeout(5000)
				.build();

		try {
			SplitClient split = SplitFactoryBuilder.build("1s46c6r6tfl9m7usijqsmicfckm04mn8b321", config).client();
			split.blockUntilReady();
			double annual_revenue = ((Double)attributes.get("AnnualRevenue")) / 1000 / 1000;		
			attributes.put("annual_revenue_millions", new Double(annual_revenue).intValue());		
			System.out.println("attributes.get(annual_revenue_millions): " + attributes.get("annual_revenue_millions"));
			String treatment = split.getTreatment(account_id, "sfdc_feature", attributes);
			System.out.println("treatment: " + treatment);

			split.destroy();
		} catch (TimeoutException | InterruptedException | URISyntaxException | IOException e) {
			throw e;
		}
		
	}

	public Map<String, Object> flattenIntoAttributes(Map<String, Object> result, JSONObject o, String prefix) {
		
		for (String record : o.keySet()) {
			Object r = o.get(record);
			if(r instanceof Double) {
				result.put(prefix + record, o.getDouble(record));
			} else if (r instanceof Integer) {
				result.put(prefix + record, o.getInt(record));
			} else if (r instanceof Float) {
				result.put(prefix + record, o.getFloat(record));
			} else if (r instanceof Long) {
				result.put(prefix + record, o.getLong(record));
			} else if (r instanceof String) {
				result.put(prefix + record, o.getString(record));
			} else if (r instanceof Boolean) {
				result.put(prefix + record, o.getBoolean(record));
			} else if (r instanceof JSONObject) {
				JSONObject jo = o.getJSONObject(record);
				flattenIntoAttributes(result, jo, prefix + record + ".");
			} else {
				// not handling arrays, big decimals or nulls
				if(o.isNull(record)) {
					System.out.println("skipping null attribute '" + record + "'");
				} else {
					System.out.println("not handling attribute " + record + " - " + r.getClass().getName());
				}
			}
		}
		
		return result;
	}
	
	public static String readFile(String path)
			throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, Charset.defaultCharset());
	}

}