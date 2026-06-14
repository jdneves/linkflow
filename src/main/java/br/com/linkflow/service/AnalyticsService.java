package br.com.linkflow.service;

import br.com.linkflow.client.ClaudeClient;
import br.com.linkflow.config.PlanLimits;
import br.com.linkflow.dto.response.DashboardResponse;
import br.com.linkflow.dto.response.DashboardResponse.*;
import br.com.linkflow.dto.response.RelatorioSemanalResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.entity.VideoMode;
import br.com.linkflow.repository.AffiliateLinkRepository;
import br.com.linkflow.repository.ClickRepository;
import br.com.linkflow.repository.ScriptRepository;
import br.com.linkflow.repository.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickRepository clickRepository;
    private final AffiliateLinkRepository linkRepository;
    private final ScriptRepository scriptRepository;
    private final VideoJobRepository videoJobRepository;
    private final ClaudeClient claudeClient;
    private final JavaMailSender mailSender;

    @Value("${linkflow.base-url}")
    private String baseUrl;

    // ── Dashboard principal ────────────────────────────────────────────────

    public DashboardResponse dashboard(User user) {
        LocalDateTime agora      = LocalDateTime.now();
        LocalDateTime ha30Dias   = agora.minusDays(30);
        LocalDateTime ha7Dias    = agora.minusDays(7);

        // ── Totais ──
        long totalCliques30d = clickRepository.totalClicksUsuario(user.getId(), ha30Dias);
        long totalCliques7d  = clickRepository.totalClicksUsuario(user.getId(), ha7Dias);
        long totalLinks      = linkRepository.countActiveByUser(user);
        long totalRoteiros   = scriptRepository.countByUserThisMonth(user);
        long facelessMes     = videoJobRepository.countByUserAndModeThisMonth(user, VideoMode.FACELESS);
        long avatarMes       = videoJobRepository.countByUserAndModeThisMonth(user, VideoMode.AVATAR);

        Totais totais = new Totais(
            totalCliques30d + totalCliques7d,
            totalLinks, totalRoteiros, facelessMes + avatarMes,
            totalCliques30d, totalCliques7d
        );

        // ── Cliques por dia ──
        List<CliqueDia> cliquesDia = clickRepository
            .clicksPorDia(user.getId(), ha30Dias)
            .stream()
            .map(row -> new CliqueDia(
                row[0].toString(),
                ((Number) row[1]).longValue()
            ))
            .toList();

        // ── Cliques por device ──
        Map<String, Long> porDevice = clickRepository
            .clicksPorDevice(user.getId(), ha30Dias)
            .stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> ((Number) row[1]).longValue()
            ));

        // ── Top 5 links ──
        List<TopLink> topLinks = linkRepository
            .findTopByUser(user, PageRequest.of(0, 5))
            .stream()
            .map(link -> new TopLink(
                link.getId().toString(),
                link.getSlug(),
                link.getTitle(),
                baseUrl + "/r/" + link.getSlug(),
                link.getClicks(),
                link.getProduct() != null ? link.getProduct().getPlatform().name() : "—"
            ))
            .toList();

        // ── Insight semanal com IA ──
        String insight = gerarInsight(user, topLinks, totalCliques7d, totalCliques30d);

        // ── Uso do plano ──
        PlanLimits limits = PlanLimits.of(user.getPlan());
        UsoPlano usoPlano = new UsoPlano(
            user.getPlan().name(),
            totalRoteiros,
            limits.hasUnlimitedScripts() ? -1 : limits.getScripts(),
            facelessMes,
            limits.hasUnlimitedFacelessVideos() ? -1 : limits.getFacelessVideos(),
            avatarMes,
            limits.getAvatarVideos(),
            totalLinks,
            limits.hasUnlimitedLinks() ? -1 : limits.getLinks()
        );

        return new DashboardResponse(totais, cliquesDia, porDevice, topLinks, insight, usoPlano);
    }

    // ── Insight semanal com IA ─────────────────────────────────────────────

    private String gerarInsight(User user, List<TopLink> topLinks, long cliques7d, long cliques30d) {
        try {
            String melhorLink = topLinks.isEmpty() ? "nenhum" : topLinks.get(0).title();
            String prompt = """
                Você é um assistente de marketing digital especializado em afiliados no Brasil.
                Gere um insight curto e motivacional (máximo 3 frases) para um criador-afiliado
                com base nos dados abaixo. Seja específico, prático e encorajador.

                Dados da semana:
                - Cliques nos últimos 7 dias: %d
                - Cliques nos últimos 30 dias: %d
                - Link mais clicado: %s
                - Total de links ativos: %d

                Retorne apenas o texto do insight, sem formatação, sem markdown.
                """.formatted(cliques7d, cliques30d, melhorLink, topLinks.size());

            return claudeClient.completar(prompt).trim();

        } catch (Exception e) {
            log.warn("Falha ao gerar insight com IA: {}", e.getMessage());
            return "Continue publicando conteúdo regularmente — a consistência é o segredo para aumentar seus cliques e conversões!";
        }
    }

    // ── Relatório semanal (toda segunda-feira às 8h) ───────────────────────

    @Scheduled(cron = "0 0 8 * * MON")
    public void enviarRelatoriosSemanal() {
        log.info("Enviando relatórios semanais...");
        // Em produção: busca todos usuários ativos e envia individualmente
        // Por ora a lógica está disponível via enviarRelatorio(user)
    }

    public void enviarRelatorio(User user) {
        try {
            LocalDateTime ha7Dias      = LocalDateTime.now().minusDays(7);
            LocalDateTime ha14Dias     = LocalDateTime.now().minusDays(14);

            long cliques7d  = clickRepository.totalClicksUsuario(user.getId(), ha7Dias);
            long cliques14d = clickRepository.totalClicksUsuario(user.getId(), ha14Dias);
            long variacao   = cliques7d - (cliques14d - cliques7d);

            List<TopLink> topLinks = linkRepository
                .findTopByUser(user, PageRequest.of(0, 3))
                .stream()
                .map(l -> new TopLink(
                    l.getId().toString(), l.getSlug(), l.getTitle(),
                    baseUrl + "/r/" + l.getSlug(), l.getClicks(),
                    l.getProduct() != null ? l.getProduct().getPlatform().name() : "—"
                ))
                .toList();

            String melhorLink = topLinks.isEmpty() ? "—" : topLinks.get(0).title();
            String melhorCliques = topLinks.isEmpty() ? "0" : String.valueOf(topLinks.get(0).clicks());
            List<String> topProdutos = topLinks.stream().map(TopLink::title).toList();
            String insight = gerarInsight(user, topLinks, cliques7d, cliques14d);

            enviarEmailRelatorio(user, new RelatorioSemanalResponse(
                user.getName(), cliques7d, variacao,
                melhorLink, melhorCliques, topProdutos, insight
            ));

        } catch (Exception e) {
            log.error("Falha ao gerar relatório para {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void enviarEmailRelatorio(User user, RelatorioSemanalResponse rel) {
        try {
            var message = mailSender.createMimeMessage();
            var helper  = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("📊 Seu relatório semanal — LinkFlow");
            helper.setText(construirHtmlRelatorio(rel), true);

            mailSender.send(message);
            log.info("Relatório semanal enviado para {}", user.getEmail());

        } catch (Exception e) {
            log.error("Falha ao enviar relatório para {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String construirHtmlRelatorio(RelatorioSemanalResponse rel) {
        String variacao = rel.variacaoCliques() >= 0
            ? "+" + rel.variacaoCliques() + " vs semana anterior 📈"
            : rel.variacaoCliques() + " vs semana anterior 📉";

        String topProdutosHtml = rel.topProdutos().stream()
            .map(p -> "<li>" + p + "</li>")
            .collect(Collectors.joining());

        return """
            <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px">
              <h2 style="color:#1a1a1a">📊 Relatório semanal, %s!</h2>

              <div style="background:#f5f5f5;border-radius:8px;padding:16px;margin:16px 0">
                <h3 style="margin:0 0 8px">Cliques esta semana</h3>
                <span style="font-size:32px;font-weight:bold">%d</span>
                <span style="color:#666;margin-left:8px">%s</span>
              </div>

              <div style="background:#f5f5f5;border-radius:8px;padding:16px;margin:16px 0">
                <h3 style="margin:0 0 8px">🏆 Melhor link da semana</h3>
                <p style="margin:0"><strong>%s</strong> — %s cliques</p>
              </div>

              <div style="background:#f5f5f5;border-radius:8px;padding:16px;margin:16px 0">
                <h3 style="margin:0 0 8px">📦 Top produtos</h3>
                <ul style="margin:0;padding-left:20px">%s</ul>
              </div>

              <div style="background:#EEEDFE;border-radius:8px;padding:16px;margin:16px 0">
                <h3 style="margin:0 0 8px">💡 Insight da semana</h3>
                <p style="margin:0">%s</p>
              </div>

              <p style="text-align:center;margin-top:24px">
                <a href="%s/dashboard"
                   style="background:#1a1a1a;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none">
                  Ver dashboard completo →
                </a>
              </p>

              <p style="color:#999;font-size:12px;text-align:center;margin-top:24px">
                LinkFlow · Você está recebendo este e-mail porque tem uma conta ativa.
              </p>
            </body></html>
            """.formatted(
                rel.nomeUsuario(), rel.totalCliques(), variacao,
                rel.melhorLink(), rel.melhorLinkCliques(),
                topProdutosHtml, rel.insight(), baseUrl
            );
    }
}
