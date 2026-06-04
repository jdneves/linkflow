package br.com.linkflow.dto.request;

import br.com.linkflow.entity.Script.Format;
import br.com.linkflow.entity.Script.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScriptRequest(

    // Produto — pode vir do Radar ou digitado manualmente
    String productId,       // opcional — UUID do produto do Radar

    @NotBlank(message = "Nome do produto é obrigatório")
    @Size(max = 500)
    String productName,

    @Size(max = 1000)
    String productHighlights, // destaques opcionais

    // Configurações do conteúdo
    @NotNull(message = "Plataforma é obrigatória")
    Platform platform,

    @NotNull(message = "Formato é obrigatório")
    Format format,

    @NotBlank(message = "Tom de voz é obrigatório")
    String tone,

    @NotBlank(message = "Duração é obrigatória")
    String duration
) {}
