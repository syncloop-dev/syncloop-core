package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanItemResponse {

	private String name;
	private OriginalRequest originalRequest;
	private String status;
	private int code;
	private List<PostmanRequestHeaders> header;
	private String body;
}
