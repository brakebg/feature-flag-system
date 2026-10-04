package com.example.featureflags.flag;

import static com.example.featureflags.support.Ids.id;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.Entities;
import com.example.featureflags.support.StandaloneMvc;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 6.1: flag controller wiring and the toggle body rule, mocked service. */
class FlagControllerTest {

  FlagService service = mock(FlagService.class);
  MockMvc mvc = StandaloneMvc.of(new FlagController(service));
  Flag flag =
      new Flag(
          id(2),
          id(1),
          "k1",
          "orders.k1",
          null,
          true,
          Entities.T0,
          "admin",
          Entities.T0,
          "admin",
          1);

  @Test
  void createIs201WithLocation() throws Exception {
    when(service.create(eq(id(1)), any())).thenReturn(flag);
    mvc.perform(
            post("/api/v1/admin/groups/" + id(1) + "/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"k1\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/admin/flags/" + id(2)))
        .andExpect(jsonPath("$.fullKey").value("orders.k1"));
  }

  @Test
  void toggleNeedsABooleanBody() throws Exception {
    when(service.toggle(id(2), true)).thenReturn(flag);
    mvc.perform(
            post("/api/v1/admin/flags/" + id(2) + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    for (String body : new String[] {"", "{}", "{\"enabled\":null}"}) {
      mvc.perform(
              post("/api/v1/admin/flags/" + id(3) + "/toggle")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.type").value("https://featureflags.local/problems/malformed-request"));
    }
    verify(service).toggle(id(2), true);
  }

  @Test
  void patchAndDelete() throws Exception {
    when(service.update(eq(id(2)), any())).thenReturn(flag);
    mvc.perform(
            patch("/api/v1/admin/flags/" + id(2))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/v1/admin/flags/" + id(2))).andExpect(status().isNoContent());
    verify(service).delete(id(2));
    FlagService untouched = mock(FlagService.class);
    StandaloneMvc.of(new FlagController(untouched))
        .perform(
            patch("/api/v1/admin/flags/" + id(2))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(untouched);
  }
}
