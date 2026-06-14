package br.com.linkflow.config;

import br.com.linkflow.entity.User.Plan;
import lombok.Getter;

import java.util.Map;

@Getter
public class PlanLimits {

    private final int scripts;
    private final int facelessVideos;
    private final int avatarVideos;
    private final int links;

    private PlanLimits(int scripts, int facelessVideos, int avatarVideos, int links) {
        this.scripts = scripts;
        this.facelessVideos = facelessVideos;
        this.avatarVideos = avatarVideos;
        this.links = links;
    }

    private static final Map<Plan, PlanLimits> LIMITS = Map.of(
        Plan.FREE, new PlanLimits(
            5,    // scripts
            3,    // faceless videos
            0,    // avatar videos (bloqueado)
            10    // links
        ),
        Plan.INICIANTE, new PlanLimits(
            20,   // scripts
            15,   // faceless videos
            0,    // avatar videos (bloqueado)
            30    // links
        ),
        Plan.PRO, new PlanLimits(
            50,   // scripts
            20,   // faceless videos
            10,   // avatar videos
            100   // links
        ),
        Plan.MASTER, new PlanLimits(
            -1,   // scripts ilimitados (<=0)
            40,   // faceless videos
            20,   // avatar videos
            -1    // links ilimitados (<=0)
        )
    );

    public static PlanLimits of(Plan plan) {
        return LIMITS.get(plan);
    }

    public boolean hasUnlimitedScripts() {
        return scripts <= 0;
    }

    public boolean hasUnlimitedFacelessVideos() {
        return facelessVideos <= 0;
    }

    public boolean hasAvatarVideos() {
        return avatarVideos > 0;
    }

    public boolean hasUnlimitedLinks() {
        return links <= 0;
    }
}
