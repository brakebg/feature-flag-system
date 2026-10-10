package com.example.featureflags.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.DatabaseCleaner;
import com.example.featureflags.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 5.4: in prod, Swagger UI and the OpenAPI document answer 404 not-found for everyone. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@Import({PostgresContainerConfig.class, DatabaseCleaner.class})
class SwaggerProdIT {

  @Autowired MockMvc mvc;

  @Test
  void docsAreNotFoundInProd() throws Exception {
    for (String path :
        new String[] {"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs"}) {
      mvc.perform(get(path))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("https://featureflags.local/problems/not-found"));
    }
  }
}
