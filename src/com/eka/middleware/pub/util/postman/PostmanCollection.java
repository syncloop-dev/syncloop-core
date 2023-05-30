package com.eka.middleware.pub.util.postman;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanCollection {

	private Map<String, String> info;
	private List<PostmanItems> item;

	private static PostmanItemAuth auth;
	public static PostmanItemAuth getAuth() {
		return auth;
	}

	public static void setAuth(PostmanItemAuth auth) {
		PostmanCollection.auth = auth;
	}

}
