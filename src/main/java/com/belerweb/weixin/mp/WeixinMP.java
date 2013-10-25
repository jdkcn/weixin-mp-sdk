package com.belerweb.weixin.mp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.helper.StringUtil;

/**
 * 微信公众平台
 */
public class WeixinMP {

  public static final String CONFIG_USERNAME = "weixin.mp.username";
  public static final String CONFIG_PASSWORD = "weixin.mp.password";

  public static final int GROUP_DEFAULT = 0;// 未分组
  public static final int GROUP_BLACKLIST = 1;// 黑名单
  public static final int GROUP_ASTERISK = 2;// 星标组

  public static final String IMAGE_JPG = "image/jpeg";
  public static final String IMAGE_PNG = "image/png";
  public static final String IMAGE_GIF = "image/gif";

  private static final Map<String, WeixinMP> MP = new HashMap<String, WeixinMP>();

  private HttpClient httpClient;
  private String username;
  private String password;
  private String token;
  private long tokenTime;

  /**
   * 私有构造函数，请通过init方法获取实例
   */
  private WeixinMP(String username, String password) {
    this.username = username;
    this.password = password;
    HttpParams httpParams = new BasicHttpParams();
    httpParams.setParameter("http.protocol.single-cookie-header", true);
    httpParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
    HttpConnectionParams.setConnectionTimeout(httpParams, 10000);
    httpClient = new DefaultHttpClient(httpParams);
  }

  /**
   * 获取凭证
   */
  public AccessToken getAccessToken(String appid, String secret) throws MpException {
    return getAccessToken("client_credential", appid, secret);
  }

  /**
   * 获取凭证
   */
  public AccessToken getAccessToken(String grantType, String appid, String secret)
      throws MpException {
    StringBuilder sb = new StringBuilder("https://api.weixin.qq.com/cgi-bin/token");
    sb.append("?grant_type=").append(grantType);
    sb.append("&appid=").append(appid);
    sb.append("&secret=").append(secret);
    HttpGet request = new HttpGet(sb.toString());
    return new AccessToken(toJsonObject(execute(request)));
  }

  /**
   * 微信公众平台初始化
   */
  public static WeixinMP init(String username, String password) throws MpException {
    if (!MP.containsKey(username)) {
      WeixinMP mp = new WeixinMP(username, password);
      mp.login();
      MP.put(username, mp);
    }
    return MP.get(username);
  }

  /**
   * 检查Token是否过期
   */
  private void checkToken() throws MpException {
    if (token == null || (System.currentTimeMillis() - tokenTime) > 600000) {
      login();
    }
  }

