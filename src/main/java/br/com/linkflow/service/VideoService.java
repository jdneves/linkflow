package br.com.linkflow.service;

import br.com.linkflow.client.ElevenLabsClient;
import br.com.linkflow.client.HeyGenClient;
import br.com.linkflow.client.StorageClient;
import br.com.linkflow.config.PlanLimits;
import br.com.linkflow.dto.request.VideoCreateRequest;
import br.com.linkflow.dto.response.VideoJobResponse;
import br.com.linkflow.entity.Script;
import br.com.linkflow.entity.User;
import br.com.linkflow.entity.VideoJob;
import br.com.linkflow.entity.VideoJob.Status;
import br.com.linkflow.entity.VideoMode;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.ScriptRepository;
import br.com.linkflow.repository.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoJobRepository videoJobRepository;
    private final ScriptRepository scriptRepository;
    private final ElevenLabsClient elevenLabsClient;
    private final HeyGenClient heyGenClient;
    private final StorageClient storageClient;
    private final JavaMailSender mailSender;
    private final OnboardingService onboardingService;

    // ── Inicia o pipeline ─────────────────────────────────────────────────

    @Transactional
    public VideoJobResponse iniciar(VideoCreateRequest request, User user) {
        validarLimitePorModo(user, request.mode());

        Script script = scriptRepository.findById(request.scriptId())
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new BusinessException("Roteiro não encontrado."));

        if (script.getHook() == null || script.getHook().isBlank()) {
            throw new BusinessException("Roteiro inválido — gancho de abertura ausente.");
        }

        VideoJob job = VideoJob.builder()
            .user(user)
            .script(script)
            .mode(request.mode())
            .avatarId(request.avatarId())
            .voiceId(request.voiceId())
            .status(Status.PENDING)
            .build();

        videoJobRepository.save(job);
        log.info("Pipeline iniciado: jobId={} modo={} usuário={}", job.getId(), request.mode(), user.getEmail());

        // Executa o pipeline de forma assíncrona
        executarPipeline(job.getId());

        return VideoJobResponse.from(job);
    }

    // ── Pipeline assíncrono ───────────────────────────────────────────────

    @Async
    @Transactional
    public void executarPipeline(UUID jobId) {
        VideoJob job = videoJobRepository.findById(jobId).orElseThrow();

        try {
            // PASSO 1: Gera áudio com ElevenLabs
            log.info("[Job {}] Gerando áudio com ElevenLabs...", jobId);
            atualizarStatus(job, Status.GENERATING_AUDIO, null);

            String textoNarracao = construirTextoNarracao(job.getScript());
            byte[] mp3 = elevenLabsClient.textToSpeech(textoNarracao, job.getVoiceId());
            String audioUrl = storageClient.uploadAudio(mp3, jobId);

            job.setAudioUrl(audioUrl);
            videoJobRepository.save(job);

            // PASSO 2: Ramificação por modo
            if (job.getMode() == VideoMode.AVATAR) {
                // Pipeline com avatar (HeyGen)
                log.info("[Job {}] Submetendo ao HeyGen (AVATAR)...", jobId);
                atualizarStatus(job, Status.GENERATING_VIDEO, null);

                String heygenVideoId = heyGenClient.submeterVideo(audioUrl, job.getAvatarId());
                job.setHeygenVideoId(heygenVideoId);
                videoJobRepository.save(job);
                onboardingService.concluirPasso(job.getUser(), OnboardingService.PASSO_VIDEO);
                log.info("[Job {}] HeyGen job submetido: heygenId={} — aguardando polling...", jobId, heygenVideoId);

            } else {
                // Pipeline faceless (sem HeyGen)
                log.info("[Job {}] Gerando vídeo FACELESS (sem avatar)...", jobId);
                atualizarStatus(job, Status.GENERATING_VIDEO, null);

                // TODO: Implementar FacelessVideoRenderer
                // String videoUrl = facelessVideoRenderer.render(audioUrl, job.getScript());
                // job.setVideoUrl(videoUrl);
                // job.setStatus(Status.COMPLETED);
                // job.setCompletedAt(LocalDateTime.now());
                // videoJobRepository.save(job);
                // onboardingService.concluirPasso(job.getUser(), OnboardingService.PASSO_VIDEO);
                // notificarConclusao(job);

                throw new UnsupportedOperationException(
                    "Renderização de vídeos FACELESS ainda não implementada. " +
                    "Aguarde próxima atualização ou use modo AVATAR."
                );
            }

        } catch (Exception e) {
            log.error("[Job {}] Falha no pipeline: {}", jobId, e.getMessage(), e);
            atualizarStatus(job, Status.FAILED, e.getMessage());
        }
    }

    // ── Polling de status HeyGen (a cada 30s) ────────────────────────────

    @Scheduled(fixedDelay = 30_000) // 30 segundos
    @Transactional
    public void verificarJobsPendentes() {
        List<VideoJob> jobs = videoJobRepository.findByStatusIn(
            List.of(Status.GENERATING_VIDEO)
        );

        if (jobs.isEmpty()) return;
        log.debug("Verificando {} jobs em andamento no HeyGen...", jobs.size());

        for (VideoJob job : jobs) {
            verificarJobHeyGen(job);
        }
    }

    private void verificarJobHeyGen(VideoJob job) {
        if (job.getHeygenVideoId() == null) return;

        HeyGenClient.VideoStatus status = heyGenClient.checarStatus(job.getHeygenVideoId());

        if (status.isCompleted()) {
            log.info("[Job {}] Vídeo concluído! url={}", job.getId(), status.videoUrl());
            job.setVideoUrl(status.videoUrl());
            job.setStatus(Status.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            videoJobRepository.save(job);
            notificarConclusao(job);

        } else if (status.isFailed()) {
            log.error("[Job {}] HeyGen falhou: {}", job.getId(), status.error());
            atualizarStatus(job, Status.FAILED, status.error());

        } else {
            log.debug("[Job {}] Ainda processando no HeyGen...", job.getId());
        }
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    public VideoJobResponse buscarPorId(UUID jobId, User user) {
        return videoJobRepository.findById(jobId)
            .filter(j -> j.getUser().getId().equals(user.getId()))
            .map(VideoJobResponse::from)
            .orElseThrow(() -> new BusinessException("Job não encontrado."));
    }

    public Page<VideoJobResponse> listar(User user, int page) {
        return videoJobRepository
            .findByUserOrderByCreatedAtDesc(user, PageRequest.of(page, 10))
            .map(VideoJobResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String construirTextoNarracao(Script script) {
        // Combina gancho + tópicos + CTA para narração completa
        StringBuilder sb = new StringBuilder();
        if (script.getHook() != null)   sb.append(script.getHook()).append(" ");
        if (script.getTopics() != null) script.getTopics().forEach(t -> sb.append(t).append(". "));
        if (script.getCta() != null)    sb.append(script.getCta());
        return sb.toString().trim();
    }

    private void atualizarStatus(VideoJob job, Status status, String erro) {
        job.setStatus(status);
        job.setErrorMessage(erro);
        videoJobRepository.save(job);
    }

    private void validarLimitePorModo(User user, VideoMode mode) {
        PlanLimits limits = PlanLimits.of(user.getPlan());

        if (mode == VideoMode.AVATAR) {
            // Avatar: limite <= 0 = bloqueado (não incluído no plano)
            if (!limits.hasAvatarVideos()) {
                throw new BusinessException(
                    "Vídeos com avatar não estão disponíveis no seu plano. " +
                    "Faça upgrade para o Pro ou Master."
                );
            }

            long avatarMes = videoJobRepository.countByUserAndModeThisMonth(user, VideoMode.AVATAR);
            if (avatarMes >= limits.getAvatarVideos()) {
                throw new BusinessException(
                    "Você atingiu o limite de vídeos com avatar do seu plano este mês."
                );
            }

        } else {
            // Faceless: limite <= 0 = ilimitado
            if (!limits.hasUnlimitedFacelessVideos()) {
                long facelessMes = videoJobRepository.countByUserAndModeThisMonth(user, VideoMode.FACELESS);
                if (facelessMes >= limits.getFacelessVideos()) {
                    throw new BusinessException(
                        "Você atingiu o limite de vídeos sem avatar do seu plano este mês."
                    );
                }
            }
        }
    }

    private void notificarConclusao(VideoJob job) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(job.getUser().getEmail());
            msg.setSubject("🎬 Seu vídeo ficou pronto — LinkFlow");
            msg.setText("""
                Olá, %s!

                Seu vídeo foi gerado com sucesso!

                Produto: %s
                Assistir: %s

                Agora é só baixar e publicar no YouTube ou Instagram!

                Equipe LinkFlow
                """.formatted(
                    job.getUser().getName(),
                    job.getScript() != null ? job.getScript().getProductName() : "—",
                    job.getVideoUrl()
                )
            );
            mailSender.send(msg);
            log.info("Notificação enviada para {}", job.getUser().getEmail());
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de conclusão: {}", e.getMessage());
        }
    }
}
