package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanItemUrl {

	private String raw;

	private String protocol;

	private List<String> host;
	private List<String> path;

	private List<UrlQuery> query;

}