  /**
   * 登录
   */
  private void login() throws MpException {
    HttpPost request = new HttpPost("https://mp.weixin.qq.com/cgi-bin/login");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("X-Requested-With", "XMLHttpRequest"));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("f", "json"));
    nvps.add(new BasicNameValuePair("imgcode", ""));
    nvps.add(new BasicNameValuePair("username", username));
    nvps.add(new BasicNameValuePair("pwd", DigestUtils.md5Hex(password)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    request.setEntity(httpEntity);

    /**
     * 正确结果示例:
     * 
     * { "Ret": 302, "ErrMsg": "/cgi-bin/home?t=home/index&lang=zh_CN&token=1234567890",
     * "ShowVerifyCode": 0, "ErrCode": 0 }
     * 
     * 错误结果示例:
     * 
     * { "Ret": 400, "ErrMsg": "", "ShowVerifyCode": 0, "ErrCode": -3 } // 密码错误
     * 
     * { "Ret": 400, "ErrMsg": "", "ShowVerifyCode": 0, "ErrCode": -2 } // HTTP错误
     */
    try {
      JSONObject result = new JSONObject(execute(request));
      if (result.getInt("Ret") == 302) {
        Matcher matcher = Pattern.compile("token=(\\d+)").matcher(result.getString("ErrMsg"));
        if (matcher.find()) {
          token = matcher.group(1);
          tokenTime = new Date().getTime();
        }
      }
    } catch (JSONException e) {
      throw new MpException(e);
    }
  }

  /**
   * 实时消息：全部消息
   */
  public List<WeixinMessage> getMessage(int offset, int count) throws MpException {
    checkToken();
    StringBuffer sb = new StringBuffer("https://mp.weixin.qq.com/cgi-bin/message");
    sb.append("?token=").append(token);
    sb.append("&lang=").append("zh_CN");
    sb.append("&day=").append("7");
    sb.append("&offset=").append(String.valueOf(offset));
    sb.append("&count=").append(String.valueOf(count));
    HttpGet request = new HttpGet(sb.toString());
    List<WeixinMessage> messages = new ArrayList<WeixinMessage>();
    for (String line : execute(request).split("[\r\n]+")) {
      line = line.trim();
      if (line.startsWith("list : ({\"msg_item\":") && line.endsWith("}).msg_item")) {
        try {
          JSONArray array = new JSONArray(line.substring(20, line.length() - 11));
          for (int i = 0; i < array.length(); i++) {
            messages.add(new WeixinMessage(array.getJSONObject(i)));
          }
        } catch (JSONException e) {
          throw new MpException(e);
        }
        break;
      }
    }
    return messages;
  }

  /**
   * 用户列表
   */
  public List<WeixinUser> getUser(int groupId, int pageidx, int pagesize) throws MpException {
    checkToken();
    StringBuffer sb = new StringBuffer("https://mp.weixin.qq.com/cgi-bin/contactmanage");
    sb.append("?token=").append(token);
    sb.append("&lang=").append("zh_CN");
    sb.append("&type=").append("0");
    sb.append("&groupid=").append(String.valueOf(groupId));
    sb.append("&pageidx=").append(String.valueOf(pageidx));
    sb.append("&pagesize=").append(String.valueOf(pagesize));
    HttpGet request = new HttpGet(sb.toString());
    List<WeixinUser> users = new ArrayList<WeixinUser>();
    for (String line : execute(request).split("[\r\n]+")) {
      line = line.trim();
      if (line.startsWith("friendsList : ({\"contacts\":") && line.endsWith("}).contacts,")) {
        try {
          JSONArray array = new JSONArray(line.substring(27, line.length() - 12));
          for (int i = 0; i < array.length(); i++) {
            users.add(new WeixinUser(array.getJSONObject(i)));
          }
        } catch (JSONException e) {
          throw new MpException(e);
        }
        break;
      }
    }
    return users;
  }

  /**
   * 通过FakeId获取指定用户信息
   */
  public WeixinUser getUser(String fakeId) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/getcontactinfo";
    HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-getcontactinfo"));
    nvps.add(new BasicNameValuePair("fakeid", fakeId));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return new WeixinUser(toJsonObject(execute(request)));
  }

  /**
   * 将用户放入某个组内
   */
  public boolean putIntoGroup(List<String> fakeIds, int groupId) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/modifycontacts";
    HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-putinto-group"));
    nvps.add(new BasicNameValuePair("action", "modifycontacts"));
    nvps.add(new BasicNameValuePair("tofakeidlist", StringUtil.join(fakeIds, "|")));
    nvps.add(new BasicNameValuePair("contacttype", String.valueOf(groupId)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return "0".equals(toJsonObject(execute(request)).optString("ret"));
  }

  /**
   * 增加分组
   */
  public boolean addGroup(String name) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/modifygroup";
    HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-friend-group"));
    nvps.add(new BasicNameValuePair("func", "add"));
    nvps.add(new BasicNameValuePair("name", name));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optString("GroupId", null) != null;
  }

  /**
   * 修改组名
   */
  public boolean renameGroup(int groupId, String name) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/modifygroup";
    HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-friend-group"));
    nvps.add(new BasicNameValuePair("func", "rename"));
    nvps.add(new BasicNameValuePair("id", String.valueOf(groupId)));
    nvps.add(new BasicNameValuePair("name", name));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optString("GroupId", null) != null;
  }

  /**
   * 删除分组
   */
  public boolean deleteGroup(int groupId) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/modifygroup";
    HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-friend-group"));
    nvps.add(new BasicNameValuePair("func", "del"));
    nvps.add(new BasicNameValuePair("id", String.valueOf(groupId)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optString("GroupId", null) != null;
  }

  /**
   * 发送文字消息
   */
  public boolean sendText(String fakeId, String content) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/singlesend";
    HttpPost request = new HttpPost(url);
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/singlemsgpage");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("error", "false"));
    nvps.add(new BasicNameValuePair("imgcode", ""));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("type", "1"));// 文字
    nvps.add(new BasicNameValuePair("tofakeid", fakeId));
    nvps.add(new BasicNameValuePair("content", content));
    nvps.add(new BasicNameValuePair("pwd", DigestUtils.md5Hex(password)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optInt("ret", -1) == 0;
  }

  /**
   * 发送图片消息
   * @param fileName
   */
  public boolean sendImage(String fakeId, String type, InputStream inputStream, String fileName)
      throws MpException {
    checkToken();
    int fileid = uploadImage(type, inputStream, fileName);
    String url = "https://mp.weixin.qq.com/cgi-bin/singlesend";
    HttpPost request = new HttpPost(url);
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/singlemsgpage");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("error", "false"));
    nvps.add(new BasicNameValuePair("imgcode", ""));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("type", "2"));// 图片
    nvps.add(new BasicNameValuePair("tofakeid", fakeId));
    nvps.add(new BasicNameValuePair("fid", String.valueOf(fileid)));
    nvps.add(new BasicNameValuePair("fileid", String.valueOf(fileid)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    boolean result = toJsonObject(execute(request)).optInt("ret", -1) == 0;
    deleteFile(fileid);
    return result;
  }

  public boolean sendImageText(String fakeId, int appMsgId) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/singlesend";
    HttpPost request = new HttpPost(url);
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/singlemsgpage");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("error", "false"));
    nvps.add(new BasicNameValuePair("imgcode", ""));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("type", "10"));// 图文
    nvps.add(new BasicNameValuePair("tofakeid", fakeId));
    nvps.add(new BasicNameValuePair("fid", String.valueOf(appMsgId)));
    nvps.add(new BasicNameValuePair("appmsgid", String.valueOf(appMsgId)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optInt("ret", -1) == 0;
  }

  /**
   * 增加单图文信息
   */
  public boolean addImageText(String title, String author, int fileId, String digest,
      String content, String source) throws MpException {
    return editImageText(null, title, author, fileId, digest, content, source);
  }

  /**
   * 编辑图文信息
   */
  public boolean editImageText(Integer appMsgId, String title, String author, int fileId,
      String digest, String content, String source) throws MpException {
    checkToken();
    String url = "https://mp.weixin.qq.com/cgi-bin/operate_appmsg";
    HttpPost request = new HttpPost(url);
    //    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/operate_appmsg");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("error", "false"));
    nvps.add(new BasicNameValuePair("imgcode", ""));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("sub", "create"));
    nvps.add(new BasicNameValuePair("count", "1"));
    nvps.add(new BasicNameValuePair("AppMsgId", appMsgId == null ? "" : String.valueOf(appMsgId)));
    nvps.add(new BasicNameValuePair("title0", title));
    nvps.add(new BasicNameValuePair("author0", author == null ? "" : author));
    nvps.add(new BasicNameValuePair("fileid0", String.valueOf(fileId)));// 大图片建议尺寸：720像素 * 400像素封面
    nvps.add(new BasicNameValuePair("digest0", digest == null ? "" : digest));
    nvps.add(new BasicNameValuePair("content0", content));
    nvps.add(new BasicNameValuePair("sourceurl0", source == null ? "" : source));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optInt("ret", -1) == 0;
  }

  /**
   * 删除图文
   */
  public boolean deleteImageText(int appMsgId) throws MpException {
    String url = "https://mp.weixin.qq.com/cgi-bin/operate_appmsg";
    HttpPost request = new HttpPost(url);
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/singlemsgpage");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    //    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("sub", "del"));// 文字
    nvps.add(new BasicNameValuePair("AppMsgId", String.valueOf(appMsgId)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optInt("ret", -1) == 0;
  }

  /**
   * 上传图片
   * @param fileName
   */
  private int uploadImage(String type, InputStream inputStream, String fileName) throws MpException {
    checkToken();
    StringBuilder sb = new StringBuilder("https://mp.weixin.qq.com/cgi-bin/uploadmaterial");
    sb.append("?token=").append(token);
    sb.append("&lang=zh_CN");
    sb.append("&t=iframe-uploadfile");
    sb.append("&cgi=uploadmaterial");
    sb.append("&type=0");
    sb.append("&formId=null");// "file_from_" + System.currentTimeMillis()
    HttpPost request = new HttpPost(sb.toString());
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/indexpage");
    try {
      MultipartEntity httpEntity = new MultipartEntity();
      httpEntity.addPart("uploadfile", new ByteArrayBody(IOUtils.toByteArray(inputStream), type,
          fileName));
      request.setEntity(httpEntity);
      String html = execute(request, false);
      Matcher matcher = Pattern.compile("'(\\d+)'").matcher(html);
      if (html.contains("上传成功") && matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
    } catch (Exception e) {
      throw new MpException(e);
    }

    throw new MpException("上传失败");
  }

  /**
   * 删除素材文件
   */
  private boolean deleteFile(int fileid) throws MpException {
    String url = "https://mp.weixin.qq.com/cgi-bin/modifyfile";
    HttpPost request = new HttpPost(url);
    request.addHeader("Referer", "https://mp.weixin.qq.com/cgi-bin/singlemsgpage");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("token", token));
    nvps.add(new BasicNameValuePair("lang", "zh_CN"));
    nvps.add(new BasicNameValuePair("t", "ajax-response"));
    nvps.add(new BasicNameValuePair("ajax", "1"));
    nvps.add(new BasicNameValuePair("oper", "del"));
    nvps.add(new BasicNameValuePair("fileid", String.valueOf(fileid)));
    HttpEntity httpEntity;
    try {
      httpEntity = new UrlEncodedFormEntity(nvps, "UTF-8");
      request.setEntity(httpEntity);
    } catch (UnsupportedEncodingException e) {
      throw new MpException(e);
    }
    return toJsonObject(execute(request)).optInt("ret", -1) == 0;
  }

  private String execute(HttpRequestBase request) throws MpException {
    return execute(request, true);
  }

  private String execute(HttpRequestBase request, boolean form) throws MpException {
    request.addHeader("Pragma", "no-cache");
    request.addHeader("User-Agent",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:23.0) Gecko/20100101 Firefox/23.0");
    if (form && request instanceof HttpPost) {
      request.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    }
    if (request.getHeaders("Referer") == null || request.getHeaders("Referer").length == 0) {
      request.addHeader("Referer", "https://mp.weixin.qq.com/");
    }

    try {
      HttpResponse response = httpClient.execute(request);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new MpException(MpException.DEFAULT_CODE, String.valueOf(response.getStatusLine()
            .getStatusCode()));
      }
      return new StringResponseHandler("UTF-8").handleResponse(response);
    } catch (IOException e) {
      throw new MpException(e);
    }
  }

  private JSONObject toJsonObject(String json) throws MpException {
    try {
      return new JSONObject(json);
    } catch (JSONException e) {
      throw new MpException(e);
    }
  }

}
