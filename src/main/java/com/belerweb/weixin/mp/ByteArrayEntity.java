/**
 * Project:weixin-mp-sdk File:ByteArrayEntity.java Copyright 2004-2013 Homolo Co., Ltd. All rights
 * reserved.
 */
package com.belerweb.weixin.mp;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author rory
 * @date Oct 25, 2013
 * @version $Id$
 */
public class ByteArrayEntity extends org.apache.http.entity.ByteArrayEntity {

  private String name;

  /**
   * @param b
   */
  public ByteArrayEntity(byte[] b) {
    super(b);
  }

  public ByteArrayEntity(byte[] b, String name) {
    super(b);
    this.name = name;
  }

  public ByteArrayEntity(byte[] b, String name, String type) {
    super(b);
    this.name = name;
    setContentType(type);
    setContentEncoding("UTF-8");
  }

}
