package io.split.dbm.sfdc2split;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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

		String queryUrl = "https://splitsoftware-dev-ed.my.salesforce.com/services/data/v50.0/query/?q=SELECT+name,AnnualRevenue+from+Account+WHERE+name='" + account_id + "'";
		Request accountRequest = new Request.Builder()
				.url(queryUrl)
				.header("Authorization", "Bearer " + token)
				.build();

		System.out.println("GET " + queryUrl);
		Response queryResponse = client.newCall(accountRequest).execute();
		System.out.println("success getting accounts by revenue?\t" + queryResponse.isSuccessful());
		JSONObject accountsObject = new JSONObject(queryResponse.body().string());
		System.out.println(accountsObject.toString(2));

		BigDecimal annual_revenue = accountsObject.getJSONArray("records").getJSONObject(0).getBigDecimal("AnnualRevenue");
		System.out.println("Annual Revenue " + annual_revenue);

		SplitClientConfig config = SplitClientConfig.builder()
				.setBlockUntilReadyTimeout(5000)
				.build();

		try {
			SplitClient split = SplitFactoryBuilder.build("1s46c6r6tfl9m7usijqsmicfckm04mn8b321", config).client();
			split.blockUntilReady();

			Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put("annual_revenue_millions", annual_revenue.intValue() / 1000 / 1000);
			System.out.println("attributes.get(annual_revenue_millions): " + attributes.get("annual_revenue_millions"));
			String treatment = split.getTreatment(account_id, "sfdc_feature", attributes);
			System.out.println("treatment: " + treatment);

			split.destroy();
		} catch (TimeoutException | InterruptedException | URISyntaxException | IOException e) {
			throw e;
		}
		
	}

	public static String readFile(String path)
			throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, Charset.defaultCharset());
	}

}