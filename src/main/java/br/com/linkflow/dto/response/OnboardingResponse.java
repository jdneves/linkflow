package br.com.linkflow.dto.response;

import br.com.linkflow.entity.OnboardingProgress;

import java.util.List;

public record OnboardingResponse(
    int progresso,
    boolean completed,
    List<Passo> passos,
    String proximoPasso,
    String mensagemMotivacional
) {
    public record Passo(
        String id,
        String titulo,
        String descricao,
        String acao,       // rota do frontend para executar
        boolean concluido,
        int ordem
    ) {}

    public static OnboardingResponse from(OnboardingProgress progress) {
        var steps = progress.getSteps();

        List<Passo> passos = List.of(
            new Passo("cadastro_concluido",
                "Criar sua conta",
                "Bem-vindo ao LinkFlow! Sua conta foi criada com sucesso.",
                "/dashboard",
                steps.getOrDefault("cadastro_concluido", false), 1),

            new Passo("primeiro_produto",
                "Descobrir um produto",
                "Explore o Radar e encontre um produto com alto potencial de venda.",
                "/radar",
                steps.getOrDefault("primeiro_produto", false), 2),

            new Passo("primeiro_roteiro",
                "Gerar seu primeiro roteiro",
                "Use a IA para criar um roteiro completo com gancho, tópicos e CTA.",
                "/studio",
                steps.getOrDefault("primeiro_roteiro", false), 3),

            new Passo("primeiro_link",
                "Criar seu link de afiliado",
                "Gere um link rastreável para saber quantas pessoas clicaram.",
                "/links",
                steps.getOrDefault("primeiro_link", false), 4),

            new Passo("primeiro_video",
                "Gerar seu primeiro vídeo",
                "Deixa a IA criar o vídeo com avatar e narração. Você só publica!",
                "/videos",
                steps.getOrDefault("primeiro_video", false), 5)
        );

        String proximo = passos.stream()
            .filter(p -> !p.concluido())
            .findFirst()
            .map(Passo::acao)
            .orElse("/dashboard");

        String motivacao = gerarMensagem(progress.calcularProgresso());

        return new OnboardingResponse(
            progress.calcularProgresso(),
            progress.getCompleted(),
            passos,
            proximo,
            motivacao
        );
    }

    private static String gerarMensagem(int progresso) {
        if (progresso == 100) return "🎉 Parabéns! Você completou todos os primeiros passos. Agora é escalar!";
        if (progresso >= 80)  return "🔥 Quase lá! Só mais um passo para completar sua jornada inicial.";
        if (progresso >= 60)  return "💪 Ótimo progresso! Você já está na frente de muitos iniciantes.";
        if (progresso >= 40)  return "🚀 Indo bem! Continue — cada passo te deixa mais perto da primeira venda.";
        if (progresso >= 20)  return "✨ Bom começo! O mais difícil é dar o primeiro passo — você já deu!";
        return "👋 Olá! Siga os passos abaixo para fazer sua primeira venda como afiliado.";
    }
}
