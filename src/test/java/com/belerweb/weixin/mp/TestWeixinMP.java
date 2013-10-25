package com.belerweb.weixin.mp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestWeixinMP {

  String username;
  String password;

  @Before
  public void init() throws IOException {
    Properties props = new Properties();
    props.load(TestWeixinMP.class.getResourceAsStream("/mp.properties"));
    username = props.getProperty(WeixinMP.CONFIG_USERNAME);
    password = props.getProperty(WeixinMP.CONFIG_PASSWORD);
  }

  @Test
  public void testGetAccessToken() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    String appid = System.getProperty("appid");
    String secret = System.getProperty("secret");
    AccessToken accessToken = mp.getAccessToken(appid, secret);
    System.out.println("Token:" + accessToken.getToken());
    System.out.println("Valid:" + accessToken.isValid());
  }

  @Test
  public void testGetMessage() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    List<WeixinMessage> messages = mp.getMessage(0, 20);
    for (WeixinMessage message : messages) {
      System.out.println("ID:" + message.getId());
      System.out.println("Type:" + message.getType());
      System.out.println("Date:" + message.getDateTime());
      System.out.println("Content:" + message.getContent());
      System.out.println("=======================================");
    }
  }

  @Test
  public void testGetUser1() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    List<WeixinUser> users = mp.getUser(0, 0, 10);
    for (WeixinUser user : users) {
      System.out.println("FakeId:" + user.getFakeid());
      System.out.println("NickName:" + user.getNickname());
      System.out.println("ReMarkName:" + user.getReMarkName());
      System.out.println("Username:" + user.getUsername());
      System.out.println("Signature:" + user.getSignature());
      System.out.println("Country:" + user.getCountry());
      System.out.println("Province:" + user.getProvince());
      System.out.println("City:" + user.getCity());
      System.out.println("Sex:" + user.getSex());
      System.out.println("GroupId:" + user.getGroupId());
      System.out.println("=======================================");
    }
  }

  @Test
  public void testGetUser2() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    WeixinUser user = mp.getUser("877915902");
    System.out.println("FakeId:" + user.getFakeid());
    System.out.println("NickName:" + user.getNickname());
    System.out.println("ReMarkName:" + user.getReMarkName());
    System.out.println("Username:" + user.getUsername());
    System.out.println("Signature:" + user.getSignature());
    System.out.println("Country:" + user.getCountry());
    System.out.println("Province:" + user.getProvince());
    System.out.println("City:" + user.getCity());
    System.out.println("Sex:" + user.getSex());
    System.out.println("GroupId:" + user.getGroupId());
    System.out.println("=======================================");
  }

  @Test
  public void testPutIntoGroup() throws MpException {
    List<String> fakeIds = new ArrayList<String>();
    fakeIds.add("25029755");
    fakeIds.add("24771975");
    fakeIds.add("2125943182");
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.putIntoGroup(fakeIds, WeixinMP.GROUP_ASTERISK));
  }

  @Test
  public void testAddGroup() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.addGroup("测试组"));
  }

  @Test
  public void testRenameGroup() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.renameGroup(100, "测试组-修改"));
  }

  @Test
  public void testDeleteGroup() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.deleteGroup(100));
  }

  @Test
  public void testSendText() throws MpException {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.sendText("1429520560", "消息来自客户端/酷😭槑"));
  }

  @Test
  public void testSendImage() throws Exception {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.sendImage("1429520560", WeixinMP.IMAGE_JPG, TestWeixinMP.class
        .getResourceAsStream("/panda.png"), "panda.png"));
  }

  @Test
  public void testAddImageText() throws Exception {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.addImageText("Title", "Author", 0, "digest", "content", null));
  }

  @Test
  public void testSendImageText() throws Exception {
    WeixinMP mp = WeixinMP.init(username, password);
    Assert.assertTrue(mp.sendImageText("1429520560", 10000016));
  }
}
