package io.split.dbm.sfdc2split;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

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

		Response loginResponse = client.newCall(request).execute();
		System.out.println("success getting token?\t" + loginResponse.isSuccessful());
		
		JSONObject loginObject = new JSONObject(loginResponse.body().string());
		System.out.println(loginObject.toString(2));
		
		String token = loginObject.getString("access_token");
		
		String queryUrl = "https://splitsoftware-dev-ed.my.salesforce.com/services/data/v50.0/query/?q=SELECT+name,AnnualRevenue+from+Account+WHERE+name='Express Logistics and Transport'";
		Request accountRequest = new Request.Builder()
				.url(queryUrl)
				.header("Authorization", "Bearer " + token)
				.build();
		
		Response queryResponse = client.newCall(accountRequest).execute();
		System.out.println("success getting accounts by revenue?\t" + queryResponse.isSuccessful());
		JSONObject accountsObject = new JSONObject(queryResponse.body().string());
		System.out.println(accountsObject.toString(2));
		
		
	}

	public static String readFile(String path)
			throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, Charset.defaultCharset());
	}

}