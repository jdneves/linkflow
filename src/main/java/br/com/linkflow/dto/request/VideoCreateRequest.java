package br.com.linkflow.dto.request;

import br.com.linkflow.entity.VideoMode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VideoCreateRequest(
    @NotNull(message = "scriptId é obrigatório")
    UUID scriptId,

    @NotNull(message = "mode é obrigatório")
    VideoMode mode,

    String avatarId,
    String voiceId
) {}
