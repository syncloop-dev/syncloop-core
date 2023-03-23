package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanItemRequest {

	private PostmanItemAuth auth;

	private String method;

	private List<PostmanRequestHeaders> header;

	private PostmanRequestItemBody body;

	private PostmanItemUrl url;
}
