package com.eka.middleware.pub.util.postman;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UrlQuery {

	private String key;

	private String value;

	private boolean disabled;

	private String description;
}
