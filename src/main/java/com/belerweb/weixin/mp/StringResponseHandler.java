/**
 * Project:weixin-mp-sdk File:StringResponseHandler.java Copyright 2004-2013 Homolo Co., Ltd. All
 * rights reserved.
 */
package com.belerweb.weixin.mp;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.jsoup.helper.StringUtil;

/**
 * @author rory
 * @date Oct 25, 2013
 * @version $Id$
 */
public class StringResponseHandler implements ResponseHandler<String> {

  private String encoding = "UTF-8";

  private StatusLine statusLine;

  public StringResponseHandler() {}

  public StringResponseHandler(String encoding) {
    if (!StringUtil.isBlank(encoding)) {
      this.encoding = encoding;
    }
  }

  public StatusLine getStatusLine() {
    return statusLine;
  }

  /**
   * Returns the response body as a String if the response was successful (a
   * 2xx status code). If no response body exists, this returns null. If the
   * response was unsuccessful (>= 300 status code), throws an
   * {@link HttpResponseException}.
   */
  @Override
  public String handleResponse(final HttpResponse response) throws HttpResponseException,
      IOException {
    statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() >= 300) {
      throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
    }

    HttpEntity entity = response.getEntity();
    return entity == null ? null : EntityUtils.toString(entity, encoding);
  }


}
