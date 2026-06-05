package br.com.linkflow.service;

import br.com.linkflow.dto.response.OnboardingResponse;
import br.com.linkflow.entity.OnboardingProgress;
import br.com.linkflow.entity.User;
import br.com.linkflow.repository.OnboardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingRepository onboardingRepository;
    private final JavaMailSender mailSender;

    // Passos disponíveis
    public static final String PASSO_PRODUTO  = "primeiro_produto";
    public static final String PASSO_ROTEIRO  = "primeiro_roteiro";
    public static final String PASSO_LINK     = "primeiro_link";
    public static final String PASSO_VIDEO    = "primeiro_video";

    // ── Inicializa onboarding no cadastro ─────────────────────────────────

    @Transactional
    public void inicializar(User user) {
        if (onboardingRepository.findByUser(user).isPresent()) return;

        OnboardingProgress progress = OnboardingProgress.builder()
            .user(user)
            .build();

        onboardingRepository.save(progress);
        log.info("Onboarding iniciado para {}", user.getEmail());

        // Envia e-mail de boas-vindas
        enviarBoasVindas(user);
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    public OnboardingResponse consultar(User user) {
        OnboardingProgress progress = onboardingRepository.findByUser(user)
            .orElseGet(() -> {
                var p = OnboardingProgress.builder().user(user).build();
                return onboardingRepository.save(p);
            });

        return OnboardingResponse.from(progress);
    }

    // ── Registra conclusão de passo ───────────────────────────────────────
    // Chamado internamente pelos outros serviços (LinkService, ScriptService, etc.)

    @Transactional
    public void concluirPasso(User user, String passo) {
        onboardingRepository.findByUser(user).ifPresent(progress -> {
            boolean jaEstaVa = progress.getSteps().getOrDefault(passo, false);
            if (!jaEstaVa) {
                progress.concluirPasso(passo);
                onboardingRepository.save(progress);
                log.info("Onboarding: passo '{}' concluído por {}", passo, user.getEmail());

                // Notifica conclusão total
                if (progress.getCompleted()) {
                    enviarParabens(user);
                }
            }
        });
    }

    // ── E-mails ───────────────────────────────────────────────────────────

    private void enviarBoasVindas(User user) {
        try {
            var message = mailSender.createMimeMessage();
            var helper  = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("🚀 Bem-vindo ao LinkFlow, " + user.getName() + "!");
            helper.setText("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px">
                  <h2>Olá, %s! 👋</h2>
                  <p>Seja bem-vindo ao <strong>LinkFlow</strong> — sua plataforma para criar conteúdo,
                     gerenciar links e descobrir produtos com potencial de venda.</p>

                  <h3>Seus primeiros passos:</h3>
                  <ol>
                    <li>🔍 <strong>Explore o Radar</strong> — encontre produtos em alta</li>
                    <li>✍️ <strong>Gere um roteiro com IA</strong> — pronto em segundos</li>
                    <li>🔗 <strong>Crie seu link rastreável</strong> — saiba o que converte</li>
                    <li>🎬 <strong>Gere seu vídeo com avatar IA</strong> — sem aparecer na câmera</li>
                  </ol>

                  <p style="text-align:center;margin-top:24px">
                    <a href="%s/onboarding"
                       style="background:#1a1a1a;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-size:15px">
                      Começar agora →
                    </a>
                  </p>

                  <p style="color:#999;font-size:12px;margin-top:32px">
                    Equipe LinkFlow · Se tiver dúvidas, responda este e-mail.
                  </p>
                </body></html>
                """.formatted(user.getName(), "https://linkflow.app"), true);

            mailSender.send(message);
            log.info("E-mail de boas-vindas enviado para {}", user.getEmail());

        } catch (Exception e) {
            log.warn("Falha ao enviar boas-vindas para {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void enviarParabens(User user) {
        try {
            var message = mailSender.createMimeMessage();
            var helper  = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("🎉 Você completou o onboarding! — LinkFlow");
            helper.setText("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px">
                  <h2>🎉 Parabéns, %s!</h2>
                  <p>Você completou todos os primeiros passos do LinkFlow.</p>
                  <p>Agora você tem tudo que precisa para crescer como criador-afiliado:</p>
                  <ul>
                    <li>✅ Produto descoberto no Radar</li>
                    <li>✅ Roteiro gerado com IA</li>
                    <li>✅ Link de afiliado rastreável</li>
                    <li>✅ Vídeo gerado com avatar IA</li>
                  </ul>
                  <p><strong>Próximo passo:</strong> Publique seu vídeo e compartilhe o link.
                     Cada clique conta!</p>
                  <p style="text-align:center;margin-top:24px">
                    <a href="%s/dashboard"
                       style="background:#1a1a1a;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none">
                      Ver meu dashboard →
                    </a>
                  </p>
                </body></html>
                """.formatted(user.getName(), "https://linkflow.app"), true);

            mailSender.send(message);

        } catch (Exception e) {
            log.warn("Falha ao enviar parabéns para {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
