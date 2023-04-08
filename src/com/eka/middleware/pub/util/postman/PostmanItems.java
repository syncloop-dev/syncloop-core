package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanItems {
	private String name;
	private List<PostmanItems> item;

	private PostmanItemRequest request;

	private List<PostmanItemResponse> response;

}
