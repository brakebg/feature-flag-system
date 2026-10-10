package com.example.featureflags.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the Admin API (spec 6.1) through MockMvc with a bearer token. */
public class AdminClient {

  public static final String BASE = "/api/v1/admin";
  public static final String PROBLEM = "https://featureflags.local/problems/";

  private final MockMvc mvc;
  private String token;

  public AdminClient(MockMvc mvc, String token) {
    this.mvc = mvc;
    this.token = token;
  }

  public AdminClient as(String newToken) {
    return new AdminClient(mvc, newToken);
  }

  private ResultActions send(MockHttpServletRequestBuilder req) throws Exception {
    if (token != null) {
      req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(req);
  }

  public ResultActions get(String path) throws Exception {
    return send(MockMvcRequestBuildersRaw.get(BASE + path));
  }

  public ResultActions postJson(String path, String body) throws Exception {
    return send(
        MockMvcRequestBuildersRaw.post(BASE + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  public ResultActions patchJson(String path, String body) throws Exception {
    return send(
        MockMvcRequestBuildersRaw.patch(BASE + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  public ResultActions delete(String path) throws Exception {
    return send(MockMvcRequestBuildersRaw.delete(BASE + path));
  }

  public JsonNode body(ResultActions r) throws Exception {
    return TestJson.tree(r.andReturn().getResponse().getContentAsString());
  }

  /** Creates a group and returns its JSON. */
  public JsonNode createGroup(String key, String name) throws Exception {
    return body(postJson("/groups", "{\"key\":\"" + key + "\",\"name\":\"" + name + "\"}"));
  }

  public JsonNode createFlag(String groupId, String key, boolean enabled) throws Exception {
    return body(
        postJson(
            "/groups/" + groupId + "/flags",
            "{\"key\":\"" + key + "\",\"enabled\":" + enabled + "}"));
  }

  /** Request builders that send the path as is (no URI template encoding). */
  static final class MockMvcRequestBuildersRaw {
    private MockMvcRequestBuildersRaw() {}

    static MockHttpServletRequestBuilder get(String p) {
      return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(URI.create(p));
    }

    static MockHttpServletRequestBuilder post(String p) {
      return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
          URI.create(p));
    }

    static MockHttpServletRequestBuilder patch(String p) {
      return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
          URI.create(p));
    }

    static MockHttpServletRequestBuilder delete(String p) {
      return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
          URI.create(p));
    }
  }
}
