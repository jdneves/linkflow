package br.com.linkflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record LinkRequest(

    @NotBlank(message = "URL de destino é obrigatória")
    @URL(message = "URL de destino inválida")
    @Size(max = 1000)
    String destinationUrl,

    @Size(max = 200, message = "Título muito longo")
    String title,

    // Slug personalizado — opcional. Ex: "airfryer-philips"
    // Gerado automaticamente se não informado
    @Pattern(regexp = "^[a-z0-9-]{3,80}$",
             message = "Slug deve ter 3-80 caracteres: apenas letras minúsculas, números e hifens")
    String customSlug,

    @Size(max = 100)
    String campaign,

    String productId,  // UUID do produto do Radar (opcional)
    String scriptId    // UUID do roteiro vinculado (opcional)
) {}
