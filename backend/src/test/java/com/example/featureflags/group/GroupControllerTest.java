package com.example.featureflags.group;

import static com.example.featureflags.support.Ids.id;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.Entities;
import com.example.featureflags.support.StandaloneMvc;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 6.1: group controller wiring (status codes, Location), mocked service. */
class GroupControllerTest {

  GroupService service = mock(GroupService.class);
  MockMvc mvc = StandaloneMvc.of(new GroupController(service));
  Group group =
      new Group(id(1), "orders", "Orders", null, Entities.T0, "admin", Entities.T0, "admin", 0);

  @Test
  void createIs201WithLocation() throws Exception {
    when(service.create(any())).thenReturn(group);
    mvc.perform(
            post("/api/v1/admin/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"orders\",\"name\":\"Orders\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/admin/groups/" + id(1)))
        .andExpect(jsonPath("$.key").value("orders"));
  }

  @Test
  void listPassesQueryAndSort() throws Exception {
    when(service.list("ord", "name"))
        .thenReturn(
            List.of(
                new GroupSummary(
                    id(1), "orders", "Orders", null, 2, 1, "admin", Entities.T0, "admin", 3)));
    mvc.perform(get("/api/v1/admin/groups?q=ord&sort=name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("orders"))
        .andExpect(jsonPath("$[0].flagCount").value(2));
    verify(service).list("ord", "name");
  }

  @Test
  void getPatchDelete() throws Exception {
    when(service.get(id(1)))
        .thenReturn(
            new GroupDetail(
                id(1),
                "orders",
                "Orders",
                null,
                Entities.T0,
                "admin",
                Entities.T0,
                "admin",
                0,
                List.of()));
    mvc.perform(get("/api/v1/admin/groups/" + id(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flags").isArray());
    when(service.update(eq(id(1)), any())).thenReturn(group);
    mvc.perform(
            patch("/api/v1/admin/groups/" + id(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value("orders"))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(delete("/api/v1/admin/groups/" + id(1))).andExpect(status().isNoContent());
    verify(service).delete(id(1));
  }
}
