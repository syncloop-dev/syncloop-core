package com.eka.middleware.pub.util.rest;

import com.eka.middleware.pub.util.auth.aws.AWS4SignerForChunkedUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.util.EntityUtils;

public class Client {
	static HostnameVerifier allHostsValid = null;
	private static final HttpClient.Builder clientBuilder = HttpClient.newBuilder();
	private static final HttpClient httpClient = clientBuilder.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10)).build();


	@Deprecated(since = "release/1.4.2")
	public static Map<String, Object> invokeREST(int itr, String url, String method, Map<String, String> headers,
			Map<String, Object> formData, String payload, File binary, String basicAuthUser, String basicAuthPass,
			Map<String, Object> respHandling, byte[] payloadBytes, InputStream payloadIS, AWS4SignerForChunkedUpload signer) throws Exception {

		String contentType = null;
		Boolean enableStreaming = false;
		if (respHandling != null) {
			contentType = (String) respHandling.get("contentType");
			if(respHandling.get("enableStreaming")!=null)
			enableStreaming = (boolean) respHandling.get("enableStreaming");
		}
		
		URL endpointUrl;
        try {
            endpointUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to parse service endpoint: " + e.getMessage());
        }
        
        HttpURLConnection conn = HttpUtils.createHttpConnection(endpointUrl, method, headers);
        
        if (formData != null) {
        	payloadBytes=getFormData(formData, headers);
        }else if(payload!=null)
        	payloadBytes=payload.getBytes();
        
        if(payloadBytes!=null || payloadIS!=null) {
        	OutputStream os= conn.getOutputStream();
        	
        	if(payloadIS!=null) {
        		BufferedReader rd = new BufferedReader(new InputStreamReader(payloadIS));
                String line;
                while ((line = rd.readLine()) != null) {
                	os.write(line.getBytes());
                }
                rd.close();
                payloadIS.close();
                os.flush();
        	}else {
        		os.write(payloadBytes);
        		os.flush();
        	}
        		
        }
        
        Map<String, Object> respMap = new HashMap<String, Object>();
		
        String body="";
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (IOException e) {
            is = conn.getErrorStream();
        }
        
        if (enableStreaming)
			respMap.put("inputStream", is);
        else {
	        
	        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
	        String line;
	        StringBuffer response = new StringBuffer();
	        while ((line = rd.readLine()) != null) {
	            response.append(line);
	            response.append('\r');
	        }
	        rd.close();
	        body= response.toString();
        }

		Map<String, List<String>> responseHeaderFields = conn.getHeaderFields();

		Map<String, String> responseHeaders = new HashMap<String, String>();

		for (Map.Entry<String, List<String>> responseHeaderField : responseHeaderFields.entrySet()) {
			if (null != responseHeaderField.getKey()) {
				responseHeaders.put(responseHeaderField.getKey(), StringUtils.join(responseHeaderField.getValue(), ","));
				responseHeaders.put(responseHeaderField.getKey().toLowerCase(), StringUtils.join(responseHeaderField.getValue(), ","));
			}

		}
		
		if ("bytes".equals(contentType)) {
			respMap.put("bytes", body.getBytes());
		} else
			respMap.put("respPayload", body + "");
		
		respMap.put("statusCode",conn.getResponseCode()+"");
		respMap.put("message",conn.getResponseMessage());
		respMap.put("responseHeaders", responseHeaders);

		conn.disconnect();
		
		return respMap;
	}

	/**
	 * @param url
	 * @param method
	 * @param formData
	 * @param reqHeaders
	 * @param sslValidation
	 * @return
	 * @throws Exception
	 */
	public static Map<String, Object> invoke(String url, String method, Map<String, Object> formData,
											 Map<String, String> reqHeaders, boolean sslValidation) throws Exception {
		return invoke(url, method, formData, reqHeaders, null, null, new HashMap<>(), sslValidation);
	}

	public static Map<String, Object> invoke(String format, String method, Map<String, Object> formData, Map<String, String> reqHeaders, String reqPayload, boolean b) throws Exception {
		return invoke(format, method, formData, reqHeaders, reqPayload, null, new HashMap<>(), b);
	}

	/**
	 * @param url
	 * @param method
	 * @param formData
	 * @param reqHeaders
	 * @param payload
	 * @param inputStream
	 * @param queryParameters
	 * @param sslValidation
	 * @return
	 * @throws Exception
	 */
	public static Map<String, Object> invoke(String url, String method, Map<String, Object> formData,
											 Map<String, String> reqHeaders, String payload, InputStream inputStream, Map<String, String> queryParameters, boolean sslValidation) throws Exception {
		return invoke(url, method, formData, reqHeaders, payload, inputStream, queryParameters, new HashMap<String, Object>(), sslValidation);
	}


	public static Map<String, Object> invoke(String url, String method, Map<String, Object> formData,
											  Map<String, String> reqHeaders, String payload, InputStream inputStream,
											  Map<String, String> queryParameters, Map<String, Object> settings, boolean sslValidation) throws Exception {
		AtomicBoolean sendBlankParams = new AtomicBoolean(false);

		if (null != settings.get("sendBlankParams")) {
			sendBlankParams.set((Boolean) settings.get("sendBlankParams"));
		}

		String queries = StringUtils.join(queryParameters.entrySet().stream()
				.filter(f -> !(sendBlankParams.get() && StringUtils.isBlank(f.getValue())))
				.map(m -> {
					try {
						return String.format("%s=%s", m.getKey(), URLEncoder.encode(m.getValue(), StandardCharsets.UTF_8.toString()));
					} catch (Exception e) {
						e.printStackTrace();
					}
					return m.getValue();
				}).collect(Collectors.toSet()), "&");

		try (CloseableHttpClient httpClient = (sslValidation) ? HttpClients.createDefault() : HttpClients.custom().setSSLContext(unsecureCommunication()).build()) {

			HttpRequestBase request;
			switch (method.toUpperCase()) {
				case "GET":
					request = new HttpGet();
					break;
				case "POST":
					request = new HttpPost();
					break;
				case "PUT":
					request = new HttpPut();
					break;
				case "DELETE":
					request = new HttpDelete();
					break;
				case "PATCH":
					request = new HttpPatch();
					break;
				case "OPTIONS":
					request = new HttpOptions();
					break;
				default:
					throw new IllegalArgumentException("Unsupported HTTP method: " + method);
			}

			URI requestUri = StringUtils.isNotBlank(queries) ? URI.create(url + "?" + queries) : URI.create(url);
			request.setURI(requestUri);

			HttpEntity bodyEntity = null;

			if (null != formData && !formData.isEmpty()) {
				boolean containedBinary = formData.entrySet().stream().anyMatch(f -> f.getValue() instanceof byte[]
						|| f.getValue() instanceof InputStream || f.getValue() instanceof File);

				if (!containedBinary) {
					String form = formData.entrySet().stream()
							.filter(entry -> {
								if (sendBlankParams.get() && StringUtils.isBlank(String.valueOf(entry.getValue()))) {
									return false;
								}
								return true;
							})
							.flatMap(e -> {
								List<String> list = new ArrayList<>();
								if (e.getValue() instanceof Collection) {
									ArrayList<?> arrayList = (ArrayList<?>) e.getValue();
									list.addAll(arrayList.stream().map(m -> e.getKey() + "=" + URLEncoder.encode(m.toString(), StandardCharsets.UTF_8)).
											map(m -> m.toString()).collect(Collectors.toList()));
								} else {
									list.add(e.getKey() + "=" + URLEncoder.encode((String) e.getValue(), StandardCharsets.UTF_8));
								}
								return list.stream();
							})
							.collect(Collectors.joining("&"));

					reqHeaders.put("Content-Type", "application/x-www-form-urlencoded");
					bodyEntity = new ByteArrayEntity(form.getBytes(StandardCharsets.UTF_8));
				} else {
					bodyEntity = addFormDataV2(formData, reqHeaders);
				}
			} else if (StringUtils.isNotBlank(payload)) {
				bodyEntity = new ByteArrayEntity(payload.getBytes(StandardCharsets.UTF_8));
				if (StringUtils.isBlank(reqHeaders.get("Content-Type"))) {
					reqHeaders.put("Content-Type", "application/json");
				}
			} else if (null != inputStream) {
				byte[] inputBytes = IOUtils.toByteArray(inputStream);
				bodyEntity = new ByteArrayEntity(inputBytes);
				((HttpEntityEnclosingRequestBase) request).setEntity(bodyEntity);
			}

			if (bodyEntity != null && request instanceof HttpEntityEnclosingRequestBase) {
				((HttpEntityEnclosingRequestBase) request).setEntity(bodyEntity);
			}

			for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
				request.setHeader(entry.getKey(), entry.getValue());
			}

			long timeout = 30L;
			if (null != settings.get("requestTimeout")) {
				timeout = Long.parseLong(settings.get("requestTimeout").toString());
			}

			HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout));
			if (!sslValidation) {
				httpClientBuilder.sslContext(unsecureCommunication());
			}

			/*CloseableHttpResponse response;
			if (null != settings.get("responseAsInputStream") && (Boolean) settings.get("responseAsInputStream")) {
				response = httpClient.execute(request, HttpClientContext.create());
			} else {
				response = httpClient.execute(request, HttpClientContext.create());
			}*/

			CloseableHttpResponse response = httpClient.execute(request, HttpClientContext.create());

			int redirectCount = settings.containsKey("redirectCount") ? (int) settings.get("redirectCount") : 0;
			if (response.getStatusLine().getStatusCode() == 301 || response.getStatusLine().getStatusCode() == 302) {
				if (redirectCount >= 5) {
					throw new RuntimeException("Too many redirects");
				}
				String redirectUrl = response.getHeaders("Location")[0].getValue();
				if (StringUtils.isNotBlank(redirectUrl)) {
					settings.put("redirectCount", redirectCount + 1);
					return invoke(redirectUrl, method, formData, reqHeaders, payload, inputStream, queryParameters, settings, sslValidation);
				}
			}

			Map<String, Object> responseMap = new HashMap<>();
			responseMap.put("statusCode", response.getStatusLine().getStatusCode());

			if (null != settings.get("responseAsInputStream") && (Boolean) settings.get("responseAsInputStream")) {
				InputStream responseInputStream = response.getEntity().getContent();
				ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
				IOUtils.copy(responseInputStream, responseBytes);

				responseMap.put("respPayload", responseBytes.toByteArray());
				responseMap.put("inputStream", new ByteArrayInputStream(responseBytes.toByteArray()));
			} else {
				responseMap.put("respPayload", EntityUtils.toString(response.getEntity()));
			}

			Header[] headers = response.getAllHeaders();
			Map<String, String> responseHeaders = new HashMap<>();

			for (Header header : headers) {
				if (null != header.getName()) {
					responseHeaders.put(header.getName(), header.getValue());
					responseHeaders.put(header.getName().toLowerCase(), header.getValue());
				}
			}
			responseMap.put("statusCode", response.getStatusLine().getStatusCode());
			responseMap.put("respHeaders", responseHeaders);
			return responseMap;
		}
	}

	private static SSLContext unsecureCommunication() {
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
		};

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			return sc;
		} catch (Exception e) {
		}
		return null;
	}

	private static void addBasicAuth(HttpClient.Builder clientBuilder, String username, String password) {
		clientBuilder.authenticator(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password.toCharArray());
			}
		});
	}

	private static HttpEntity addFormDataV2(Map<String, Object> data, Map<String, String> headers) throws Exception {

		String boundary = "------syncloop" + new BigInteger(35, new java.util.Random()).toString();

		headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Map.Entry<String, Object> entry : data.entrySet()) {

			baos.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
			baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

			if (entry.getValue() instanceof File) {
				File file = (File) entry.getValue();
				var path = Path.of(file.toURI());
				String fileName = file.getName();
				String mimeType = Files.probeContentType(path);

				baos.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"; filename=\"" + fileName + "\"").getBytes(StandardCharsets.UTF_8));
				baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
				baos.write(("Content-Type: " + mimeType).getBytes(StandardCharsets.UTF_8));
				baos.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
				baos.write(Files.readAllBytes(path));
			} else {

				if (entry.getValue() instanceof byte[]) {
					String fileName = entry.getKey();
					String mimeType = "application/octet-stream";

					baos.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"; filename=\"" + fileName + "\"").getBytes(StandardCharsets.UTF_8));
					baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
					baos.write(("Content-Type: " + mimeType).getBytes(StandardCharsets.UTF_8));
					baos.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
					baos.write((byte[]) entry.getValue());
				} else if (entry.getValue() instanceof InputStream) {
					String fileName = entry.getKey();
					String mimeType = "application/octet-stream";

					baos.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"; filename=\"" + fileName + "\"").getBytes(StandardCharsets.UTF_8));
					baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
					baos.write(("Content-Type: " + mimeType).getBytes(StandardCharsets.UTF_8));
					baos.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
					baos.write(IOUtils.toByteArray((InputStream) entry.getValue()));
				} else {
					baos.write(("Content-Disposition: form-data; name=\"" + entry.getKey() +"\"").getBytes(StandardCharsets.UTF_8));
					baos.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
					baos.write(entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
				}
			}
			baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
		}

		baos.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
		baos.close();

		//return HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray());
		return new ByteArrayEntity(baos.toByteArray());
	}

	private static HttpRequest.BodyPublisher addFormData(Map<String, Object> data, Map<String, String> headers)
			throws Exception {
		var builder = new StringBuilder();
		String boundary = new BigInteger(35, new java.util.Random()).toString();
		headers.put("Content-Type", "multipart/form-data;boundary=" + boundary);
		byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=")
				.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			baos.write(separator);
			if (builder.length() > 0)
				baos.write("&".getBytes(StandardCharsets.UTF_8));
			if (entry.getValue() instanceof File) {
				File file = (File) entry.getValue();
				var path = Path.of(file.toURI());
				String fileName = file.getName();
				String mimeType = Files.probeContentType(path);
				baos.write(("\"" + entry.getKey() + "\"; filename=\"" + fileName + "\"\r\nContent-Type: " + mimeType
						+ "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				baos.write(Files.readAllBytes(path));
				baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
			} else {
				baos.write(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
						.getBytes(StandardCharsets.UTF_8));
//	      baos.write(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
//	      baos.write("=".getBytes(StandardCharsets.UTF_8));
//	      baos.write(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
			}
		}
		baos.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
		HttpRequest.BodyPublisher bp = HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray());
		baos.close();
		return bp;
	}
	
	private static byte[] getFormData(Map<String, Object> data, Map<String, String> headers)
			throws Exception {
		var builder = new StringBuilder();
		String boundary = new BigInteger(35, new java.util.Random()).toString();
		headers.put("Content-Type", "multipart/form-data;boundary=" + boundary);
		byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=")
				.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			baos.write(separator);
			if (builder.length() > 0)
				baos.write("&".getBytes(StandardCharsets.UTF_8));
			if (entry.getValue() instanceof File) {
				File file = (File) entry.getValue();
				var path = Path.of(file.toURI());
				String fileName = file.getName();
				String mimeType = Files.probeContentType(path);
				baos.write(("\"" + entry.getKey() + "\"; filename=\"" + fileName + "\"\r\nContent-Type: " + mimeType
						+ "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				baos.write(Files.readAllBytes(path));
				baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
			} else {
				baos.write(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
						.getBytes(StandardCharsets.UTF_8));
//	      baos.write(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
//	      baos.write("=".getBytes(StandardCharsets.UTF_8));
//	      baos.write(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
			}
		}
		baos.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
		//HttpRequest.BodyPublisher bp = HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray());
		byte[] bytes=baos.toByteArray();
		baos.close();
		return bytes;
	}


}
