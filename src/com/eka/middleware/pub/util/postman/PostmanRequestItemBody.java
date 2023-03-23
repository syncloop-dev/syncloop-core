package com.eka.middleware.pub.util.postman;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostmanRequestItemBody {

	private String mode;
	private String raw;
	private Options options;
	private FormData formdate;
	private Binary binary;
	private List<UrlEncodedParameter> urlEncoded;
}
