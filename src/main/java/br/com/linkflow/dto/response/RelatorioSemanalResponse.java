package br.com.linkflow.dto.response;

import java.util.List;

public record RelatorioSemanalResponse(
    String nomeUsuario,
    long totalCliques,
    long variacaoCliques,       // diferença vs semana anterior
    String melhorLink,
    String melhorLinkCliques,
    List<String> topProdutos,
    String insight
) {}
