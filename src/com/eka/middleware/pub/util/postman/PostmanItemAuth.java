package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanItemAuth {

	private String type;
	private NoAuth noAuth;
	private List<ApiKey> apikey;
	private List<JwtBearer> jwt;
	private List<Basic> basic;
	private List<AwsSignature> awsv4;
	private List<Bearer> bearer;
}
