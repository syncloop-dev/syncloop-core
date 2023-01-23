package com.eka.middleware.pub.util.rest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;

import com.eka.middleware.pub.util.auth.aws.AWS4SignerForChunkedUpload;
import org.apache.commons.lang3.StringUtils;

public class Client {
	static HostnameVerifier allHostsValid = null;
	private static final HttpClient.Builder clientBuilder = HttpClient.newBuilder();
	private static final HttpClient httpClient = clientBuilder.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10)).build();

	
	
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
            System.out.println(endpointUrl);
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
		
		if ("bytes".equals(contentType)) {
			respMap.put("bytes", body.getBytes());
		} else
			respMap.put("respPayload", body);
		
		respMap.put("statusCode",conn.getResponseCode()+"");
		respMap.put("message",conn.getResponseMessage());
		
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
		HttpRequest.Builder builder = HttpRequest.newBuilder();

		builder.uri(URI.create(url));

		HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();

		if (null != formData) {
			boolean containedBinary = formData.entrySet().stream().anyMatch(f -> f.getValue() instanceof byte[]);

			if (!containedBinary) {
				String form = formData.entrySet()
						.stream()
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
				bodyPublisher = HttpRequest.BodyPublishers.ofString(form);
			} else {
				bodyPublisher = Client.addFormData(formData, reqHeaders);
				builder.method(method, bodyPublisher);
			}
		}

		builder.method(method, bodyPublisher);

		reqHeaders.entrySet().stream().forEach(map -> {
			builder.header(map.getKey(), map.getValue());
		});

		HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
		if (!sslValidation) {
			httpClientBuilder.sslContext(unsecureCommunication());
		}

		HttpResponse<String> response = httpClientBuilder.build()
				.send(builder.build(), HttpResponse.BodyHandlers.ofString());

		Map<String, Object> responseMap = Maps.newHashMap();
		responseMap.put("statusCode", response.statusCode());
		responseMap.put("respPayload", response.body());
		responseMap.put("respHeaders", response.headers().map());

		return responseMap;
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
