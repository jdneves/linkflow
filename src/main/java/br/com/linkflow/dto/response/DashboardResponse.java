package br.com.linkflow.dto.response;

import java.util.List;
import java.util.Map;

public record DashboardResponse(

    // Totais gerais
    Totais totais,

    // Gráfico de cliques por dia (últimos 30 dias)
    List<CliqueDia> cliquesPorDia,

    // Distribuição por device
    Map<String, Long> cliquesPorDevice,

    // Top 5 links mais clicados
    List<TopLink> topLinks,

    // Insight semanal gerado por IA
    String insightSemanal,

    // Uso do plano atual
    UsoPlano usoPlano
) {
    public record Totais(
        long totalCliques,
        long totalLinks,
        long totalRoteiros,
        long totalVideos,
        long cliquesUltimos30Dias,
        long cliquesUltimos7Dias
    ) {}

    public record CliqueDia(String data, long total) {}

    public record TopLink(
        String id,
        String slug,
        String title,
        String shortUrl,
        long clicks,
        String platform
    ) {}

    public record UsoPlano(
        String plano,
        long rotelirosMes,      int limiteRoteiros,
        long videosMes,         int limiteVideos,
        long linksAtivos,       int limiteLinks
    ) {}
}
