package com.example.featureflags.flag;

import com.example.featureflags.common.MalformedRequestException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Spec 6.1: flag endpoints of the Admin API. */
@RestController
@RequestMapping("/api/v1/admin")
public class FlagController {

  private final FlagService flags;

  public FlagController(FlagService flags) {
    this.flags = flags;
  }

  @PostMapping("/groups/{groupId}/flags")
  public ResponseEntity<Flag> create(
      @PathVariable UUID groupId, @Valid @RequestBody CreateFlagRequest body) {
    Flag flag = flags.create(groupId, body);
    return ResponseEntity.created(URI.create("/api/v1/admin/flags/" + flag.id())).body(flag);
  }

  @PatchMapping("/flags/{flagId}")
  public Flag update(@PathVariable UUID flagId, @Valid @RequestBody UpdateFlagRequest body) {
    return flags.update(flagId, body);
  }

  /** The body {@code {"enabled": true|false}} is required; anything else is malformed (6.1). */
  @PostMapping("/flags/{flagId}/toggle")
  public Flag toggle(
      @PathVariable UUID flagId, @RequestBody(required = false) ToggleFlagRequest body) {
    if (body == null || body.enabled() == null) {
      throw new MalformedRequestException("Body must be {\"enabled\": true|false}");
    }
    return flags.toggle(flagId, body.enabled());
  }

  @DeleteMapping("/flags/{flagId}")
  public ResponseEntity<Void> delete(@PathVariable UUID flagId) {
    flags.delete(flagId);
    return ResponseEntity.noContent().build();
  }
}
